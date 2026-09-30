package day.bark.android.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build

class BarkUpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = BarkUpdateStore(context)
        val id = store.sessionId
        val nonce = store.sessionNonce ?: return
        val completedRelease = store.sessionRelease
        if (id < 0 || intent.action != BarkUpdateInstaller.ACTION_INSTALL_RESULT ||
            intent.dataString != "bark-update://install/$id/$nonce" ||
            intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1) != id) return
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                if (!store.applyInstallResult(id, nonce, pending = true, message = "")) return
                val confirmation = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    else @Suppress("DEPRECATION") intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirmation == null || !BarkUpdatesActivity.showConfirmationIfVisible(confirmation)) {
                    BarkUpdateNotifications.show(context, "Tap to confirm the Bark update in Android", confirmation)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                if (!store.applyInstallResult(id, nonce, pending = false, message = "Update installed")) return
                completedRelease?.let { store.file(it).delete(); store.partial(it).delete() }
                BarkUpdateNotifications.cancel(context)
            }
            else -> {
                val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)?.take(180) ?: "Android rejected the update"
                if (!store.applyInstallResult(id, nonce, pending = false, message = "Installation not completed: $detail")) return
                BarkUpdateNotifications.show(context, "Update was not installed. Open Updates to review or retry.")
            }
        }
    }
}
