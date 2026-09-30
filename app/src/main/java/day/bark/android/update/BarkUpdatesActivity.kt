package day.bark.android.update

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import day.bark.android.BuildConfig
import java.lang.ref.WeakReference
import java.text.DateFormat
import java.util.Date

class BarkUpdatesActivity : ComponentActivity() {
    private lateinit var store: BarkUpdateStore
    private var revision by mutableStateOf(0)
    private var busy by mutableStateOf(false)
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> runOnUiThread { revision++ } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = BarkUpdateStore(this)
        store.prefs.registerOnSharedPreferenceChangeListener(listener)
        setContent {
            val updated = revision
            val release = store.sessionRelease ?: store.release
            val available = release?.isNewerThan(BuildConfig.VERSION_CODE.toLong(), android.os.Build.VERSION.SDK_INT) == true
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("App updates", style = MaterialTheme.typography.headlineMedium)
                        Text("Installed: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                        Text("Updates are published at bark.atrl.me and must match this app's signing key.")
                        Text(store.status, style = MaterialTheme.typography.titleMedium)
                        if (store.checkedAt > 0) Text("Last checked: ${DateFormat.getDateTimeInstance().format(Date(store.checkedAt))}")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(store.automatic, { BarkUpdateScheduler.setAutomatic(this@BarkUpdatesActivity, it) })
                            Text("Automatically download and install on Wi-Fi")
                        }
                        Text("Automatic downloads use unmetered Wi-Fi. Android may still ask you to confirm installation. You can turn automatic updates off at any time.")
                        OutlinedButton(onClick = { BarkUpdateScheduler.check(this@BarkUpdatesActivity) }) { Text("Check now") }
                        if (available) {
                            Text("Available: ${release.versionName}", style = MaterialTheme.typography.titleLarge)
                            if (release.releaseNotes.isNotBlank()) Text(release.releaseNotes)
                            if (!store.file(release).isFile) {
                                Button(onClick = { BarkUpdateScheduler.download(this@BarkUpdatesActivity, manual = true) }) { Text("Download now (any network)") }
                            } else {
                                Button(enabled = !busy, onClick = { install() }) {
                                    Text(if (store.sessionPhase == "confirmation") "Continue installation" else "Install update")
                                }
                            }
                        }
                        if (!packageManager.canRequestPackageInstalls()) {
                            OutlinedButton(onClick = { openInstallPermission() }) { Text("Allow app updates in Android settings") }
                        }
                        OutlinedButton(onClick = { finish() }) { Text("Back") }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        visible = WeakReference(this)
        BarkUpdateScheduler.onForeground(this)
        revision++
        if (store.installAfterPermission && packageManager.canRequestPackageInstalls()) {
            store.installAfterPermission = false
            install()
        }
    }

    override fun onPause() {
        if (visible?.get() === this) visible = null
        super.onPause()
    }

    override fun onDestroy() {
        store.prefs.unregisterOnSharedPreferenceChangeListener(listener)
        super.onDestroy()
    }

    private fun install() {
        if (busy) return
        val release = store.sessionRelease ?: store.release ?: return
        if (!packageManager.canRequestPackageInstalls()) {
            store.installAfterPermission = true
            openInstallPermission()
            return
        }
        busy = true
        Thread {
            try { BarkUpdateInstaller.install(applicationContext, release, manual = true) }
            catch (error: Exception) { store.status = "Update not installed: ${error.message.orEmpty().take(180)}" }
            finally { runOnUiThread { busy = false; revision++ } }
        }.start()
    }

    private fun openInstallPermission() {
        startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
    }

    companion object {
        private var visible: WeakReference<BarkUpdatesActivity>? = null
        fun showConfirmationIfVisible(intent: Intent): Boolean {
            val activity = visible?.get() ?: return false
            if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return false
            return runCatching { activity.startActivity(intent); true }.getOrDefault(false)
        }
    }
}
