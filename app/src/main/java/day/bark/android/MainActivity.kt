@file:Suppress("DEPRECATION")
@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package day.bark.android

import android.Manifest
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
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
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.google.zxing.integration.android.IntentIntegrator
import java.io.File
import java.net.URLConnection
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private enum class MainTab(val title: String) {
        SERVICE("Service"),
        HISTORY("History"),
        SETTINGS("Settings"),
    }

    private lateinit var settings: BarkSettingsStore
    private lateinit var store: BarkMessageStore
    private var currentTab by mutableStateOf(MainTab.SERVICE)
    private var statusMessage by mutableStateOf("")
    private var uiVersion by mutableStateOf(0)
    private var selectedHistoryGroups by mutableStateOf<Set<String?>>(emptySet())
    private var historySearchText by mutableStateOf<String?>(null)
    private var historySearchInputText by mutableStateOf("")
    private var pendingQrScan = false
    private var currentSoundPlayer: MediaPlayer? = null

    private var serverUrlText by mutableStateOf("")
    private var deviceKeyText by mutableStateOf("")
    private var serverNameText by mutableStateOf("")
    private var pushTitleText by mutableStateOf("")
    private var pushSubtitleText by mutableStateOf("")
    private var pushDeviceKeysText by mutableStateOf("")
    private var pushBodyText by mutableStateOf("")
    private var pushIdText by mutableStateOf("")
    private var pushMarkdownText by mutableStateOf("")
    private var pushSoundText by mutableStateOf("")
    private var pushLevelText by mutableStateOf("")
    private var pushIconText by mutableStateOf("")
    private var pushImageText by mutableStateOf("")
    private var pushUrlText by mutableStateOf("")
    private var pushActionText by mutableStateOf("")
    private var pushCiphertextText by mutableStateOf("")
    private var pushIvText by mutableStateOf("")
    private var pushGroupText by mutableStateOf("")
    private var pushVolumeText by mutableStateOf("")
    private var pushBadgeText by mutableStateOf("")
    private var pushCopyText by mutableStateOf("")
    private var pushArchiveText by mutableStateOf("")
    private var pushTtlText by mutableStateOf("")
    private var pushCallChecked by mutableStateOf(false)
    private var pushCriticalChecked by mutableStateOf(false)
    private var pushAutoCopyChecked by mutableStateOf(false)
    private var pushDeleteChecked by mutableStateOf(false)
    private var archiveChecked by mutableStateOf(true)
    private var algorithmText by mutableStateOf("")
    private var modeText by mutableStateOf("")
    private var paddingText by mutableStateOf("")
    private var cryptoKeyText by mutableStateOf("")
    private var ivText by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = BarkSettingsStore(this)
        store = BarkMessageStore(this)
        requestNotificationPermission()
        loadSettings()
        setContent { BarkApp() }
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
        permissions: Array<String>,
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

    @Composable
    private fun BarkApp() {
        MaterialTheme(
            colorScheme = lightColorScheme(
                primary = Color(0xFF006C5B),
                secondary = Color(0xFF5964D8),
                tertiary = Color(0xFFC2410C),
                background = Color(0xFFF7F8FA),
                surface = Color(0xFFFFFFFF),
                surfaceVariant = Color(0xFFE7ECEF),
            ),
        ) {
            Surface(color = MaterialTheme.colorScheme.background) {
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            MainTab.entries.forEach { tab ->
                                NavigationBarItem(
                                    selected = currentTab == tab,
                                    onClick = { showTab(tab) },
                                    icon = { Text(tab.title.first().toString()) },
                                    label = { Text(tab.title) },
                                )
                            }
                        }
                    },
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        AppHeader()
                        StatusStrip()
                        val version = uiVersion
                        when (currentTab) {
                            MainTab.SERVICE -> ServiceScreen(version)
                            MainTab.HISTORY -> HistoryScreen(version)
                            MainTab.SETTINGS -> SettingsScreen(version)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun AppHeader() {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Bark", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Self-hosted push control",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    @Composable
    private fun StatusStrip() {
        if (statusMessage.isBlank()) return
        ElevatedCard(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Text(
                text = statusMessage,
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    @Composable
    private fun ServiceScreen(version: Int) {
        val profiles = settings.serverProfiles().normalized(BarkSettingsStore.DEFAULT_ANDROID_SERVER)
        val profile = profiles.current(BarkSettingsStore.DEFAULT_ANDROID_SERVER)
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ServerCard(profile, profiles.profiles.size, version)
            ActionPanel()
            ExamplesPanel()
        }
    }

    @Composable
    private fun ServerCard(profile: BarkServerProfile, serverCount: Int, version: Int) {
        ElevatedCard(shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(profile.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    profile.address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                InfoRow("Key", profile.key.takeIf { it.isNotBlank() }?.let(BarkDeviceTokenText::mask) ?: "Not registered")
                InfoRow("Listening", if (settings.listeningEnabled) "On" else "Off")
                InfoRow("Servers", serverCount.toString())
                Spacer(Modifier.height(2.dp))
                ActionFlow {
                    PrimaryAction("Register") { registerCurrentServer() }
                    if (settings.listeningEnabled) {
                        SecondaryAction("Stop Listening") { stopPollingService() }
                    } else {
                        PrimaryAction("Start Listening") { startPollingService() }
                    }
                    SecondaryAction("Copy URL") { copyCurrentPushUrl() }
                    SecondaryAction("Ping") { pingSelectedServer() }
                }
            }
        }
    }

    @Composable
    private fun ActionPanel() {
        SectionCard("Quick Actions") {
            ActionFlow {
                PrimaryAction("Test Push") { sendTestPush() }
                SecondaryAction("Servers") { showServerPicker() }
                SecondaryAction("Add Server") { showAddServerDialog() }
                SecondaryAction("Import URL") { importServerLink() }
                SecondaryAction("Scan QR") { scanQrCode() }
                SecondaryAction("Register All") { registerAllServers() }
            }
        }
    }

    @Composable
    private fun ExamplesPanel() {
        SectionCard("Examples") {
            BarkPushExampleCatalog.examples.forEachIndexed { index, example ->
                val exampleUrl = example.url(settings.serverProfiles().current(BarkSettingsStore.DEFAULT_ANDROID_SERVER))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(example.body, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(example.notice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(exampleUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryAction("Copy") { copyPushExample(example) }
                        SecondaryAction("Open") { openPushExample(example) }
                    }
                }
                if (index != BarkPushExampleCatalog.examples.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                }
            }
        }
    }

    @Composable
    private fun HistoryScreen(version: Int) {
        val messages = store.recent(searchText = historySearchText)
        val groups = BarkHistoryView.groups(messages)
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionCard("History") {
                Field("Search history", historySearchInputText) { historySearchInputText = it }
                ActionFlow {
                    PrimaryAction("Search") {
                        historySearchText = historySearchInputText.takeIf { it.isNotBlank() }
                        selectedHistoryGroups = emptySet()
                        refreshHistory()
                    }
                    SecondaryAction("Reset") {
                        historySearchText = null
                        historySearchInputText = ""
                        selectedHistoryGroups = emptySet()
                        refreshHistory()
                    }
                    SecondaryAction("Export") { exportHistory() }
                    SecondaryAction("Import") { startHistoryImport() }
                }
            }
            SectionCard("Cleanup") {
                ActionFlow {
                    SecondaryAction("Clear 1h") { clearHistory(BarkHistoryDeleteRange.LAST_HOUR) }
                    SecondaryAction("Clear Today") { clearHistory(BarkHistoryDeleteRange.TODAY) }
                    SecondaryAction("Today+Yesterday") { clearHistory(BarkHistoryDeleteRange.TODAY_AND_YESTERDAY) }
                    SecondaryAction("Clear Month") { clearHistory(BarkHistoryDeleteRange.LAST_MONTH) }
                    SecondaryAction("Before 1h") { clearHistory(BarkHistoryDeleteRange.BEFORE_ONE_HOUR) }
                    SecondaryAction("Before Today") { clearHistory(BarkHistoryDeleteRange.BEFORE_TODAY) }
                    SecondaryAction("Before Yesterday") { clearHistory(BarkHistoryDeleteRange.BEFORE_YESTERDAY) }
                    SecondaryAction("Clear Old") { clearHistory(BarkHistoryDeleteRange.BEFORE_ONE_MONTH) }
                    SecondaryAction("Clear All") { clearHistory(BarkHistoryDeleteRange.ALL_TIME) }
                    if (selectedHistoryGroups.size == 1) {
                        SecondaryAction("Clear Group") { clearHistoryGroup(selectedHistoryGroups.first()) }
                    }
                }
            }
            SectionCard("Groups") {
                ActionFlow {
                    SecondaryAction(if (selectedHistoryGroups.isEmpty()) "All *" else "All") {
                        selectedHistoryGroups = emptySet()
                        refreshHistory()
                    }
                    groups.forEach { group ->
                        SecondaryAction(historyFilterLabel(group)) {
                            selectedHistoryGroups = setOf(group.group)
                            refreshHistory()
                        }
                    }
                }
            }
            SectionCard("Messages") {
                val dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                val visibleMessages = if (selectedHistoryGroups.isEmpty()) {
                    groups.flatMap { it.messages }
                } else {
                    BarkHistoryView.filter(messages, selectedHistoryGroups)
                }
                if (visibleMessages.isEmpty()) {
                    Text("No messages", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                visibleMessages.forEachIndexed { index, message ->
                    HistoryItem(message, dateFormat)
                    if (index != visibleMessages.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    }
                }
            }
        }
    }

    @Composable
    private fun HistoryItem(message: BarkMessage, dateFormat: DateFormat) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                BarkHistoryMessageText.format(
                    message,
                    dateFormat.format(Date(message.createAtMillis)),
                    BarkExpiryText.format(message.expireAtMillis),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            message.image?.takeIf { it.isNotBlank() }?.let { imageUrl ->
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    factory = { context ->
                        ImageView(context).apply {
                            visibility = View.GONE
                            adjustViewBounds = true
                            scaleType = ImageView.ScaleType.CENTER_INSIDE
                            loadHistoryImage(imageUrl, this)
                        }
                    },
                )
            }
            ActionFlow {
                SecondaryAction("Copy") { copyHistoryMessage(message) }
                if (BarkTapAction.urlToOpen(message) != null) {
                    SecondaryAction("Open") { openHistoryMessage(message) }
                }
                if (!message.image.isNullOrBlank()) {
                    SecondaryAction("Open Image") { openHistoryImage(message) }
                    SecondaryAction("Share Image") { shareHistoryImage(message) }
                    SecondaryAction("Save Image") { saveHistoryImage(message) }
                }
                SecondaryAction("Delete") { deleteHistoryMessage(message) }
            }
        }
    }

    @Composable
    private fun SettingsScreen(version: Int) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionCard("Server") {
                Field("Server", serverUrlText) { serverUrlText = it }
                Field("Key", deviceKeyText) { deviceKeyText = it }
                Field("Name", serverNameText) { serverNameText = it }
                ActionFlow {
                    PrimaryAction("Save") { saveSettings() }
                    SecondaryAction("Add Server") { addServerProfile() }
                    SecondaryAction("Check") { pingSelectedServer() }
                }
            }
            SectionCard("Push Composer") {
                Field("Title", pushTitleText) { pushTitleText = it }
                Field("Subtitle", pushSubtitleText) { pushSubtitleText = it }
                Field("Device Keys", pushDeviceKeysText) { pushDeviceKeysText = it }
                Field("Body", pushBodyText, singleLine = false) { pushBodyText = it }
                Field("Notification ID", pushIdText) { pushIdText = it }
                Field("Markdown", pushMarkdownText, singleLine = false) { pushMarkdownText = it }
                TwoFields("Sound", pushSoundText, { pushSoundText = it }, "Level", pushLevelText, { pushLevelText = it })
                TwoFields("Volume 0-10", pushVolumeText, { pushVolumeText = it }, "Badge", pushBadgeText, { pushBadgeText = it })
                TwoFields("Icon URL", pushIconText, { pushIconText = it }, "Image URL", pushImageText, { pushImageText = it })
                Field("Tap URL", pushUrlText) { pushUrlText = it }
                Field("Action", pushActionText) { pushActionText = it }
                TwoFields("Ciphertext", pushCiphertextText, { pushCiphertextText = it }, "IV", pushIvText, { pushIvText = it })
                Field("Group", pushGroupText) { pushGroupText = it }
                Field("Copy", pushCopyText) { pushCopyText = it }
                TwoFields("isArchive 1/0", pushArchiveText, { pushArchiveText = it }, "TTL seconds", pushTtlText, { pushTtlText = it })
                CheckboxRow("Call", pushCallChecked) { pushCallChecked = it }
                CheckboxRow("Critical", pushCriticalChecked) { pushCriticalChecked = it }
                CheckboxRow("Auto Copy", pushAutoCopyChecked) { pushAutoCopyChecked = it }
                CheckboxRow("Delete", pushDeleteChecked) { pushDeleteChecked = it }
                PrimaryAction("Send Push") { sendCustomPush() }
            }
            SectionCard("Archive") {
                CheckboxRow("Archive notifications", archiveChecked) { archiveChecked = it }
            }
            SectionCard("Crypto") {
                Field("Algorithm", algorithmText) { algorithmText = it }
                TwoFields("Mode", modeText, { modeText = it }, "Padding", paddingText, { paddingText = it })
                Field("Key", cryptoKeyText) { cryptoKeyText = it }
                Field("IV", ivText) { ivText = it }
                ActionFlow {
                    PrimaryAction("Save Crypto") { saveSettings() }
                    SecondaryAction("Copy Example") { copyCryptoExample() }
                }
            }
            SectionCard("Info") {
                Text("Device Token", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(BarkDeviceTokenText.mask(settings.installToken), color = MaterialTheme.colorScheme.onSurfaceVariant)
                ActionFlow {
                    SecondaryAction("Copy Token") { copyDeviceToken() }
                    SecondaryAction("FAQ") { openExternalUrl(BARK_FAQ_URL) }
                    SecondaryAction("Documentation") { openExternalUrl(BARK_DOC_URL) }
                    SecondaryAction("Source Code") { openExternalUrl(BARK_SOURCE_URL) }
                    SecondaryAction("Privacy Policy") { openExternalUrl(BARK_PRIVACY_URL) }
                }
            }
            SoundsPanel(version)
        }
    }

    @Composable
    private fun SoundsPanel(version: Int) {
        SectionCard("Sounds") {
            PrimaryAction("Import Sound") { startSoundImport() }
            val customSounds = BarkCustomSoundStore(this@MainActivity).list()
            customSounds.forEach { sound ->
                SoundRow("Custom: ${sound.name}") {
                    SecondaryAction("Play") { playCustomSound(sound) }
                    SecondaryAction("Copy") { copySoundName(sound.name) }
                    SecondaryAction("Delete") { deleteCustomSound(sound) }
                }
            }
            BarkSoundCatalog.builtInSounds.forEach { sound ->
                SoundRow(sound.name) {
                    SecondaryAction("Play") { playSound(sound.name) }
                    SecondaryAction("Copy") { copySoundName(sound.name) }
                }
            }
        }
    }

    @Composable
    private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                content()
            }
        }
    }

    @Composable
    private fun InfoRow(label: String, value: String) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Text(label, modifier = Modifier.width(82.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun ActionFlow(content: @Composable FlowRowScope.() -> Unit) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }

    @Composable
    private fun PrimaryAction(text: String, onClick: () -> Unit) {
        Button(onClick = onClick, shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
            Text(text)
        }
    }

    @Composable
    private fun SecondaryAction(text: String, onClick: () -> Unit) {
        OutlinedButton(onClick = onClick, shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)) {
            Text(text)
        }
    }

    @Composable
    private fun Field(label: String, value: String, singleLine: Boolean = true, onChange: (String) -> Unit) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            singleLine = singleLine,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
        )
    }

    @Composable
    private fun TwoFields(
        firstLabel: String,
        firstValue: String,
        firstChange: (String) -> Unit,
        secondLabel: String,
        secondValue: String,
        secondChange: (String) -> Unit,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = firstValue,
                onValueChange = firstChange,
                label = { Text(firstLabel) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
            )
            OutlinedTextField(
                value = secondValue,
                onValueChange = secondChange,
                label = { Text(secondLabel) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
            )
        }
    }

    @Composable
    private fun CheckboxRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = onChange)
            Text(label)
        }
    }

    @Composable
    private fun SoundRow(label: String, actions: @Composable FlowRowScope.() -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            ActionFlow(actions)
        }
    }

    private fun showTab(tab: MainTab) {
        currentTab = tab
    }

    private fun loadSettings() {
        serverUrlText = settings.serverUrl
        deviceKeyText = settings.deviceKey.orEmpty()
        serverNameText = settings.serverName.orEmpty()
        archiveChecked = settings.archiveEnabled
        algorithmText = settings.cryptoAlgorithm
        modeText = settings.cryptoMode
        paddingText = settings.cryptoPadding
        cryptoKeyText = settings.cryptoKey.orEmpty()
        ivText = settings.cryptoIv.orEmpty()
        refreshExamples()
    }

    private fun saveSettings() {
        val currentId = settings.serverProfiles().currentId
        settings.serverUrl = serverUrlText
        settings.deviceKey = deviceKeyText.takeIf { it.isNotBlank() }
        settings.renameServer(currentId, serverNameText.takeIf { it.isNotBlank() })
        settings.archiveEnabled = archiveChecked
        settings.cryptoAlgorithm = algorithmText
        settings.cryptoMode = modeText
        settings.cryptoPadding = paddingText
        settings.cryptoKey = cryptoKeyText.takeIf { it.isNotBlank() }
        settings.cryptoIv = ivText.takeIf { it.isNotBlank() }
        status("Saved")
        refreshServers()
        refreshExamples()
    }

    private fun copyCryptoExample() {
        try {
            val script = BarkCryptoExampleScript.create(
                profile = settings.serverProfiles().current(BarkSettingsStore.DEFAULT_ANDROID_SERVER),
                settings = CryptoSettings(
                    algorithm = algorithmText,
                    mode = modeText,
                    padding = paddingText,
                    key = cryptoKeyText,
                    iv = ivText.takeIf { it.isNotBlank() },
                ),
            )
            copyText(script)
            settings.cryptoAlgorithm = algorithmText
            settings.cryptoMode = modeText
            settings.cryptoPadding = paddingText
            settings.cryptoKey = cryptoKeyText.takeIf { it.isNotBlank() }
            settings.cryptoIv = ivText.takeIf { it.isNotBlank() }
            status("Copied crypto example")
        } catch (error: IllegalArgumentException) {
            status(error.message ?: "Invalid crypto settings")
        }
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
                    deviceKeyText = result.deviceKey
                    refreshExamples()
                    startPollingService()
                    status("Registered and listening")
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
                currentKey?.let { deviceKeyText = it }
                refreshServers()
                refreshExamples()
                status(if (failures.isEmpty()) "Registered" else "Registered $registeredCount, failed ${failures.size}")
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
            address = serverUrlText,
            key = deviceKeyText.takeIf { it.isNotBlank() },
            name = serverNameText.takeIf { it.isNotBlank() },
        )
        loadSettings()
        refreshServers()
        status("Added ${profile.address}")
    }

    private fun importServerLink() {
        if (!importServerLink(listOf(serverUrlText, deviceKeyText, clipboardText()))) {
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
            historySearchInputText = ""
            selectedHistoryGroups = emptySet()
            uri.getQueryParameter("group")
                ?.let(BarkHistoryView::normalizeGroup)
                ?.let { group -> selectedHistoryGroups = setOf(group) }
            showTab(MainTab.HISTORY)
            refreshHistory()
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
        val link = candidates.firstNotNullOfOrNull { BarkServerLinkParser.parse(it) } ?: return false
        val profile = settings.addServer(address = link.address, key = link.key)
        loadSettings()
        refreshServers()
        status("Imported ${profile.address}")
        return true
    }

    private fun refreshServers() {
        refreshUi()
    }

    private fun refreshUi() {
        uiVersion += 1
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
            .setItems(serverActionLabels()) { _, index ->
                when (serverActionLabels()[index]) {
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

    private fun serverActionLabels(): Array<String> =
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
        val form = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
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
        refreshUi()
    }

    private fun copyPushExample(example: BarkPushExample) {
        copyText(example.url(settings.serverProfiles().current(BarkSettingsStore.DEFAULT_ANDROID_SERVER)))
        status("Copied example")
    }

    private fun openPushExample(example: BarkPushExample) {
        openExternalUrl(example.url(settings.serverProfiles().current(BarkSettingsStore.DEFAULT_ANDROID_SERVER)))
    }

    private fun refreshSounds() {
        refreshUi()
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
        val nameInput = edit("Name").apply { setText(profile?.name.orEmpty()) }
        AlertDialog.Builder(this)
            .setTitle("Rename")
            .setView(nameInput)
            .setPositiveButton("Save") { _, _ ->
                settings.renameServer(id, nameInput.text.toString().takeIf { it.isNotBlank() })
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
                    deviceKeyText = result.deviceKey
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
            title = pushTitleText,
            subtitle = pushSubtitleText,
            body = pushBodyText,
            id = pushIdText,
            markdown = pushMarkdownText,
            level = pushLevelText,
            isCall = pushCallChecked,
            isCritical = pushCriticalChecked,
            volume = pushVolumeText.toIntOrNull(),
            badge = pushBadgeText.toIntOrNull(),
            autoCopy = pushAutoCopyChecked,
            copy = pushCopyText,
            sound = pushSoundText,
            icon = pushIconText,
            image = pushImageText,
            group = pushGroupText,
            archive = pushArchiveText.toArchiveFlagOrNull(),
            ttlSeconds = pushTtlText.toLongOrNull(),
            url = pushUrlText,
            action = pushActionText,
            ciphertext = pushCiphertextText,
            iv = pushIvText,
            isDelete = pushDeleteChecked,
        )
        val targetKeys = BarkPushTargetKeys.parse(pushDeviceKeysText).ifEmpty { listOf(key) }
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
        try {
            val intent = Intent(this, BarkPollingService::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            refreshServers()
            status("Listening")
        } catch (error: Exception) {
            settings.listeningEnabled = false
            refreshServers()
            status(error.message ?: "Failed to start listening")
        }
    }

    private fun stopPollingService() {
        settings.listeningEnabled = false
        startService(Intent(this, BarkPollingService::class.java).setAction(BarkPollingService.ACTION_STOP))
        refreshServers()
        status("Stopped")
    }

    private fun refreshHistory() {
        val messages = store.recent(searchText = historySearchText)
        val groups = BarkHistoryView.groups(messages)
        if (selectedHistoryGroups.isNotEmpty() && groups.none { it.group in selectedHistoryGroups }) {
            selectedHistoryGroups = emptySet()
        }
        refreshUi()
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

    private fun historyFilterLabel(group: BarkHistoryGroup): String =
        buildString {
            if (selectedHistoryGroups.size == 1 && selectedHistoryGroups.contains(group.group)) append("* ")
            append(group.displayName)
            append(" (").append(group.totalCount).append(")")
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
        statusMessage = value
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

    private fun edit(hint: String): EditText =
        EditText(this).apply {
            this.hint = hint
            setSingleLine(true)
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
