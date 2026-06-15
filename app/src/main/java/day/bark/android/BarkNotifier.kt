@file:Suppress("DEPRECATION")

package day.bark.android

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider

class BarkNotifier(private val context: Context) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val remoteImageCache = BarkRemoteImageCache(context)

    fun show(message: BarkMessage): Boolean {
        ensureChannels()
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        if (message.autoCopy) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(
                ClipData.newPlainText("Bark", message.copy ?: message.displayBody ?: message.body.orEmpty()),
            )
        }

        val builder = Notification.Builder(context, ensureChannelFor(message))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(message.title ?: context.getString(R.string.app_name))
            .setSubText(message.subtitle?.takeIf { it.isNotBlank() })
            .setContentText(message.displayBody ?: message.body.orEmpty())
            .setStyle(Notification.BigTextStyle().bigText(message.displayBody ?: message.body.orEmpty()))
            .setAutoCancel(BarkTapAction.shouldAttachContentIntent(message))
            .setOnlyAlertOnce(false)
            .setNumber(message.badge ?: 0)
            .setDeleteIntent(notificationDismissedIntent(message.id))
            .addAction(
                android.R.drawable.ic_menu_edit,
                context.getString(R.string.copy_action),
                copyIntent(message),
            )
        contentIntent(message)?.let(builder::setContentIntent)

        when (message.level?.lowercase()) {
            "passive" -> builder.setPriority(Notification.PRIORITY_LOW)
            "timesensitive", "critical" -> builder.setPriority(Notification.PRIORITY_HIGH)
        }

        loadBitmap(message.icon)?.let { builder.setLargeIcon(it) }
        loadBitmap(message.image)?.let {
            builder.setStyle(
                Notification.BigPictureStyle()
                    .bigPicture(it)
                    .setBigContentTitle(message.title ?: context.getString(R.string.app_name))
                    .setSummaryText(message.displayBody ?: message.body.orEmpty()),
            )
        }
        val muteGroup = BarkGroupMutePolicy.groupKey(message.group)
        builder.addAction(
            android.R.drawable.ic_lock_silent_mode,
            context.getString(R.string.mute_group_1h),
            muteGroupIntent(muteGroup),
        )
        val group = message.group?.takeIf { it.isNotBlank() }
        if (group != null) {
            builder.setGroup(group)
            showGroupSummary(group, message)
        }

        val id = message.id.hashCode()
        notificationManager.notify(id, builder.build())
        if (message.call) {
            BarkCallAlertPlayer.play(context.applicationContext, message)
        }
        return true
    }

    fun cancel(message: BarkMessage) {
        cancel(message.id, message.group)
    }

    fun cancel(messageId: String) {
        cancel(messageId, null)
    }

    fun cancel(messageId: String, group: String?) {
        notificationManager.cancel(messageId.hashCode())
        cancelGroupSummaryIfLastChild(messageId, group)
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        listOf(
            NotificationChannel(CHANNEL_DEFAULT, "Bark", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CHANNEL_LOW, "Bark Passive", NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(CHANNEL_HIGH, "Bark Time Sensitive", NotificationManager.IMPORTANCE_HIGH),
        ).forEach(notificationManager::createNotificationChannel)
    }

    private fun ensureChannelFor(message: BarkMessage): String {
        ensureChannels()
        if (Build.VERSION.SDK_INT < 26) {
            return CHANNEL_DEFAULT
        }

        val channelId = channelIdFor(message)
        if (channelId in setOf(CHANNEL_DEFAULT, CHANNEL_LOW, CHANNEL_HIGH)) {
            return channelId
        }

        if (notificationManager.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(channelId, channelName(message), importanceFor(message))
            resolvedSound(message)?.let { soundUri ->
                channel.setSound(soundUri, soundAttributes(message))
            }
            notificationManager.createNotificationChannel(channel)
        }
        return channelId
    }

    private fun channelIdFor(message: BarkMessage): String {
        val builtIn = BarkSoundPolicy.resourceNameFor(message)
        val custom = BarkCustomSoundStore(context).find(message.sound)
        val base = baseChannelId(message)
        return when {
            message.call && builtIn != null -> "${base}_call_$builtIn"
            builtIn != null -> "${base}_$builtIn"
            custom != null -> "${base}_custom_${custom.name}"
            else -> base
        }
    }

    private fun baseChannelId(message: BarkMessage): String =
        when {
            message.call -> CHANNEL_HIGH
            message.level?.lowercase() == "passive" -> CHANNEL_LOW
            message.level?.lowercase() in setOf("timesensitive", "critical") -> CHANNEL_HIGH
            else -> CHANNEL_DEFAULT
        }

    private fun channelName(message: BarkMessage): String {
        val sound = BarkSoundPolicy.resourceNameFor(message)
            ?: BarkCustomSoundStore(context).find(message.sound)?.name
            ?: "default"
        return if (message.call) "Bark Call $sound" else "Bark $sound"
    }

    private fun importanceFor(message: BarkMessage): Int {
        val level = message.level?.lowercase()
        return when {
            message.call -> NotificationManager.IMPORTANCE_HIGH
            level == "passive" -> NotificationManager.IMPORTANCE_LOW
            level in setOf("timesensitive", "critical") -> NotificationManager.IMPORTANCE_HIGH
            else -> NotificationManager.IMPORTANCE_DEFAULT
        }
    }

    private fun soundAttributes(message: BarkMessage): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(if (message.call || message.level?.lowercase() == "critical") AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

    private fun rawSoundUri(name: String): Uri =
        Uri.parse("android.resource://${context.packageName}/raw/$name")

    private fun resolvedSound(message: BarkMessage): Uri? {
        BarkSoundPolicy.resourceNameFor(message)?.let { builtIn ->
            return rawSoundUri(builtIn)
        }
        val custom = BarkCustomSoundStore(context).find(message.sound) ?: return null
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.soundprovider",
            custom.file,
        )
    }

    private fun contentIntent(message: BarkMessage): PendingIntent? {
        if (!BarkTapAction.shouldAttachContentIntent(message)) return null
        val url = BarkTapAction.urlToOpen(message)
        val intent = when {
            url != null -> Intent(Intent.ACTION_VIEW, Uri.parse(url))
            BarkTapAction.shouldShowAlert(message) -> Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_SHOW_MESSAGE_ALERT)
                .putExtra(MainActivity.EXTRA_ALERT_TITLE, message.title)
                .putExtra(MainActivity.EXTRA_ALERT_SUBTITLE, message.subtitle)
                .putExtra(MainActivity.EXTRA_ALERT_BODY, message.displayBody ?: message.body.orEmpty())
                .putExtra(MainActivity.EXTRA_ALERT_COPY, message.copy ?: message.displayBody ?: message.body.orEmpty())
                .putExtra(MainActivity.EXTRA_ALERT_SHARE, BarkCopyText.from(message))
            else -> Intent(context, MainActivity::class.java)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            message.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun copyIntent(message: BarkMessage): PendingIntent {
        val intent = Intent(context, BarkNotificationActionReceiver::class.java)
            .setAction(BarkNotificationActionReceiver.ACTION_COPY_MESSAGE)
            .putExtra(BarkNotificationActionReceiver.EXTRA_COPY_TEXT, BarkCopyText.from(message))
        return PendingIntent.getBroadcast(
            context,
            message.id.hashCode() xor COPY_REQUEST_CODE_MASK,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun muteGroupIntent(group: String): PendingIntent {
        val intent = Intent(context, BarkNotificationActionReceiver::class.java)
            .setAction(BarkNotificationActionReceiver.ACTION_MUTE_GROUP)
            .putExtra(BarkNotificationActionReceiver.EXTRA_GROUP, group)
        return PendingIntent.getBroadcast(
            context,
            group.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notificationDismissedIntent(messageId: String): PendingIntent {
        val intent = Intent(context, BarkNotificationActionReceiver::class.java)
            .setAction(BarkNotificationActionReceiver.ACTION_NOTIFICATION_DISMISSED)
            .putExtra(BarkNotificationActionReceiver.EXTRA_MESSAGE_ID, messageId)
        return PendingIntent.getBroadcast(
            context,
            messageId.hashCode() xor DISMISS_REQUEST_CODE_MASK,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun showGroupSummary(group: String, message: BarkMessage) {
        val summary = Notification.Builder(context, ensureChannelFor(message))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(group)
            .setContentText(context.getString(R.string.app_name))
            .setGroup(group)
            .setGroupSummary(true)
            .setDefaults(0)
            .setSound(null)
            .setVibrate(null)
            .setOnlyAlertOnce(true)
            .build()
        notificationManager.notify(groupSummaryId(group), summary)
    }

    private fun groupSummaryId(group: String): Int =
        group.hashCode() xor GROUP_SUMMARY_ID_MASK

    private fun cancelGroupSummaryIfLastChild(messageId: String, group: String?) {
        val normalizedGroup = group?.takeIf { it.isNotBlank() } ?: return
        val isLastChild = notificationManager.activeNotifications.none { record ->
            record.id != messageId.hashCode() &&
                record.id != groupSummaryId(normalizedGroup) &&
                record.notification.group == normalizedGroup &&
                (record.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0
        }
        if (isLastChild) {
            notificationManager.cancel(groupSummaryId(normalizedGroup))
        }
    }

    private fun loadBitmap(url: String?) = remoteImageCache.bitmap(url)

    companion object {
        const val CHANNEL_DEFAULT = "bark_default"
        const val CHANNEL_LOW = "bark_low"
        const val CHANNEL_HIGH = "bark_high"
        private const val COPY_REQUEST_CODE_MASK = 0x435059
        private const val DISMISS_REQUEST_CODE_MASK = 0x444953
        private const val GROUP_SUMMARY_ID_MASK = 0x475250
    }
}
