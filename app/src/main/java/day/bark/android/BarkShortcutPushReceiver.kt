@file:Suppress("DEPRECATION")

package day.bark.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast

class BarkShortcutPushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        Thread {
            try {
                when (intent.action) {
                    ACTION_PUSH_CURRENT -> pushToCurrentServer(context.applicationContext, intent)
                    ACTION_PUSH_ADDRESS -> pushToAddress(intent)
                    else -> return@Thread
                }
                toast(context, R.string.shortcut_push_sent)
            } catch (_: Exception) {
                toast(context, R.string.shortcut_push_failed)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private fun pushToCurrentServer(context: Context, intent: Intent) {
        val settings = BarkSettingsStore(context)
        val key = settings.deviceKey?.takeIf { it.isNotBlank() } ?: error("Register first")
        BarkServerClient(settings.serverUrl).push(key, intent.toPushRequest())
    }

    private fun pushToAddress(intent: Intent) {
        val address = intent.extraString(EXTRA_ADDRESS) ?: error("Address required")
        BarkServerClient(address).pushToAddress(intent.toPushRequest())
    }

    private fun Intent.toPushRequest(): BarkPushRequest =
        BarkPushRequest(
            title = extraString(EXTRA_TITLE),
            subtitle = extraString(EXTRA_SUBTITLE),
            body = extraString(EXTRA_BODY),
            id = extraString(EXTRA_ID),
            markdown = extraString(EXTRA_MARKDOWN),
            level = extraString(EXTRA_LEVEL),
            isCall = extraBoolean(EXTRA_CALL),
            isCritical = extraBoolean(EXTRA_CRITICAL),
            volume = extraInt(EXTRA_VOLUME),
            badge = extraInt(EXTRA_BADGE),
            autoCopy = extraBoolean(EXTRA_AUTO_COPY),
            copy = extraString(EXTRA_COPY),
            sound = extraString(EXTRA_SOUND),
            icon = extraString(EXTRA_ICON),
            image = extraString(EXTRA_IMAGE),
            group = extraString(EXTRA_GROUP),
            archive = extraBooleanOrNull(EXTRA_ARCHIVE),
            ttlSeconds = extraLong(EXTRA_TTL),
            url = extraString(EXTRA_URL),
            action = extraString(EXTRA_ACTION),
            ciphertext = extraString(EXTRA_CIPHERTEXT),
            iv = extraString(EXTRA_IV),
            isDelete = extraBoolean(EXTRA_DELETE),
        )

    private fun Intent.extraString(name: String): String? =
        extras?.get(name)?.toString()?.trim()?.takeIf { it.isNotBlank() }

    private fun Intent.extraInt(name: String): Int? =
        when (val value = extras?.get(name)) {
            is Number -> value.toInt()
            is String -> value.trim().toIntOrNull()
            else -> null
        }

    private fun Intent.extraLong(name: String): Long? =
        when (val value = extras?.get(name)) {
            is Number -> value.toLong()
            is String -> value.trim().toLongOrNull()
            else -> null
        }

    private fun Intent.extraBoolean(name: String): Boolean =
        extraBooleanOrNull(name) ?: false

    private fun Intent.extraBooleanOrNull(name: String): Boolean? {
        if (!hasExtra(name)) return null
        return when (val value = extras?.get(name)) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> value == "1" || value.equals("true", ignoreCase = true) || value.equals("yes", ignoreCase = true)
            else -> null
        }
    }

    private fun toast(context: Context, messageResId: Int) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, messageResId, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val ACTION_PUSH_CURRENT = "day.bark.android.action.PUSH_CURRENT"
        const val ACTION_PUSH_ADDRESS = "day.bark.android.action.PUSH_ADDRESS"
        const val EXTRA_ADDRESS = "day.bark.android.extra.ADDRESS"
        const val EXTRA_TITLE = "day.bark.android.extra.TITLE"
        const val EXTRA_SUBTITLE = "day.bark.android.extra.SUBTITLE"
        const val EXTRA_BODY = "day.bark.android.extra.BODY"
        const val EXTRA_ID = "day.bark.android.extra.ID"
        const val EXTRA_MARKDOWN = "day.bark.android.extra.MARKDOWN"
        const val EXTRA_LEVEL = "day.bark.android.extra.LEVEL"
        const val EXTRA_CALL = "day.bark.android.extra.CALL"
        const val EXTRA_CRITICAL = "day.bark.android.extra.CRITICAL"
        const val EXTRA_VOLUME = "day.bark.android.extra.VOLUME"
        const val EXTRA_BADGE = "day.bark.android.extra.BADGE"
        const val EXTRA_AUTO_COPY = "day.bark.android.extra.AUTO_COPY"
        const val EXTRA_COPY = "day.bark.android.extra.COPY"
        const val EXTRA_SOUND = "day.bark.android.extra.SOUND"
        const val EXTRA_ICON = "day.bark.android.extra.ICON"
        const val EXTRA_IMAGE = "day.bark.android.extra.IMAGE"
        const val EXTRA_GROUP = "day.bark.android.extra.GROUP"
        const val EXTRA_ARCHIVE = "day.bark.android.extra.ARCHIVE"
        const val EXTRA_TTL = "day.bark.android.extra.TTL"
        const val EXTRA_URL = "day.bark.android.extra.URL"
        const val EXTRA_ACTION = "day.bark.android.extra.ACTION"
        const val EXTRA_CIPHERTEXT = "day.bark.android.extra.CIPHERTEXT"
        const val EXTRA_IV = "day.bark.android.extra.IV"
        const val EXTRA_DELETE = "day.bark.android.extra.DELETE"
    }
}
