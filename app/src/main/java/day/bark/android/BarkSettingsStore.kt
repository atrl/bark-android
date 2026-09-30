package day.bark.android

import android.content.Context
import android.provider.Settings
import java.util.UUID

class BarkSettingsStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("bark_settings", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = serverProfiles().current(DEFAULT_ANDROID_SERVER).address
        set(value) = saveCurrentServer(value, deviceKey)

    var deviceKey: String?
        get() = serverProfiles().current(DEFAULT_ANDROID_SERVER).key.takeIf { it.isNotBlank() }
        set(value) = saveCurrentServer(serverUrl, value)

    var serverName: String?
        get() = serverProfiles().current(DEFAULT_ANDROID_SERVER).name
        set(value) = renameServer(serverProfiles().currentId, value)

    var archiveEnabled: Boolean
        get() = prefs.getBoolean("archive_enabled", true)
        set(value) = prefs.edit().putBoolean("archive_enabled", value).apply()

    var listeningEnabled: Boolean
        get() = prefs.getBoolean("listening_enabled", false)
        set(value) = prefs.edit().putBoolean("listening_enabled", value).apply()

    var fcmToken: String?
        get() = prefs.getString("fcm_token", null)
        set(value) { check(prefs.edit().putString("fcm_token", value).commit()) }

    var stopPending: Boolean
        get() = prefs.getBoolean("transport_stop_pending", false)
        set(value) { prefs.edit().putBoolean("transport_stop_pending", value).apply() }

    private fun transportKey(target: BarkPollTarget): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(BarkSyncEngine.source(target).toByteArray()).joinToString("") { "%02x".format(it) }

    fun isFcmRegistered(target: BarkPollTarget): Boolean =
        !fcmToken.isNullOrBlank() && prefs.getString("fcm_bound_${transportKey(target)}", null) == fcmToken

    fun markFcmRegistered(target: BarkPollTarget, token: String, canonicalUrl: String?) {
        check(prefs.edit().putString("fcm_bound_${transportKey(target)}", token)
            .putString("transport_status_${transportKey(target)}", "FCM registered with server")
            .putString("canonical_server_${transportKey(target)}", canonicalUrl).commit())
    }

    fun canonicalServer(target: BarkPollTarget): String = prefs.getString("canonical_server_${transportKey(target)}", null)
        ?.trimEnd('/') ?: target.address.trimEnd('/')

    fun notificationMode(): String = if (!cryptoKey.isNullOrBlank() || prefs.all.any { (key, value) ->
        key.startsWith(GROUP_MUTE_PREFIX) && (value as? Long ?: 0L) > System.currentTimeMillis()
    }) "data" else "notification"

    fun markPolling(target: BarkPollTarget, status: String) {
        check(prefs.edit().remove("fcm_bound_${transportKey(target)}")
            .putString("transport_status_${transportKey(target)}", status).commit())
    }

    fun setTransportStatus(target: BarkPollTarget, status: String) {
        prefs.edit().putString("transport_status_${transportKey(target)}", status).apply()
    }

    fun transportStatus(target: BarkPollTarget): String {
        if (!listeningEnabled) return if (stopPending) "Stopping; server confirmation pending" else "Off"
        val detail = prefs.getString("transport_status_${transportKey(target)}", "Configuring delivery").orEmpty()
        if (isFcmRegistered(target)) {
            if (detail != "FCM registered with server") return "FCM registration retained · $detail"
            return if (notificationMode() == "data") "FCM registered · local handling" else "FCM registered · system notifications"
        }
        return if (BarkPollingService.isRunning) "Polling service running · $detail" else "Polling paused · $detail"
    }

    var cryptoAlgorithm: String
        get() = prefs.getString("crypto_algorithm", "AES128") ?: "AES128"
        set(value) = prefs.edit().putString("crypto_algorithm", value).apply()

    var cryptoMode: String
        get() = prefs.getString("crypto_mode", "CBC") ?: "CBC"
        set(value) = prefs.edit().putString("crypto_mode", value).apply()

    var cryptoPadding: String
        get() = prefs.getString("crypto_padding", "pkcs7") ?: "pkcs7"
        set(value) = prefs.edit().putString("crypto_padding", value).apply()

    var cryptoKey: String?
        get() = prefs.getString("crypto_key", null)
        set(value) = prefs.edit().putString("crypto_key", value).apply()

    var cryptoIv: String?
        get() = prefs.getString("crypto_iv", null)
        set(value) = prefs.edit().putString("crypto_iv", value).apply()

    val installToken: String
        get() {
            val existing = prefs.getString("install_token", null)
            if (!existing.isNullOrBlank()) return existing
            val androidId = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
            val token = "android:${androidId ?: UUID.randomUUID().toString()}"
            prefs.edit().putString("install_token", token).apply()
            return token
        }

    fun cryptoSettingsOrNull(): CryptoSettings? {
        val key = cryptoKey?.takeIf { it.isNotBlank() } ?: return null
        return CryptoSettings(
            algorithm = cryptoAlgorithm,
            mode = cryptoMode,
            padding = cryptoPadding,
            key = key,
            iv = cryptoIv?.takeIf { it.isNotBlank() },
        )
    }

    fun serverProfiles(): BarkServerProfiles {
        val json = prefs.getString("server_profiles", null)
        if (!json.isNullOrBlank()) {
            val decoded = BarkServerProfiles.fromJson(json, defaultAddress = DEFAULT_ANDROID_SERVER)
            val migrated = migrateBlankLegacyEmulatorDefault(decoded)
            if (migrated != decoded) {
                saveServerProfiles(migrated)
            }
            return migrated
        }

        val legacyAddress = prefs.getString("server_url", DEFAULT_ANDROID_SERVER) ?: DEFAULT_ANDROID_SERVER
        val legacyKey = prefs.getString("device_key", null).orEmpty()
        val defaultAddress = if (legacyAddress == LEGACY_EMULATOR_SERVER && legacyKey.isBlank()) {
            DEFAULT_ANDROID_SERVER
        } else {
            legacyAddress
        }
        return BarkServerProfiles.empty(defaultAddress = defaultAddress)
            .updateCurrent(defaultAddress, legacyKey, defaultAddress = defaultAddress)
            .also(::saveServerProfiles)
    }

    private fun migrateBlankLegacyEmulatorDefault(profiles: BarkServerProfiles): BarkServerProfiles {
        val normalized = profiles.normalized(DEFAULT_ANDROID_SERVER)
        val migratedProfiles = normalized.profiles.map { profile ->
            if (profile.address == LEGACY_EMULATOR_SERVER && profile.key.isBlank() && profile.name.isNullOrBlank()) {
                profile.copy(address = DEFAULT_ANDROID_SERVER)
            } else {
                profile
            }
        }
        return normalized.copy(profiles = migratedProfiles).normalized(DEFAULT_ANDROID_SERVER)
    }

    fun saveCurrentServer(address: String, key: String?) {
        saveServerProfiles(
            serverProfiles().updateCurrent(
                address = address,
                key = key.orEmpty(),
                defaultAddress = DEFAULT_ANDROID_SERVER,
            ),
        )
    }

    fun addServer(address: String, key: String? = null, name: String? = null): BarkServerProfile {
        val profile = BarkServerProfile(
            id = UUID.randomUUID().toString(),
            address = address,
            key = key.orEmpty(),
            name = name,
        )
        saveServerProfiles(serverProfiles().add(profile, defaultAddress = DEFAULT_ANDROID_SERVER))
        return profile
    }

    fun selectServer(id: String) {
        saveServerProfiles(serverProfiles().select(id, defaultAddress = DEFAULT_ANDROID_SERVER))
    }

    fun removeServer(id: String) {
        saveServerProfiles(serverProfiles().remove(id, defaultAddress = DEFAULT_ANDROID_SERVER))
    }

    fun renameServer(id: String, name: String?) {
        saveServerProfiles(serverProfiles().rename(id, name, defaultAddress = DEFAULT_ANDROID_SERVER))
    }

    fun updateServerKey(id: String, key: String) {
        saveServerProfiles(serverProfiles().updateKey(id, key, defaultAddress = DEFAULT_ANDROID_SERVER))
    }

    fun updateCurrentServerKey(key: String) {
        saveCurrentServer(serverUrl, key)
    }

    private fun saveServerProfiles(profiles: BarkServerProfiles) {
        val normalized = profiles.normalized(DEFAULT_ANDROID_SERVER)
        val current = normalized.current(DEFAULT_ANDROID_SERVER)
        prefs.edit()
            .putString("server_profiles", normalized.toJson())
            .putString("server_url", current.address)
            .putString("device_key", current.key)
            .apply()
    }

    fun muteGroupFor(
        group: String,
        durationMillis: Long = GROUP_MUTE_DURATION_MILLIS,
        nowMillis: Long = System.currentTimeMillis(),
    ): Long {
        val untilMillis = nowMillis + durationMillis
        prefs.edit().putLong(groupMuteKey(group), untilMillis).apply()
        if (listeningEnabled) BarkDeliveryController.enqueueSync(appContext)
        return untilMillis
    }

    fun groupMutedUntilMillis(group: String, nowMillis: Long = System.currentTimeMillis()): Long? {
        val key = groupMuteKey(group)
        if (!prefs.contains(key)) return null
        val untilMillis = prefs.getLong(key, 0L)
        if (untilMillis <= nowMillis) {
            prefs.edit().remove(key).apply()
            return null
        }
        return untilMillis
    }

    private fun groupMuteKey(group: String): String = "$GROUP_MUTE_PREFIX$group"

    companion object {
        const val DEFAULT_ANDROID_SERVER = "https://bark.atrl.me"
        private const val LEGACY_EMULATOR_SERVER = "http://10.0.2.2:8080"
        const val GROUP_MUTE_DURATION_MILLIS = 60L * 60L * 1000L
        private const val GROUP_MUTE_PREFIX = "group_mute_until_"
    }
}
