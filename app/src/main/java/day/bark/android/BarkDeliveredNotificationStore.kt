package day.bark.android

import android.content.Context
import org.json.JSONObject

class BarkDeliveredNotificationStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun save(id: String, group: String?) {
        val key = id.trim().takeIf { it.isNotBlank() } ?: return
        val groups = groupsJson()
        groups.put(key, group?.trim()?.takeIf { it.isNotBlank() }.orEmpty())
        prefs.edit().putString(KEY_GROUPS, groups.toString()).apply()
    }

    @Synchronized
    fun groupFor(id: String): String? {
        val key = id.trim().takeIf { it.isNotBlank() } ?: return null
        return groupsJson().optString(key).takeIf { it.isNotBlank() }
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
