package day.bark.android

import android.content.Context
import org.json.JSONObject

class BarkDeliveredNotificationStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun save(id: String, group: String?, notificationTag: String? = null) {
        val key = id.trim().takeIf { it.isNotBlank() } ?: return
        val groups = groupsJson()
        val tags = notificationTag?.let(::listOf) ?: tagsFor(id)
        groups.put(key, JSONObject().put("group", group?.trim().orEmpty()).put("tags", org.json.JSONArray(tags)))
        prefs.edit().putString(KEY_GROUPS, groups.toString()).apply()
    }

    @Synchronized
    fun groupFor(id: String): String? {
        val key = id.trim().takeIf { it.isNotBlank() } ?: return null
        val groups = groupsJson()
        return (groups.optJSONObject(key)?.optString("group") ?: groups.optString(key)).takeIf { it.isNotBlank() }
    }

    @Synchronized
    fun tagsFor(id: String): List<String> {
        val tags = groupsJson().optJSONObject(id)?.optJSONArray("tags") ?: return emptyList()
        return (0 until tags.length()).map { tags.getString(it) }
    }

    @Synchronized
    fun delete(id: String) {
        val key = id.trim().takeIf { it.isNotBlank() } ?: return
        val groups = groupsJson()
        groups.remove(key)
        prefs.edit().putString(KEY_GROUPS, groups.toString()).apply()
    }

    private fun groupsJson(): JSONObject =
        try {
            JSONObject(prefs.getString(KEY_GROUPS, "{}").orEmpty())
        } catch (_: Exception) {
            JSONObject()
        }

    companion object {
        private const val PREFS_NAME = "bark_delivered_notifications"
        private const val KEY_GROUPS = "groups"
    }
}
