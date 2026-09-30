package day.bark.android.update

import android.content.Context
import day.bark.android.BarkAppRelease
import java.io.File

class BarkUpdateStore(context: Context) {
    val prefs = context.applicationContext.getSharedPreferences("bark_updates", Context.MODE_PRIVATE)
    private val directory = File(context.applicationContext.noBackupFilesDir, "updates").apply { mkdirs() }

    var automatic: Boolean
        get() = prefs.getBoolean("automatic", true)
        set(value) { prefs.edit().putBoolean("automatic", value).apply() }
    var status: String
        get() = prefs.getString("status", "No update check yet").orEmpty()
        set(value) { prefs.edit().putString("status", value).apply() }
    var attemptedAt: Long
        get() = prefs.getLong("attempted_at", 0)
        set(value) { prefs.edit().putLong("attempted_at", value).apply() }
    var checkedAt: Long
        get() = prefs.getLong("checked_at", 0)
        set(value) { prefs.edit().putLong("checked_at", value).apply() }
    var release: BarkAppRelease?
        get() = prefs.getString("release", null)?.let { runCatching { BarkAppRelease.parse(it) }.getOrNull() }
        set(value) { check(prefs.edit().putString("release", value?.toJson()).commit()) }
    var installAfterPermission: Boolean
        get() = prefs.getBoolean("install_after_permission", false)
        set(value) { prefs.edit().putBoolean("install_after_permission", value).apply() }

    val sessionId: Int get() = prefs.getInt("session_id", -1)
    val sessionNonce: String? get() = prefs.getString("session_nonce", null)
    val sessionVersion: Long get() = prefs.getLong("session_version", 0)
    val sessionPhase: String? get() = prefs.getString("session_phase", null)
    val sessionRelease: BarkAppRelease? get() = prefs.getString("session_release", null)
        ?.let { runCatching { BarkAppRelease.parse(it) }.getOrNull() }

    fun file(release: BarkAppRelease): File = File(directory, release.fileName)
    fun partial(release: BarkAppRelease): File = File(directory, "${release.fileName}.part")
    fun clearOldDownloads(keep: File?) { directory.listFiles()?.filter { it != keep }?.forEach(File::delete) }

    fun recordStaging(id: Int, release: BarkAppRelease) = synchronized(sessionLock) {
        check(prefs.edit().putInt("session_id", id).putLong("session_version", release.versionCode)
            .putString("session_release", release.toJson())
            .remove("session_nonce").putString("session_phase", "staging")
            .putString("status", "Preparing installation").commit())
    }

    fun recordSession(id: Int, nonce: String, release: BarkAppRelease) = synchronized(sessionLock) {
        check(prefs.edit().putInt("session_id", id).putString("session_nonce", nonce)
            .putLong("session_version", release.versionCode).putString("session_release", release.toJson())
            .putString("session_phase", "submitted")
            .putString("status", "Installing update; waiting for Android").commit())
    }

    fun waitingForConfirmation() = synchronized(sessionLock) {
        check(prefs.edit().putString("session_phase", "confirmation")
            .putString("status", "Android requires installation confirmation").commit())
    }

    fun clearSession(message: String) = synchronized(sessionLock) {
        check(prefs.edit().remove("session_id").remove("session_nonce").remove("session_version").remove("session_phase").remove("session_release")
            .putString("status", message).commit())
    }

    fun applyInstallResult(id: Int, nonce: String, pending: Boolean, message: String): Boolean = synchronized(sessionLock) {
        if (sessionId != id || sessionNonce != nonce) return@synchronized false
        if (pending) waitingForConfirmation() else clearSession(message)
        true
    }

    companion object { private val sessionLock = Any() }
}
