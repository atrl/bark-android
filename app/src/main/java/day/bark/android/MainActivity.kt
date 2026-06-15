@file:Suppress("DEPRECATION")

package day.bark.android

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.zxing.integration.android.IntentIntegrator
import java.io.File
import java.net.URLConnection
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private enum class MainTab { SERVICE, HISTORY, SETTINGS }

    private lateinit var settings: BarkSettingsStore
    private lateinit var store: BarkMessageStore
    private lateinit var serverInput: EditText
    private lateinit var keyInput: EditText
    private lateinit var serverNameInput: EditText
    private lateinit var pushTitleInput: EditText
    private lateinit var pushSubtitleInput: EditText
    private lateinit var pushDeviceKeysInput: EditText
    private lateinit var pushBodyInput: EditText
    private lateinit var pushIdInput: EditText
    private lateinit var pushMarkdownInput: EditText
    private lateinit var pushSoundInput: EditText
    private lateinit var pushLevelInput: EditText
    private lateinit var pushIconInput: EditText
    private lateinit var pushImageInput: EditText
    private lateinit var pushUrlInput: EditText
    private lateinit var pushActionInput: EditText
    private lateinit var pushCiphertextInput: EditText
    private lateinit var pushIvInput: EditText
    private lateinit var pushGroupInput: EditText
    private lateinit var pushVolumeInput: EditText
    private lateinit var pushBadgeInput: EditText
    private lateinit var pushCopyInput: EditText
    private lateinit var pushArchiveInput: EditText
    private lateinit var pushTtlInput: EditText
    private lateinit var pushCallCheck: CheckBox
    private lateinit var pushCriticalCheck: CheckBox
    private lateinit var pushAutoCopyCheck: CheckBox
    private lateinit var pushDeleteCheck: CheckBox
    private lateinit var archiveCheck: CheckBox
    private lateinit var algorithmInput: EditText
    private lateinit var modeInput: EditText
    private lateinit var paddingInput: EditText
    private lateinit var cryptoKeyInput: EditText
    private lateinit var ivInput: EditText
    private lateinit var statusText: TextView
    private lateinit var contentScroll: ScrollView
    private lateinit var contentRoot: LinearLayout
    private lateinit var currentServerPanel: LinearLayout
    private lateinit var exampleList: LinearLayout
    private lateinit var soundList: LinearLayout
    private lateinit var historySearchInput: EditText
    private lateinit var historyFilters: LinearLayout
    private lateinit var historyList: LinearLayout
    private var currentTab = MainTab.SERVICE
    private var selectedHistoryGroups: Set<String?> = emptySet()
    private var historySearchText: String? = null
    private var pendingQrScan = false
    private var currentSoundPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = BarkSettingsStore(this)
        store = BarkMessageStore(this)
        requestNotificationPermission()
        setContentView(buildContentView())
        loadSettings()
        refreshServers()
        refreshSounds()
        refreshHistory()
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        refreshHistory()
    }

    override fun onDestroy() {
        stopSound()
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == SOUND_IMPORT_REQUEST) {
            val uri = data?.data
            if (resultCode == RESULT_OK && uri != null) {
                importCustomSound(uri)
            } else {
                status("Import cancelled")
            }
            return
        }
        if (requestCode == HISTORY_IMPORT_REQUEST) {
            val uri = data?.data
            if (resultCode == RESULT_OK && uri != null) {
                importHistory(uri)
            } else {
                status("Import cancelled")
            }
            return
        }
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            val contents = result.contents
            if (contents.isNullOrBlank()) {
                status("Scan cancelled")
            } else if (!importServerLink(listOf(contents))) {
                status("Invalid Bark URL")
            }
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST && pendingQrScan) {
            pendingQrScan = false
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                startQrScanner()
            } else {
                status("Camera permission required")
            }
        }
    }

    private fun buildContentView(): View {
        initializeInputs()

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 36, 36, 36)
        }

        outer.addView(title("Bark"))
        outer.addView(row(
            button("Service") { showTab(MainTab.SERVICE) },
            button("History") { showTab(MainTab.HISTORY) },
            button("Settings") { showTab(MainTab.SETTINGS) },
        ))

        statusText = TextView(this).apply { textSize = 14f }
        outer.addView(statusText)

        contentRoot = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        contentScroll = ScrollView(this).apply { addView(contentRoot) }
        outer.addView(
            contentScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )

        showTab(MainTab.SERVICE)
        return outer
    }

    private fun initializeInputs() {
        serverInput = edit("Server")
        keyInput = edit("Key")
        serverNameInput = edit("Name")
        pushTitleInput = edit("Title")
        pushSubtitleInput = edit("Subtitle")
        pushDeviceKeysInput = edit("Device Keys")
        pushBodyInput = edit("Body")
        pushIdInput = edit("Notification ID")
        pushMarkdownInput = edit("Markdown")
        pushSoundInput = edit("Sound")
        pushLevelInput = edit("Level")
        pushIconInput = edit("Icon URL")
        pushImageInput = edit("Image URL")
        pushUrlInput = edit("Tap URL")
        pushActionInput = edit("Action")
        pushCiphertextInput = edit("Ciphertext")
        pushIvInput = edit("IV")
        pushGroupInput = edit("Group")
        pushVolumeInput = edit("Volume 0-10")
        pushBadgeInput = edit("Badge")
        pushCopyInput = edit("Copy")
        pushArchiveInput = edit("isArchive 1/0")
        pushTtlInput = edit("TTL seconds")
        pushCallCheck = CheckBox(this).apply { text = "Call" }
        pushCriticalCheck = CheckBox(this).apply { text = "Critical" }
        pushAutoCopyCheck = CheckBox(this).apply { text = "Auto Copy" }
        pushDeleteCheck = CheckBox(this).apply { text = "Delete" }

        exampleList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        archiveCheck = CheckBox(this).apply { text = "Archive" }

        algorithmInput = edit("Algorithm")
        modeInput = edit("Mode")
        paddingInput = edit("Padding")
        cryptoKeyInput = edit("Key")
        ivInput = edit("IV")

        soundList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        historySearchInput = edit("Search history")
        historyFilters = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        historyList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    }

    private fun showTab(tab: MainTab) {
        currentTab = tab
        contentRoot.removeAllViews()
        when (tab) {
            MainTab.SERVICE -> buildServiceTab()
            MainTab.HISTORY -> buildHistoryTab()
            MainTab.SETTINGS -> buildSettingsTab()
        }
    }

    private fun buildServiceTab() {
        val serviceRoot = contentRoot
        currentServerPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 18, 0, 18)
        }
        serviceRoot.addView(currentServerPanel)
        serviceRoot.addView(row(
            button("Register Current Server") { registerCurrentServer() },
            button("Start Listening") { startPollingService() },
            button("Stop Listening") { stopPollingService() },
        ))
        serviceRoot.addView(row(
            button("Copy Push URL") { copyCurrentPushUrl() },
            button("Ping") { pingSelectedServer() },
            button("Servers") { showServerPicker() },
        ))
        serviceRoot.addView(row(
            button("Add Server") { showAddServerDialog() },
            button("Import URL") { importServerLink() },
            button("Scan QR") { scanQrCode() },
        ))
        serviceRoot.addView(row(
            button("Register All") { registerAllServers() },
            button("Test") { sendTestPush() },
        ))
        serviceRoot.addView(section("Examples"))
        exampleList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        serviceRoot.addView(exampleList)
        refreshServiceSummary()
        refreshExamples()
    }

    private fun buildHistoryTab() {
        contentRoot.addView(section("History"))
        contentRoot.addView(row(
            button("Export History") { exportHistory() },
            button("Import History") { startHistoryImport() },
        ))
        contentRoot.addReusableView(historySearchInput)
        contentRoot.addView(row(
            button("Search") {
                historySearchText = historySearchInput.text.toString().takeIf { it.isNotBlank() }
                selectedHistoryGroups = emptySet()
                refreshHistory()
            },
            button("Reset") {
                historySearchText = null
                historySearchInput.setText("")
                selectedHistoryGroups = emptySet()
                refreshHistory()
            },
        ))
        contentRoot.addView(row(
            button("Clear 1h") { clearHistory(BarkHistoryDeleteRange.LAST_HOUR) },
            button("Clear Today") { clearHistory(BarkHistoryDeleteRange.TODAY) },
            button("Clear Today+Yesterday") { clearHistory(BarkHistoryDeleteRange.TODAY_AND_YESTERDAY) },
        ))
        contentRoot.addView(row(
            button("Clear Month") { clearHistory(BarkHistoryDeleteRange.LAST_MONTH) },
            button("Before 1h") { clearHistory(BarkHistoryDeleteRange.BEFORE_ONE_HOUR) },
            button("Before Today") { clearHistory(BarkHistoryDeleteRange.BEFORE_TODAY) },
        ))
        contentRoot.addView(row(
            button("Before Yesterday") { clearHistory(BarkHistoryDeleteRange.BEFORE_YESTERDAY) },
            button("Clear Old") { clearHistory(BarkHistoryDeleteRange.BEFORE_ONE_MONTH) },
            button("Clear All") { clearHistory(BarkHistoryDeleteRange.ALL_TIME) },
        ))
        historyFilters = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        contentRoot.addView(historyFilters)
        historyList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        contentRoot.addView(historyList)
        refreshHistory()
    }

    private fun buildSettingsTab() {
        contentRoot.addView(section("Server"))
        contentRoot.addReusableView(serverInput)
        contentRoot.addReusableView(keyInput)
        contentRoot.addReusableView(serverNameInput)
        contentRoot.addView(row(
            button("Save") { saveSettings() },
            button("Add Server") { addServerProfile() },
            button("Check") { pingSelectedServer() },
        ))

        contentRoot.addView(section("Push"))
        contentRoot.addView(row(pushTitleInput, pushSubtitleInput))
        contentRoot.addReusableView(pushDeviceKeysInput)
        contentRoot.addReusableView(pushBodyInput)
        contentRoot.addReusableView(pushIdInput)
        contentRoot.addReusableView(pushMarkdownInput)
        contentRoot.addView(row(pushSoundInput, pushLevelInput))
        contentRoot.addView(row(pushVolumeInput, pushBadgeInput))
        contentRoot.addView(row(pushIconInput, pushImageInput))
        contentRoot.addReusableView(pushUrlInput)
        contentRoot.addReusableView(pushActionInput)
        contentRoot.addView(row(pushCiphertextInput, pushIvInput))
        contentRoot.addReusableView(pushGroupInput)
        contentRoot.addReusableView(pushCopyInput)
        contentRoot.addView(row(pushArchiveInput, pushTtlInput))
        contentRoot.addView(row(pushCallCheck, pushCriticalCheck, pushAutoCopyCheck, pushDeleteCheck))
        contentRoot.addView(row(
            button("Send Push") { sendCustomPush() },
        ))

        contentRoot.addView(section("Archive"))
        contentRoot.addReusableView(archiveCheck)

        contentRoot.addView(section("Crypto"))
        contentRoot.addView(row(algorithmInput, modeInput, paddingInput))
        contentRoot.addReusableView(cryptoKeyInput)
        contentRoot.addReusableView(ivInput)
        contentRoot.addView(row(
            button("Save Crypto") { saveSettings() },
            button("Copy Example") { copyCryptoExample() },
        ))

        contentRoot.addView(section("Info"))
        contentRoot.addView(TextView(this).apply {
            textSize = 14f
            text = "Device Token\n${BarkDeviceTokenText.mask(settings.installToken)}"
        })
        contentRoot.addView(row(
            button("Copy Token") { copyDeviceToken() },
        ))
        contentRoot.addView(row(
            button("FAQ") { openExternalUrl(BARK_FAQ_URL) },
            button("Documentation") { openExternalUrl(BARK_DOC_URL) },
        ))
        contentRoot.addView(row(
            button("Source Code") { openExternalUrl(BARK_SOURCE_URL) },
            button("Privacy Policy") { openExternalUrl(BARK_PRIVACY_URL) },
        ))

        contentRoot.addView(section("Sounds"))
        contentRoot.addView(button("Import Sound") { startSoundImport() })
        soundList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        contentRoot.addView(soundList)
        refreshSounds()
    }

    private fun loadSettings() {
        serverInput.setText(settings.serverUrl)
        keyInput.setText(settings.deviceKey.orEmpty())
        serverNameInput.setText(settings.serverName.orEmpty())
        archiveCheck.isChecked = settings.archiveEnabled
        algorithmInput.setText(settings.cryptoAlgorithm)
        modeInput.setText(settings.cryptoMode)
        paddingInput.setText(settings.cryptoPadding)
        cryptoKeyInput.setText(settings.cryptoKey.orEmpty())
        ivInput.setText(settings.cryptoIv.orEmpty())
        refreshExamples()
    }

    private fun saveSettings() {
        val currentId = settings.serverProfiles().currentId
        settings.serverUrl = serverInput.text.toString()
        settings.deviceKey = keyInput.text.toString().takeIf { it.isNotBlank() }
        settings.renameServer(currentId, serverNameInput.text.toString().takeIf { it.isNotBlank() })
        settings.archiveEnabled = archiveCheck.isChecked
        settings.cryptoAlgorithm = algorithmInput.text.toString()
        settings.cryptoMode = modeInput.text.toString()
        settings.cryptoPadding = paddingInput.text.toString()
        settings.cryptoKey = cryptoKeyInput.text.toString().takeIf { it.isNotBlank() }
        settings.cryptoIv = ivInput.text.toString().takeIf { it.isNotBlank() }
        status("Saved")
        refreshServers()
        refreshExamples()
    }

    private fun copyCryptoExample() {
        try {
            val script = BarkCryptoExampleScript.create(
                profile = settings.serverProfiles().current(BarkSettingsStore.DEFAULT_ANDROID_SERVER),
                settings = CryptoSettings(
                    algorithm = algorithmInput.text.toString(),
                    mode = modeInput.text.toString(),
                    padding = paddingInput.text.toString(),
                    key = cryptoKeyInput.text.toString(),
                    iv = ivInput.text.toString().takeIf { it.isNotBlank() },
                ),
            )
            copyText(script)
            settings.cryptoAlgorithm = algorithmInput.text.toString()
            settings.cryptoMode = modeInput.text.toString()
            settings.cryptoPadding = paddingInput.text.toString()
            settings.cryptoKey = cryptoKeyInput.text.toString().takeIf { it.isNotBlank() }
            settings.cryptoIv = ivInput.text.toString().takeIf { it.isNotBlank() }
            status("Copied crypto example")
        } catch (error: IllegalArgumentException) {
            status(error.message ?: "Invalid crypto settings")
        }
    }

    private fun registerDevice() {
        registerAllServers()
    }

    private fun registerCurrentServer() {
        saveSettings()
        status("Registering")
        background {
            val profile = settings.serverProfiles().current(BarkSettingsStore.DEFAULT_ANDROID_SERVER)
            runCatching {
                registerProfile(profile)
            }.onSuccess { result ->
                runOnUiThread {
                    keyInput.setText(result.deviceKey)
                    refreshServers()
                    refreshExamples()
                    status("Registered")
                }
            }.onFailure { error ->
                runOnUiThread { status(error.message ?: "Registration failed") }
            }
        }
    }

    private fun registerAllServers() {
        saveSettings()
        status("Registering all servers")
        background {
            val profiles = settings.serverProfiles()
            var currentKey: String? = null
            var registeredCount = 0
            val failures = mutableListOf<String>()
            for (profile in profiles.profiles) {
                runCatching {
                    registerProfile(profile)
                }.onSuccess { result ->
                    registeredCount += 1
                    if (profile.id == profiles.currentId) {
                        currentKey = result.deviceKey
                    }
                }.onFailure {
                    failures += profile.displayName
                }
            }
            runOnUiThread {
                currentKey?.let(keyInput::setText)
                refreshServers()
                refreshExamples()
                status(
                    if (failures.isEmpty()) {
                        "Registered"
                    } else {
                        "Registered $registeredCount, failed ${failures.size}"
                    },
                )
            }
        }
    }

    private fun registerProfile(profile: BarkServerProfile): RegistrationResult {
        val result = BarkServerClient(profile.address)
            .register(profile.key.takeIf { it.isNotBlank() }, settings.installToken)
        settings.updateServerKey(profile.id, result.deviceKey)
        return result
    }

    private fun addServerProfile() {
        saveSettings()
        val profile = settings.addServer(
            address = serverInput.text.toString(),
            key = keyInput.text.toString().takeIf { it.isNotBlank() },
            name = serverNameInput.text.toString().takeIf { it.isNotBlank() },
        )
        loadSettings()
        refreshServers()
        status("Added ${profile.address}")
    }

    private fun importServerLink() {
        if (!importServerLink(listOf(serverInput.text.toString(), keyInput.text.toString(), clipboardText()))) {
            status("Invalid Bark URL")
        }
    }

    private fun scanQrCode() {
        if (Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingQrScan = true
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
            return
        }
        startQrScanner()
    }

    private fun startQrScanner() {
        IntentIntegrator(this).apply {
            setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            setPrompt("Scan Bark QR code")
            setBeepEnabled(false)
            initiateScan()
        }
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == ACTION_SHOW_MESSAGE_ALERT) {
            showNotificationAlert(intent)
            intent.setAction(null)
            return
        }
        if (intent != null && handleShareIntent(intent)) {
            intent.setAction(null)
            return
        }
        if (intent?.action != Intent.ACTION_VIEW) return
        if (handleHistoryDeepLink(intent.data)) {
            intent.setAction(null)
            return
        }
        val data = intent.dataString.orEmpty()
        if (data.isNotBlank() && !importServerLink(listOf(data))) {
            status("Invalid Bark URL")
        }
    }

    private fun handleHistoryDeepLink(uri: Uri?): Boolean {
        if (uri?.scheme?.equals("bark", ignoreCase = true) == true &&
            uri.host?.equals("history", ignoreCase = true) == true
        ) {
            historySearchText = null
            historySearchInput.setText("")
            selectedHistoryGroups = emptySet()
            uri.getQueryParameter("group")
                ?.let(BarkHistoryView::normalizeGroup)
                ?.let { group -> selectedHistoryGroups = setOf(group) }
            showTab(MainTab.HISTORY)
            refreshHistory()
            contentScroll.post { contentScroll.fullScroll(View.FOCUS_DOWN) }
            return true
        }
        return false
    }

    private fun handleShareIntent(intent: Intent): Boolean {
        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            val key = settings.deviceKey
            if (key.isNullOrBlank()) {
                status("Register first")
                return true
            }
            val request = BarkPushRequest.fromSharedText(
                subject = intent.getStringExtra(Intent.EXTRA_SUBJECT),
                text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
            )
            status("Sending shared push")
            background {
                try {
                    BarkServerClient(settings.serverUrl).push(key, request)
                    runOnUiThread { status("Shared push sent") }
                } catch (error: Exception) {
                    runOnUiThread { status(error.message ?: "Shared push failed") }
                }
            }
            return true
        }
        return false
    }

    private fun showNotificationAlert(intent: Intent) {
        val title = intent.getStringExtra(EXTRA_ALERT_TITLE)
            ?: intent.getStringExtra(EXTRA_ALERT_SUBTITLE)
            ?: getString(R.string.app_name)
        val body = intent.getStringExtra(EXTRA_ALERT_BODY).orEmpty()
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(body)
            .setPositiveButton("Copy") { _, _ ->
                copyText(intent.getStringExtra(EXTRA_ALERT_COPY) ?: body)
                status("Copied")
            }
            .setNeutralButton("More") { _, _ ->
                shareNotificationAlert(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun shareNotificationAlert(intent: Intent) {
        val shareText = intent.getStringExtra(EXTRA_ALERT_SHARE)
            ?: intent.getStringExtra(EXTRA_ALERT_BODY)
            ?: return
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, shareText),
                null,
            ),
        )
    }

    private fun importServerLink(candidates: List<String>): Boolean {
        val link = candidates.firstNotNullOfOrNull { BarkServerLinkParser.parse(it) }
        if (link == null) {
            return false
        }
        val profile = settings.addServer(address = link.address, key = link.key)
        loadSettings()
        refreshServers()
        status("Imported ${profile.address}")
        return true
    }

    private fun refreshServers() {
        refreshServiceSummary()
    }

    private fun refreshServiceSummary() {
        if (!::currentServerPanel.isInitialized) return
        currentServerPanel.removeAllViews()
        val profiles = settings.serverProfiles().normalized(BarkSettingsStore.DEFAULT_ANDROID_SERVER)
        val profile = profiles.current(BarkSettingsStore.DEFAULT_ANDROID_SERVER)
        currentServerPanel.addView(TextView(this).apply {
            textSize = 18f
            text = profile.displayName
        })
        currentServerPanel.addView(TextView(this).apply {
            textSize = 14f
            text = buildString {
                append(profile.address)
                append("\nKey: ")
                append(profile.key.takeIf { it.isNotBlank() }?.let(BarkDeviceTokenText::mask) ?: "Not registered")
                append("\nListening: ")
                append(if (settings.listeningEnabled) "On" else "Off")
            }
        })
    }

    private fun copyCurrentPushUrl() {
        val profile = settings.serverProfiles().current(BarkSettingsStore.DEFAULT_ANDROID_SERVER)
        copyText(profile.pushBaseUrl)
        status("Copied push URL")
    }

    private fun showServerPicker() {
        val profiles = settings.serverProfiles().normalized(BarkSettingsStore.DEFAULT_ANDROID_SERVER)
        val labels = profiles.profiles.map { profile ->
            if (profile.id == profiles.currentId) "* ${profile.displayName}" else profile.displayName
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Servers")
            .setItems(labels) { _, index ->
                showServerActions(profiles.profiles[index])
            }
            .setPositiveButton("Add Server") { _, _ -> showAddServerDialog() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showServerActions(profile: BarkServerProfile) {
        AlertDialog.Builder(this)
            .setTitle(profile.displayName)
            .setItems(serverActionLabels(profile)) { _, index ->
                when (serverActionLabels(profile)[index]) {
                    "Use" -> {
                        settings.selectServer(profile.id)
                        loadSettings()
                        refreshServers()
                        refreshExamples()
                        status("Selected ${profile.address}")
                    }
                    "Copy Address and Key" -> {
                        copyText(profile.pushBaseUrl)
                        status("Copied")
                    }
                    "Check" -> pingServer(profile.address)
                    "Rename" -> renameServer(profile.id)
                    "Register / Reset Key" -> resetServer(profile)
                    "Delete" -> deleteServer(profile)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun serverActionLabels(profile: BarkServerProfile): Array<String> =
        arrayOf(
            "Use",
            "Copy Address and Key",
            "Check",
            "Rename",
            "Register / Reset Key",
            "Delete",
        )

    private fun showAddServerDialog() {
        val addressInput = edit("Server").apply { setText("https://") }
        val nameInput = edit("Name")
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 12, 36, 0)
            addView(addressInput)
            addView(nameInput)
        }
        AlertDialog.Builder(this)
            .setTitle("Add Server")
            .setView(form)
            .setPositiveButton("Add") { _, _ ->
                val address = addressInput.text.toString()
                val name = nameInput.text.toString().takeIf { it.isNotBlank() }
                addServerProfile(address, name)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addServerProfile(address: String, name: String?) {
        status("Checking $address")
        background {
            val online = BarkServerClient(address).ping()
            if (!online) error("Invalid server")
            val profile = settings.addServer(address = address, key = null, name = name)
            runOnUiThread {
                loadSettings()
                refreshServers()
                refreshExamples()
                status("Added ${profile.address}")
            }
        }
    }

    private fun refreshExamples() {
        exampleList.removeAllViews()
        BarkPushExampleCatalog.examples.forEach { example ->
            val exampleUrl = example.url(settings.serverProfiles().current(BarkSettingsStore.DEFAULT_ANDROID_SERVER))
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8, 0, 8)
            }
            item.addView(TextView(this).apply {
                textSize = 14f
                text = "${example.body}\n${example.notice}\n$exampleUrl"
            })
            item.addView(row(
                button("Copy") { copyPushExample(example) },
                button("Open") { openPushExample(example) },
            ))
            exampleList.addView(item)
        }
    }

    private fun copyPushExample(example: BarkPushExample) {
        copyText(example.url(settings.serverProfiles().current(BarkSettingsStore.DEFAULT_ANDROID_SERVER)))
        status("Copied example")
    }

    private fun openPushExample(example: BarkPushExample) {
        openExternalUrl(example.url(settings.serverProfiles().current(BarkSettingsStore.DEFAULT_ANDROID_SERVER)))
    }

    private fun refreshSounds() {
        soundList.removeAllViews()
        BarkCustomSoundStore(this).list().forEach { sound ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8, 0, 8)
            }
            item.addView(TextView(this).apply {
                textSize = 14f
                text = "Custom: ${sound.name}"
            })
            item.addView(row(
                button("Play") { playCustomSound(sound) },
                button("Copy") { copySoundName(sound.name) },
                button("Delete") { deleteCustomSound(sound) },
            ))
            soundList.addView(item)
        }
        BarkSoundCatalog.builtInSounds.forEach { sound ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8, 0, 8)
            }
            item.addView(TextView(this).apply {
                textSize = 14f
                text = sound.name
            })
            item.addView(row(
                button("Play") { playSound(sound.name) },
                button("Copy") { copySoundName(sound.name) },
            ))
            soundList.addView(item)
        }
    }

    private fun startSoundImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            setType("audio/*")
        }
        startActivityForResult(intent, SOUND_IMPORT_REQUEST)
    }

    private fun importCustomSound(uri: Uri) {
        try {
            val sound = BarkCustomSoundStore(this).import(uri)
            refreshSounds()
            status("Imported ${sound.name}")
        } catch (error: Exception) {
            status(error.message ?: "Import failed")
        }
    }

    private fun deleteCustomSound(sound: BarkCustomSound) {
        BarkCustomSoundStore(this).delete(sound)
        refreshSounds()
        status("Deleted ${sound.name}")
    }

    private fun copySoundName(soundName: String) {
        copyText(soundName)
        status("Copied $soundName")
    }

    private fun playCustomSound(sound: BarkCustomSound) {
        stopSound()
        val player = MediaPlayer.create(this, Uri.fromFile(sound.file))
        if (player == null) {
            status("Sound not found")
            return
        }
        currentSoundPlayer = player
        player.setOnCompletionListener { completed ->
            if (currentSoundPlayer === completed) {
                currentSoundPlayer = null
            }
            completed.release()
        }
        player.start()
        status("Playing ${sound.name}")
    }

    private fun playSound(soundName: String) {
        stopSound()
        val player = MediaPlayer.create(this, rawSoundResource(soundName))
        if (player == null) {
            status("Sound not found")
            return
        }
        currentSoundPlayer = player
        player.setOnCompletionListener { completed ->
            if (currentSoundPlayer === completed) {
                currentSoundPlayer = null
            }
            completed.release()
        }
        player.start()
        status("Playing $soundName")
    }

    private fun stopSound() {
        currentSoundPlayer?.let { player ->
            try {
                if (player.isPlaying) player.stop()
            } catch (_: Exception) {
            } finally {
                player.release()
            }
        }
        currentSoundPlayer = null
    }

    private fun rawSoundResource(soundName: String): Int =
        resources.getIdentifier(soundName, "raw", packageName)

    private fun renameServer(id: String) {
        val profile = settings.serverProfiles().profiles.firstOrNull { it.id == id }
        serverNameInput.setText(profile?.name.orEmpty())
        serverNameInput.detachFromParent()
        AlertDialog.Builder(this)
            .setTitle("Rename")
            .setView(serverNameInput)
            .setPositiveButton("Save") { _, _ ->
                settings.renameServer(id, serverNameInput.text.toString().takeIf { it.isNotBlank() })
                loadSettings()
                refreshServers()
                status("Renamed")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun resetServer(profile: BarkServerProfile) {
        status("Resetting ${profile.address}")
        background {
            if (profile.key.isNotBlank()) {
                runCatching { BarkServerClient(profile.address).unregister(profile.key) }
            }
            val result = BarkServerClient(profile.address).register(null, settings.installToken)
            settings.updateServerKey(profile.id, result.deviceKey)
            runOnUiThread {
                if (settings.serverProfiles().currentId == profile.id) {
                    keyInput.setText(result.deviceKey)
                }
                refreshServers()
                refreshExamples()
                status("Reset ${profile.address}")
            }
        }
    }

    private fun deleteServer(profile: BarkServerProfile) {
        status("Deleting ${profile.address}")
        background {
            if (profile.key.isNotBlank()) {
                runCatching { BarkServerClient(profile.address).unregister(profile.key) }
            }
            settings.removeServer(profile.id)
            runOnUiThread {
                loadSettings()
                refreshServers()
                refreshExamples()
                status("Deleted")
            }
        }
    }

    private fun pingSelectedServer() {
        saveSettings()
        pingServer(settings.serverUrl)
    }

    private fun pingServer(address: String) {
        status("Checking $address")
        background {
            val online = try {
                BarkServerClient(address).ping()
            } catch (_: Exception) {
                false
            }
            runOnUiThread {
                if (online) {
                    status("Online $address")
                } else {
                    status("Offline $address")
                }
            }
        }
    }

    private fun sendTestPush() {
        saveSettings()
        val key = settings.deviceKey
        if (key.isNullOrBlank()) {
            status("Register first")
            return
        }
        status("Sending")
        background {
            BarkServerClient(settings.serverUrl).pushTest(key)
            runOnUiThread { status("Sent") }
        }
    }

    private fun sendCustomPush() {
        saveSettings()
        val key = settings.deviceKey
        if (key.isNullOrBlank()) {
            status("Register first")
            return
        }
        val request = BarkPushRequest(
            title = pushTitleInput.text.toString(),
            subtitle = pushSubtitleInput.text.toString(),
            body = pushBodyInput.text.toString(),
            id = pushIdInput.text.toString(),
            markdown = pushMarkdownInput.text.toString(),
            level = pushLevelInput.text.toString(),
            isCall = pushCallCheck.isChecked,
            isCritical = pushCriticalCheck.isChecked,
            volume = pushVolumeInput.text.toString().toIntOrNull(),
            badge = pushBadgeInput.text.toString().toIntOrNull(),
            autoCopy = pushAutoCopyCheck.isChecked,
            copy = pushCopyInput.text.toString(),
            sound = pushSoundInput.text.toString(),
            icon = pushIconInput.text.toString(),
            image = pushImageInput.text.toString(),
            group = pushGroupInput.text.toString(),
            archive = pushArchiveInput.text.toString().toArchiveFlagOrNull(),
            ttlSeconds = pushTtlInput.text.toString().toLongOrNull(),
            url = pushUrlInput.text.toString(),
            action = pushActionInput.text.toString(),
            ciphertext = pushCiphertextInput.text.toString(),
            iv = pushIvInput.text.toString(),
            isDelete = pushDeleteCheck.isChecked,
        )
        val targetKeys = BarkPushTargetKeys.parse(pushDeviceKeysInput.text.toString()).ifEmpty { listOf(key) }
        status("Sending push")
        background {
            try {
                BarkServerClient(settings.serverUrl).push(targetKeys, request)
                runOnUiThread { status("Push sent") }
            } catch (error: Exception) {
                runOnUiThread { status(error.message ?: "Push failed") }
            }
        }
    }

    private fun String.toArchiveFlagOrNull(): Boolean? =
        trim().takeIf { it.isNotBlank() }?.let { it == "1" || it.equals("true", ignoreCase = true) }

    private fun startPollingService() {
        saveSettings()
        settings.listeningEnabled = true
        val intent = Intent(this, BarkPollingService::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        status("Listening")
    }

    private fun stopPollingService() {
        settings.listeningEnabled = false
        startService(Intent(this, BarkPollingService::class.java).setAction(BarkPollingService.ACTION_STOP))
        status("Stopped")
    }

    private fun refreshHistory() {
        historyList.removeAllViews()
        val messages = store.recent(searchText = historySearchText)
        refreshHistoryFilters(messages)
        val dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)

        if (selectedHistoryGroups.isEmpty()) {
            BarkHistoryView.groups(messages).forEach { group ->
                historyList.addView(groupHeader(group))
                group.messages.forEach { message ->
                    historyList.addView(historyItem(message, dateFormat))
                }
                historyList.addView(button("Clear Group") { clearHistoryGroup(group.group) })
                if (group.totalCount > group.messages.size) {
                    historyList.addView(button("View all ${group.displayName}") {
                        selectedHistoryGroups = setOf(group.group)
                        refreshHistory()
                    })
                }
            }
        } else {
            if (selectedHistoryGroups.size == 1) {
                historyList.addView(button("Clear Group") { clearHistoryGroup(selectedHistoryGroups.first()) })
            }
            BarkHistoryView.filter(messages, selectedHistoryGroups).forEach { message ->
                historyList.addView(historyItem(message, dateFormat))
            }
        }
    }

    private fun clearHistory(range: BarkHistoryDeleteRange) {
        store.delete(range)
        selectedHistoryGroups = emptySet()
        refreshHistory()
        status("Cleared")
    }

    private fun clearHistoryGroup(group: String?) {
        store.deleteGroup(group)
        selectedHistoryGroups = emptySet()
        refreshHistory()
        status("Cleared group")
    }

    private fun refreshHistoryFilters(messages: List<BarkMessage>) {
        historyFilters.removeAllViews()
        val groups = BarkHistoryView.groups(messages)
        if (selectedHistoryGroups.isNotEmpty() && groups.none { it.group in selectedHistoryGroups }) {
            selectedHistoryGroups = emptySet()
        }
        historyFilters.addView(button(if (selectedHistoryGroups.isEmpty()) "* All" else "All") {
            selectedHistoryGroups = emptySet()
            refreshHistory()
        })
        groups.forEach { group ->
            historyFilters.addView(button(historyFilterLabel(group)) {
                selectedHistoryGroups = setOf(group.group)
                refreshHistory()
            })
        }
    }

    private fun historyFilterLabel(group: BarkHistoryGroup): String =
        buildString {
            if (selectedHistoryGroups.size == 1 && selectedHistoryGroups.contains(group.group)) append("* ")
            append(group.displayName)
            append(" (").append(group.totalCount).append(")")
        }

    private fun groupHeader(group: BarkHistoryGroup): TextView =
        TextView(this).apply {
            textSize = 16f
            setPadding(0, 18, 0, 6)
            text = "${group.displayName} (${group.totalCount})"
            setOnClickListener {
                selectedHistoryGroups = setOf(group.group)
                refreshHistory()
            }
        }

    private fun historyItem(message: BarkMessage, dateFormat: DateFormat): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 12, 0, 18)
            addView(TextView(this@MainActivity).apply {
                textSize = 15f
                text = BarkHistoryMessageText.format(
                    message,
                    dateFormat.format(Date(message.createAtMillis)),
                    BarkExpiryText.format(message.expireAtMillis),
                )
            })
            message.image?.takeIf { it.isNotBlank() }?.let { imageUrl ->
                val imageView = ImageView(this@MainActivity).apply {
                    visibility = View.GONE
                    adjustViewBounds = true
                    maxHeight = 600
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    setPadding(0, 8, 0, 8)
                }
                addView(imageView)
                loadHistoryImage(imageUrl, imageView)
            }
            val openUrl = BarkTapAction.urlToOpen(message)
            val actionButtons = mutableListOf<View>(
                button("Copy") { copyHistoryMessage(message) },
            )
            if (openUrl != null) {
                actionButtons += button("Open") { openHistoryMessage(message) }
            }
            if (!message.image.isNullOrBlank()) {
                actionButtons += button("Open Image") { openHistoryImage(message) }
                actionButtons += button("Share Image") { shareHistoryImage(message) }
                actionButtons += button("Save Image") { saveHistoryImage(message) }
            }
            actionButtons += button("Delete") { deleteHistoryMessage(message) }
            addView(row(*actionButtons.toTypedArray()))
        }

    private fun loadHistoryImage(imageUrl: String, imageView: ImageView) {
        background {
            val bitmap = BarkRemoteImageCache(this).bitmap(imageUrl)
            runOnUiThread {
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                    imageView.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun copyHistoryMessage(message: BarkMessage) {
        copyText(BarkCopyText.from(message))
        status("Copied")
    }

    private fun openHistoryMessage(message: BarkMessage) {
        val url = BarkTapAction.urlToOpen(message) ?: return
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun openHistoryImage(message: BarkMessage) {
        val imageUrl = message.image?.takeIf { it.isNotBlank() } ?: return
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(imageUrl)))
    }

    private fun shareHistoryImage(message: BarkMessage) {
        val imageUrl = message.image?.takeIf { it.isNotBlank() } ?: return
        background {
            val file = BarkRemoteImageCache(this).file(imageUrl) ?: error("Image unavailable")
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.soundprovider",
                file,
            )
            runOnUiThread {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    setType(historyImageMimeType(imageUrl))
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, "Share Bark image"))
            }
        }
    }

    private fun saveHistoryImage(message: BarkMessage) {
        val imageUrl = message.image?.takeIf { it.isNotBlank() } ?: return
        background {
            val file = BarkRemoteImageCache(this).file(imageUrl) ?: error("Image unavailable")
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, historyImageFileName(imageUrl))
                put(MediaStore.Images.Media.MIME_TYPE, historyImageMimeType(imageUrl))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/Bark",
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Could not save image")
            try {
                contentResolver.openOutputStream(uri)?.use { output ->
                    file.inputStream().use { input -> input.copyTo(output) }
                } ?: error("Could not save image")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    contentResolver.update(uri, values, null, null)
                }
                runOnUiThread { status("Saved image") }
            } catch (error: Exception) {
                contentResolver.delete(uri, null, null)
                throw error
            }
        }
    }

    private fun historyImageMimeType(imageUrl: String): String =
        URLConnection.guessContentTypeFromName(Uri.parse(imageUrl).lastPathSegment.orEmpty())
            ?.takeIf { it.startsWith("image/") }
            ?: "image/*"

    private fun historyImageFileName(imageUrl: String): String {
        val stamp = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.US).format(Date())
        val extension = Uri.parse(imageUrl).lastPathSegment
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase(Locale.US)
            ?.takeIf { it.matches(Regex("[a-z0-9]{1,5}")) }
            ?: when (historyImageMimeType(imageUrl)) {
                "image/png" -> "png"
                "image/gif" -> "gif"
                "image/webp" -> "webp"
                else -> "jpg"
            }
        return "bark_image_$stamp.$extension"
    }

    private fun deleteHistoryMessage(message: BarkMessage) {
        store.delete(message.id)
        refreshHistory()
        status("Deleted")
    }

    private fun copyDeviceToken() {
        copyText(settings.installToken)
        status("Copied token")
    }

    private fun openExternalUrl(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            status("No browser available")
        }
    }

    private fun exportHistory() {
        background {
            val file = historyExportFile()
            file.parentFile?.mkdirs()
            file.writeText(store.exportBackupJson())
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.soundprovider",
                file,
            )
            runOnUiThread {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    setType("application/json")
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, "Export Bark history"))
                status("Exported history")
            }
        }
    }

    private fun historyExportFile(): File {
        val stamp = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.US).format(Date())
        return File(File(cacheDir, "history_exports"), "bark_messages_$stamp.json")
    }

    private fun startHistoryImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            setType("application/json")
        }
        startActivityForResult(intent, HISTORY_IMPORT_REQUEST)
    }

    private fun importHistory(uri: Uri) {
        background {
            val json = contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: error("Import failed")
            val count = store.restoreBackupJson(json)
            runOnUiThread {
                refreshHistory()
                status("Imported $count messages")
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

    private fun background(block: () -> Unit) {
        Thread {
            try {
                block()
            } catch (error: Exception) {
                runOnUiThread { status(error.message ?: error.javaClass.simpleName) }
            }
        }.start()
    }

    private fun status(value: String) {
        statusText.text = value
        Toast.makeText(this, value, Toast.LENGTH_SHORT).show()
    }

    private fun copyText(value: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Bark", value))
    }

    private fun clipboardText(): String {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(this)
            ?.toString()
            .orEmpty()
    }

    private fun title(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 28f
            setPadding(0, 0, 0, 24)
        }

    private fun section(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 18f
            setPadding(0, 30, 0, 10)
        }

    private fun edit(hint: String): EditText =
        EditText(this).apply {
            this.hint = hint
            setSingleLine(true)
        }

    private fun button(text: String, onClick: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            setOnClickListener { onClick() }
        }

    private fun row(vararg views: View): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            views.forEach { view ->
                view.detachFromParent()
                addView(view, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
        }

    private fun LinearLayout.addReusableView(view: View) {
        view.detachFromParent()
        addView(view)
    }

    private fun View.detachFromParent() {
        (parent as? ViewGroup)?.removeView(this)
    }

    companion object {
        const val ACTION_SHOW_MESSAGE_ALERT = "day.bark.android.action.SHOW_MESSAGE_ALERT"
        const val EXTRA_ALERT_TITLE = "day.bark.android.extra.ALERT_TITLE"
        const val EXTRA_ALERT_SUBTITLE = "day.bark.android.extra.ALERT_SUBTITLE"
        const val EXTRA_ALERT_BODY = "day.bark.android.extra.ALERT_BODY"
        const val EXTRA_ALERT_COPY = "day.bark.android.extra.ALERT_COPY"
        const val EXTRA_ALERT_SHARE = "day.bark.android.extra.ALERT_SHARE"
        private const val CAMERA_PERMISSION_REQUEST = 101
        private const val SOUND_IMPORT_REQUEST = 102
        private const val HISTORY_IMPORT_REQUEST = 103
        private const val BARK_FAQ_URL = "https://bark.day.app/#/en-us/faq"
        private const val BARK_DOC_URL = "https://bark.day.app/#/en-us/?id=bark"
        private const val BARK_SOURCE_URL = "https://github.com/Finb/Bark"
        private const val BARK_PRIVACY_URL = "https://api.day.app/privacy"
    }
}
