package day.bark.android.update

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.work.Worker
import androidx.work.WorkerParameters
import day.bark.android.BarkUpdatePolicy
import java.io.IOException

class BarkUpdateWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result = synchronized(updateLock) {
        val store = BarkUpdateStore(applicationContext)
        try {
            BarkUpdateInstaller.reconcile(applicationContext)
            when (inputData.getString("operation")) {
                "download" -> download(store)
                else -> check(store)
            }
        } catch (error: Exception) {
            store.status = "Update not completed: ${error.message.orEmpty().take(180)}"
            // Rejected manifests/APKs must not be retried in a tight loop. A later
            // manual/daily check can obtain a corrected release.
            if (error is IOException && runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun check(store: BarkUpdateStore): Result {
        store.attemptedAt = System.currentTimeMillis()
        store.status = "Checking for updates"
        val release = BarkUpdateHttpClient().latest()
        val installed = BarkApkVerifier(applicationContext).installed()
        store.checkedAt = System.currentTimeMillis()
        // A pending session owns its immutable release snapshot. Do not replace
        // it with a newer manifest and strand a lost confirmation callback.
        if (store.sessionId >= 0 && store.sessionRelease?.versionCode == store.sessionVersion) {
            store.status = "Complete the pending installation before starting another update"
            return Result.success()
        }
        store.release = release
        when {
            release.versionCode <= installed.versionCode -> {
                store.status = if (release.versionCode == installed.versionCode) "Up to date" else "Published version is older; keeping the installed app"
                if (store.sessionId < 0) store.clearOldDownloads(null)
            }
            release.minSdk > Build.VERSION.SDK_INT -> store.status = "Update ${release.versionName} requires Android API ${release.minSdk}"
            installed.signers != setOf(BarkUpdatePolicy.RELEASE_SIGNER) -> store.status = "Automatic updates unavailable: this installation uses a different signing key"
            else -> {
                store.status = "Version ${release.versionName} is available"
                if (store.automatic && store.sessionId < 0) BarkUpdateScheduler.download(applicationContext, manual = false)
                else if (store.sessionId < 0) BarkUpdateNotifications.show(applicationContext, "Bark ${release.versionName} is available")
            }
        }
        return Result.success()
    }

    private fun download(store: BarkUpdateStore): Result {
        val manual = inputData.getBoolean("manual", false)
        if (!manual && !store.automatic) return Result.success()
        if (!manual && !isUnmeteredWifi()) return Result.retry()
        val release = store.release ?: return Result.failure()
        val verifier = BarkApkVerifier(applicationContext)
        if (!release.isNewerThan(verifier.installed().versionCode, Build.VERSION.SDK_INT)) return Result.success()
        if (store.sessionId >= 0) return Result.success()
        val file = store.file(release)
        if (!file.isFile || runCatching { verifier.verify(release, file) }.isFailure) {
            file.delete()
            val partial = store.partial(release)
            partial.delete()
            store.status = "Downloading ${release.versionName}"
            try {
                BarkUpdateHttpClient().download(release, partial) {
                    !isStopped && (manual || (store.automatic && isUnmeteredWifi()))
                }
                verifier.verify(release, partial)
                check(partial.renameTo(file)) { "Could not save the verified update" }
            } finally { partial.delete() }
        }
        verifier.verify(release, file)
        store.clearOldDownloads(file)
        store.status = "Version ${release.versionName} downloaded and verified"
        if (!manual && store.automatic && !isStopped && isUnmeteredWifi()) {
            BarkUpdateInstaller.install(applicationContext, release, manual = false)
        } else {
            BarkUpdateNotifications.show(applicationContext, "Bark ${release.versionName} is ready to install")
        }
        return Result.success()
    }

    private fun isUnmeteredWifi(): Boolean {
        val manager = applicationContext.getSystemService(ConnectivityManager::class.java)
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !manager.isActiveNetworkMetered
    }

    companion object { private val updateLock = Any() }
}
