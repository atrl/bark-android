package day.bark.android

import org.json.JSONArray
import org.json.JSONObject

data class BarkServerProfile(
    val id: String,
    val address: String,
    val key: String,
    val name: String? = null,
) {
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() } ?: address

    val pushBaseUrl: String
        get() = "${address.trimEnd('/')}/$key/"
}

data class BarkPollTarget(
    val id: String,
    val address: String,
    val key: String,
)

data class BarkServerProfiles(
    val profiles: List<BarkServerProfile>,
    val currentId: String,
) {
    fun normalized(defaultAddress: String = DEFAULT_ADDRESS): BarkServerProfiles {
        val nonEmptyProfiles = if (profiles.isEmpty()) {
            listOf(defaultProfile(defaultAddress))
        } else {
            profiles.map { it.copy(address = normalizeAddress(it.address, defaultAddress)) }
        }
        val selectedId = currentId.takeIf { id -> nonEmptyProfiles.any { it.id == id } }
            ?: nonEmptyProfiles.first().id
        return copy(profiles = nonEmptyProfiles, currentId = selectedId)
    }

    fun current(defaultAddress: String = DEFAULT_ADDRESS): BarkServerProfile {
        val state = normalized(defaultAddress)
        return state.profiles.first { it.id == state.currentId }
    }

    fun pollTargets(defaultAddress: String = DEFAULT_ADDRESS): List<BarkPollTarget> =
        normalized(defaultAddress).profiles
            .filter { it.key.isNotBlank() }
            .map {
                BarkPollTarget(
                    id = it.id,
                    address = it.address,
                    key = it.key.trim(),
                )
            }

    fun select(id: String, defaultAddress: String = DEFAULT_ADDRESS): BarkServerProfiles {
        val state = normalized(defaultAddress)
        return if (state.profiles.any { it.id == id }) {
            state.copy(currentId = id)
        } else {
            state
        }
    }

    fun add(
        profile: BarkServerProfile,
        select: Boolean = true,
        defaultAddress: String = DEFAULT_ADDRESS,
    ): BarkServerProfiles {
        val normalizedProfile = profile.copy(address = normalizeAddress(profile.address, defaultAddress))
        val state = normalized(defaultAddress)
        val existing = state.profiles.firstOrNull {
            it.address == normalizedProfile.address && it.key == normalizedProfile.key
        }
        if (existing != null) {
            return state.copy(currentId = if (select) existing.id else state.currentId)
        }
        val remaining = state.profiles.filterNot { it.id == normalizedProfile.id }
        val next = copy(
            profiles = remaining + normalizedProfile,
            currentId = if (select) normalizedProfile.id else currentId,
        )
        return next.normalized(defaultAddress)
    }

    fun updateCurrent(
        address: String,
        key: String,
        name: String? = null,
        defaultAddress: String = DEFAULT_ADDRESS,
    ): BarkServerProfiles {
        val state = normalized(defaultAddress)
        return state.copy(
            profiles = state.profiles.map { profile ->
                if (profile.id == state.currentId) {
                    profile.copy(address = normalizeAddress(address, defaultAddress), key = key, name = name ?: profile.name)
                } else {
                    profile
                }
            },
        )
    }

    fun rename(id: String, name: String?, defaultAddress: String = DEFAULT_ADDRESS): BarkServerProfiles {
        val normalizedName = name?.trim()?.takeIf { it.isNotBlank() }
        val state = normalized(defaultAddress)
        return state.copy(
            profiles = state.profiles.map { profile ->
                if (profile.id == id) profile.copy(name = normalizedName) else profile
            },
        )
    }

    fun updateKey(id: String, key: String, defaultAddress: String = DEFAULT_ADDRESS): BarkServerProfiles {
        val state = normalized(defaultAddress)
        return state.copy(
            profiles = state.profiles.map { profile ->
                if (profile.id == id) profile.copy(key = key) else profile
            },
        )
    }

    fun remove(id: String, defaultAddress: String = DEFAULT_ADDRESS): BarkServerProfiles {
        val remaining = normalized(defaultAddress).profiles.filterNot { it.id == id }
        val profiles = remaining.ifEmpty { listOf(defaultProfile(defaultAddress)) }
        val selectedId = currentId.takeIf { current -> profiles.any { it.id == current } }
            ?: profiles.first().id
        return copy(profiles = profiles, currentId = selectedId).normalized(defaultAddress)
    }

    fun toJson(): String {
        val root = JSONObject()
            .put("currentId", currentId)
            .put(
                "profiles",
                JSONArray().apply {
                    profiles.forEach { profile ->
                        put(
                            JSONObject()
                                .put("id", profile.id)
                                .put("address", profile.address)
                                .put("key", profile.key)
                                .put("name", profile.name ?: JSONObject.NULL),
                        )
                    }
                },
            )
        return root.toString()
    }

    companion object {
        const val DEFAULT_ADDRESS = "https://api.day.app"

        fun empty(defaultAddress: String = DEFAULT_ADDRESS): BarkServerProfiles =
            BarkServerProfiles(listOf(defaultProfile(defaultAddress)), "default")

        fun fromJson(json: String?, defaultAddress: String = DEFAULT_ADDRESS): BarkServerProfiles {
            if (json.isNullOrBlank()) return empty(defaultAddress)
            return try {
                val root = JSONObject(json)
                val profilesJson = root.optJSONArray("profiles") ?: JSONArray()
                val profiles = buildList {
                    for (index in 0 until profilesJson.length()) {
                        val item = profilesJson.getJSONObject(index)
                        add(
                            BarkServerProfile(
                                id = item.optString("id").ifBlank { "server-$index" },
                                address = item.optString("address", defaultAddress),
                                key = item.optString("key"),
                                name = item.optionalString("name"),
                            ),
                        )
                    }
                }
                BarkServerProfiles(
                    profiles = profiles,
                    currentId = root.optString("currentId"),
                ).normalized(defaultAddress)
            } catch (_: Exception) {
                empty(defaultAddress)
            }
        }

        fun defaultProfile(defaultAddress: String = DEFAULT_ADDRESS): BarkServerProfile =
            BarkServerProfile(id = "default", address = normalizeAddress(defaultAddress, DEFAULT_ADDRESS), key = "")

        fun normalizeAddress(address: String, defaultAddress: String = DEFAULT_ADDRESS): String =
            address.trim().trimEnd('/').ifBlank { defaultAddress.trim().trimEnd('/').ifBlank { DEFAULT_ADDRESS } }

        private fun JSONObject.optionalString(name: String): String? {
            if (isNull(name)) return null
            val value = optString(name).trim()
            return value.takeIf { it.isNotBlank() && it != "null" }
        }
    }
}
