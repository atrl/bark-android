package day.bark.android.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import day.bark.android.BarkAppRelease
import day.bark.android.BarkUpdateRecovery
import day.bark.android.recoverBarkUpdate
import java.util.UUID

object BarkUpdateInstaller {
    private val lock = Any()

    fun reconcile(context: Context) = synchronized(lock) {
        val store = BarkUpdateStore(context)
        val installer = context.packageManager.packageInstaller
        // Clean a crash between createSession and persisting its ID. These are
        // uncommitted sessions owned by this app and targeting this app only.
        val ownedSessions = installer.mySessions.filter { it.appPackageName == context.packageName }
        ownedSessions.filter { it.sessionId != store.sessionId && !it.isSealed }
            .forEach { runCatching { installer.abandonSession(it.sessionId) } }
        if (store.sessionId < 0) return@synchronized
        val info = ownedSessions.firstOrNull { it.sessionId == store.sessionId }
        val sessionRelease = store.sessionRelease
        when (recoverBarkUpdate(BarkApkVerifier(context).installed().versionCode, store.sessionVersion,
            info != null, info?.isSealed == true, info?.isActive == true)) {
            BarkUpdateRecovery.INSTALLED -> {
                store.clearSession("Update installed")
                sessionRelease?.let { store.file(it).delete(); store.partial(it).delete() }
                BarkUpdateNotifications.cancel(context)
            }
            BarkUpdateRecovery.RETRY_STAGING -> {
                if (info != null) runCatching { installer.abandonSession(info.sessionId) }
                store.clearSession("Installation interrupted; verified download retained for retry")
                if (store.automatic && store.release != null) BarkUpdateScheduler.download(context, manual = false)
            }
            BarkUpdateRecovery.CONTINUE_CONFIRMATION -> {
                store.waitingForConfirmation()
                BarkUpdateNotifications.show(context, "Open Updates to continue the pending installation")
            }
            BarkUpdateRecovery.WAIT_SYSTEM -> Unit
        }
    }

    /** Called off the main thread. Android decides whether confirmation is required. */
    fun install(context: Context, release: BarkAppRelease, manual: Boolean) = synchronized(lock) {
        val store = BarkUpdateStore(context)
        reconcile(context)
        val file = store.file(release)
        BarkApkVerifier(context).verify(release, file)
        if (!context.packageManager.canRequestPackageInstalls()) {
            store.status = "Downloaded and verified; allow app updates to install"
            BarkUpdateNotifications.show(context, "Update ${release.versionName} is ready. Open Updates to allow installation.")
            return@synchronized
        }
        val installer = context.packageManager.packageInstaller
        // A committed session survives process death. Manual retry reattaches a
        // callback to it, recovering a lost confirmation intent without serializing
        // system Parcelables. Automatic jobs leave pending user decisions alone.
        if (store.sessionId >= 0) {
            if (!manual) return@synchronized
            if (store.sessionVersion == release.versionCode && installer.getSessionInfo(store.sessionId) != null) {
                commit(context, installer, store.sessionId, release)
                return@synchronized
            }
            runCatching { installer.abandonSession(store.sessionId) }
            store.clearSession("Preparing update")
        }
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(release.sizeBytes)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        try {
            store.recordStaging(id, release)
            installer.openSession(id).use { session ->
                session.openWrite("base.apk", 0, release.sizeBytes).use { output ->
                    file.inputStream().use { it.copyTo(output) }
                    session.fsync(output)
                }
            }
            // Recheck immediately before commit: another installer may have
            // upgraded this package while the APK was being copied.
            BarkApkVerifier(context).verify(release, file)
            commit(context, installer, id, release)
        } catch (error: Exception) {
            runCatching { installer.abandonSession(id) }
            store.clearSession("Installation could not start: ${error.message.orEmpty().take(180)}")
            throw error
        }
    }

    private fun commit(context: Context, installer: PackageInstaller, id: Int, release: BarkAppRelease) {
        val nonce = UUID.randomUUID().toString()
        val store = BarkUpdateStore(context)
        store.recordSession(id, nonce, release)
        val intent = Intent(context, BarkUpdateInstallReceiver::class.java).setAction(ACTION_INSTALL_RESULT)
            .setData(Uri.parse("bark-update://install/$id/$nonce"))
        // The system fills EXTRA_STATUS/EXTRA_INTENT. Do not use IMMUTABLE or
        // ONE_SHOT: pending confirmation can be followed by a final callback.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val receiver = PendingIntent.getBroadcast(context, id, intent, flags)
        installer.openSession(id).use { it.commit(receiver.intentSender) }
    }

    const val ACTION_INSTALL_RESULT = "day.bark.android.UPDATE_INSTALL_RESULT"
}
