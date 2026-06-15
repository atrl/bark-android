package day.bark.android

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

class BarkNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_COPY_MESSAGE -> copyMessage(context, intent)
            ACTION_MUTE_GROUP -> muteGroup(context, intent)
            ACTION_NOTIFICATION_DISMISSED -> notificationDismissed(context, intent)
        }
    }

    private fun copyMessage(context: Context, intent: Intent) {
        val text = intent.getStringExtra(EXTRA_COPY_TEXT)?.takeIf { it.isNotBlank() } ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Bark", text))
        Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    private fun muteGroup(context: Context, intent: Intent) {
        val group = intent.getStringExtra(EXTRA_GROUP) ?: return
        BarkSettingsStore(context).muteGroupFor(group)
        Toast.makeText(
            context,
            context.getString(R.string.group_muted, BarkGroupMutePolicy.displayName(group)),
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun notificationDismissed(context: Context, intent: Intent) {
        val messageId = intent.getStringExtra(EXTRA_MESSAGE_ID)?.takeIf { it.isNotBlank() } ?: return
        BarkDeliveredNotificationStore(context).delete(messageId)
    }

    companion object {
        const val ACTION_COPY_MESSAGE = "day.bark.android.action.COPY_MESSAGE"
        const val ACTION_MUTE_GROUP = "day.bark.android.action.MUTE_GROUP"
        const val ACTION_NOTIFICATION_DISMISSED = "day.bark.android.action.NOTIFICATION_DISMISSED"
        const val EXTRA_COPY_TEXT = "day.bark.android.extra.COPY_TEXT"
        const val EXTRA_GROUP = "day.bark.android.extra.GROUP"
        const val EXTRA_MESSAGE_ID = "day.bark.android.extra.MESSAGE_ID"
    }
}
