# Bark Android

Kotlin Android implementation of Bark with compatibility for the Bark push
parameter surface and the Android delivery extension used by the paired
`bark-server` build.

## Project home and desktop widgets

Version 0.4.2 opens on **项目 (Projects)** with a native financial dashboard,
alongside **消息 / 推送 / 设置**. The initial **IC 基差** card shows annualized
discount, historical percentile and the selected real contract's recent daily
closes. Choose IC, IM or IF and an explicit maturity; absent maturities stay
missing. The full dashboard opens at `https://basis.atrl.me/`. Add, edit, reorder
or remove other project cards through their menu, using a name, HTTPS address
and optional description/message group.
Project settings stay on the device and do not contain Bark receiving credentials.

Project pages open inside a dedicated WebView with back navigation, refresh,
loading/error feedback and an option to open the system browser. The current
project's HTTPS origin stays inside the app; links to other origins open in the
browser. JavaScript and DOM storage support the existing IC dashboard. The app
does not inject a native JavaScript bridge or bypass certificate errors. Website
login remains the responsibility of the project website. The last successfully
opened WebView can be reused for up to five minutes; it is detached from its
Activity and released under memory pressure. Static website resources use the
site's verified immutable cache. Refresh still revalidates the page and data;
there is no unrestricted request interception or forced stale network cache.

To add a desktop entry, long-press the Android home screen, choose **Widgets →
Bark 项目**, and select a project, family and preferred maturity. At 220 × 160 dp
and above, basis widgets show all four maturities in a 2 × 2 grid, each with its
own annualized discount and same-contract daily-close curve. Smaller widgets
retain two maturities (front plus the preferred maturity, or next if front is
preferred), show **2/4**, and keep both curves. Larger widgets add percentile and
per-contract status. Missing maturities stay missing; the footer uses the oldest
visible source time and marks mixed stale/missing data. The refresh icon requests an update and the settings icon reconfigures
the widget. Tapping the card opens the full website. Other web projects retain
their shortcut card. Removing a selected project shows a
reconfiguration prompt instead of redirecting the widget to another project.
The existing recent-message/group widget remains available separately.

Native cards and widgets share the bounded `/api/mobile-summary` data cache.
Verified snapshots are shown immediately and revalidated in the background using
ETags; simultaneous requests are coalesced, ordinary refreshes are limited to one
per minute and manual taps to one per 15 seconds. One private disk record per
family survives process restarts, with a 128 KiB response limit and 30-day disk
retention. A failed or empty refresh does not erase a usable snapshot. A 304
updates only the check time, never the quote time. The API and native views use
the same `annualized_carry_pct` formula and percentile metric; graphs preserve
each real contract and gaps and are labeled as daily closes.

Widgets use a 15-minute WorkManager schedule while data widgets exist; Android
can delay background work. Transient connection failures and HTTP 408/425/429/5xx
responses retry at most three times, with a minimum one-minute exponential
backoff (nominally 1, 2 and 4 minutes), before waiting for the next periodic or
manual refresh. This delay clears the shared cache's one-minute cooldown.
Permanent HTTP and data-validation failures do not automatically retry; valid
responses with stale source data do not trigger retries either. Existing periodic
work adopts the retry policy when the app starts after an update.
Widgets label live-source values as sampled snapshots and always display their
absolute source time after **截至**; they do not promise continuously live prices.
An initial connection failure displays **连接失败**, while a connection failure
with saved data displays **离线缓存** and keeps the original quote time. Manual
refresh remains available. The foreground card revalidates every minute and updates
its age label without a network request. Expired live quotes, old closes,
insufficient samples and offline cache remain explicitly distinguishable.

IC notifications may include the project website as their Bark `url`. This release
keeps existing notification handling: background FCM taps first open message
history, where the message URL can be opened. Project and widget navigation are
independent of notification delivery.

## Compatibility

- Supports the official Bark push parameters used by upstream Bark examples:
  `title`, `subtitle`, `body`, `id`, `markdown`, `sound`, `level`, `volume`,
  `badge`, `call`, `autoCopy`, `copy`, `icon`, `image`, `group`, `isArchive`,
  `ttl`, `url`, `action`, `ciphertext`, `iv`, and `delete`.
- Supports Bark server profiles, imported push URLs, batch
  target keys, custom sounds, notification groups, history/archive, widgets,
  QR import, Android share targets, and shortcut/broadcast push intents.
- Android receiving uses the paired server's authenticated transport, sync and
  acknowledgement extensions. Ordinary notifications use FCM system delivery;
  servers without FCM remain usable through an explicit foreground polling service.
- New installations default to `https://bark.atrl.me`. Existing saved servers and
  device keys are preserved. The official `api.day.app` has no Android extension.

The official Bark parameter format is preserved for outbound push requests and
examples. APNs cannot deliver to Android devices.

## Build

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  ./gradlew --console=plain :core:test :app:testDebugUnitTest :app:assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Firebase configuration

Register Android package `day.bark.android` in your Firebase project and download
its `google-services.json`. Google Play publication is **not** required; a signed,
sideloaded APK works. The phone needs working Google Play services and connectivity
to FCM, and must grant Android notification permission.

Keep the app config outside Git and point the build at it:

```bash
BARK_FIREBASE_CONFIG="$HOME/.config/bark/firebase/google-services.json" \
  ./gradlew --console=plain :core:test :app:testDebugUnitTest :app:assembleDebug
```

An ignored `app/google-services.json` is also supported. Gradle reads the matching
Android client and generates the standard Firebase resources without copying the
config into the repository. Missing configuration is a supported polling build;
a supplied invalid path or mismatched package fails the build. Server service-account
credentials must never be placed in the APK. Firebase Messaging `25.1.3` is pinned;
this build uses its supported registration-token compatibility API. The newer FID
`register()` API requires a coordinated server migration and is not enabled here.

On the Service screen, Register/Start Listening configures delivery. `FCM registered`
means both the phone and Bark server accepted the transport registration. It does
not mean a test message has been displayed. Once every saved server uses FCM,
the permanent polling service stops. Missing Firebase/Play services or a server
returning 503 uses polling; opening the app restores interrupted polling. Connection,
registration and polling status are shown separately from the enabled preference.

## Delivery and recovery

- `POST /android/transport/:device_key` binds the FCM token or selects polling.
- `GET /android/sync/:device_key?timeout=0&limit=50` reads durable deliveries.
- `POST /android/ack/:device_key` acknowledges processed delivery IDs.
- These routes send the installation token in `X-Bark-Device-Token`; redirects are
  disabled so the credential cannot be forwarded to another host. The returned
  canonical server URL maps FCM hints back to saved LAN/alias profiles.
- Each message is persisted in a separate SQLite receive ledger before processing
  and ACK. Retries deduplicate by delivery ID, not Bark's editable `id`, so updates
  and deletion commands still work. Disabling or clearing visible history does not
  remove pending deliveries. After a successful server ACK, the recovery payload
  is erased and only the deduplication receipt remains (at most seven days / 5000
  completed receipts). Pending receipts and their payloads are not evicted.
- Normal background notifications carry title/body for Google Play services to
  display. Opening a notification or reopening the app syncs history. FCM acceptance
  is not display proof: recovery quietly replaces the stable notification tag when
  display is unknown. Already-clicked delivery IDs do not reappear. If the user
  dismissed an OS notification before the app synced, a quiet recovered notification
  can reappear. The receive ledger prevents further replay after processing.
- Foreground/data callbacks persist their requested delivery ID before scheduling
  WorkManager; high-priority callbacks use expedited work and sync the target server
  first. Periodic recovery is best-effort (minimum 15 minutes), not a realtime SLA.
- Active group mute or a configured encryption key registers local `data` handling.
  Saving those settings updates the server asynchronously. Until confirmation,
  an already-in-flight system notification may still display. The paired server
  also uses data messages for payload features that require local handling (custom
  sound, call, copy/actions, TTL and deletion). They depend on Android permitting
  the app's callback/worker to execute. Ordinary system notifications have fewer
  local action/customization features. Encrypted fallback previews never send
  decrypted text through the server or FCM.
- Stop Listening stops polling immediately and queues server deregistration and
  token deletion. Offline devices show confirmation pending because OS notifications
  may continue until remote deregistration succeeds. Reset/remove only discard a
  saved server after its authenticated unregister succeeds.

Older Android-enabled servers are supported only when the sync endpoint returns
404, using their legacy destructive poll route. Authentication errors never fall
back to that route. Legacy polling cannot promise the new ACK guarantees.

Android's **Force stop**, disabled notification permission, unavailable Google
services, offline FCM, and OEM restrictions remain real delivery limits. A process
kill/reboot test on the target phone is required before calling delivery verified.
Build/tests alone do not demonstrate handset receipt.

## Automatic APK updates (0.2.2 and later)

The project home and project widgets are available on the stable update channel
starting with **0.3.1 (version code 7)**. This also updates the local 0.3.0
validation build (version code 6). An update must have a strictly greater version
code than the installed app.

Pushing to `main` runs debug CI only. To publish an app update, commit the new
version and release notes, push the matching `v<version_name>` tag, wait for
**Android Release** to publish its verified GitHub release bundle, and verify
that the NAS `stable.json` reports that version and exact APK hash. A successful
debug build or a local signed APK does not update the stable channel.

Open **Settings → App updates → Manage updates** to check, download, install, or
turn automatic updates off. The app checks at most once per six hours on foreground
entry and also schedules a daily WorkManager check. Background execution and network
availability can delay these checks.

Automatic updates are on by default and use **unmetered Wi-Fi** for downloads and
automatic installation attempts. A manual download may use any connected network.
Android must first allow Bark to install app updates under its "Install unknown
apps" setting. The app opens that settings screen only from a user action. When the
permission is missing, the verified APK stays ready and a notification links to
Updates. Turning automatic updates off cancels automatic downloads; manual checks,
downloads and installation remain available.

On Android 12 and later, Bark requests self-update without additional interaction
when Android permits it. OEM policy, update ownership, developer verification, and
other system decisions can still require confirmation. The app handles this through
a notification or the visible Updates screen; it never opens an installer from the
background. Older Android versions use the system confirmation flow. Disabling
notification permission can hide the reminder; the pending action remains available
from Updates.

The update source is fixed to
`https://bark.atrl.me/android/releases/stable.json`, independently of saved push
servers. Downloads reject redirects and every other origin. Before downloading or
installing, the updater enforces the package identity and supported Android version.
Before installation it verifies the exact byte count and SHA-256, reads actual APK
metadata with PackageManager, and requires a strictly newer version and the same
current signing certificate as both this installed app and the pinned release key.
The system PackageInstaller also verifies the APK signature during installation.
Debug builds and other signing identities cannot install production updates.

Downloads are written privately to a temporary file, verified, then renamed. A
cancelled/interrupted download is removed and may be retried. Completed downloads
remain available offline. Install session ID, release snapshot, nonce and phase are
persisted: interrupted uncommitted sessions are discarded and retried, while a
pending Android confirmation can be recovered by reopening Updates and continuing
the existing session. A newer server manifest cannot replace a pending session's
release snapshot. The app checks the currently installed version again before commit
to prevent racing another installer or downgrading.

**One-time migration:** version 0.2.0 has no updater. Install signed 0.2.2 manually
once; subsequent published releases can follow the update flow above. Installation
on a real phone still needs to be verified separately from compilation/unit tests.

The release workflow checks out a fixed commit of the server repository's
`prepare-android-release.py`, which verifies the signed APK and produces one
immutable bundle: `stable.json`, the versioned APK, and its `.apk.json` sidecar.
The tag must be `v<version_name>`. These exact CI bytes are uploaded to GitHub Releases
and mirrored to the NAS; do not publish a different local build under the same
version code/filename. `RELEASE_NOTES.txt` supplies the notes shown inside the app.

Android API behavior is documented in the official
[PackageInstaller SessionParams reference](https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams#setRequireUserAction(int))
and [Session reference](https://developer.android.com/reference/android/content/pm/PackageInstaller.Session).

## GitHub CI

`Android CI` runs on every push and pull request to `main`. It runs the unit
tests, builds the debug APK, and uploads `bark-android-debug-apk` as a workflow
artifact. This workflow does not use signing secrets and exercises a polling-only build.

`Android Release` runs from a manual workflow dispatch or a `v*` tag. It builds
signed release artifacts:

- `bark-android-release-aab`: use this AAB for Google Play. With Play App
  Signing enabled, the CI keystore is the upload key.
- `bark-android-release-apk`: use this signed APK for stores or distribution
  channels that do not accept AAB.

## Release Signing

Create a release/upload key locally:

```bash
keytool -genkeypair \
  -v \
  -keystore bark-release.keystore \
  -alias bark \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

Export the keystore for GitHub Secrets:

```bash
base64 -i bark-release.keystore | pbcopy
```

Set these repository secrets in GitHub:

```text
BARK_ANDROID_KEYSTORE_BASE64
BARK_ANDROID_KEYSTORE_PASSWORD
BARK_ANDROID_KEY_ALIAS
BARK_ANDROID_KEY_PASSWORD
BARK_FIREBASE_CONFIG_JSON
```

`BARK_FIREBASE_CONFIG_JSON` is the app's complete `google-services.json` content,
written only to the runner's temporary directory. It is optional for polling-only
releases; configure it to distribute an FCM-enabled release.

For local signed builds, point Gradle at the same keystore through environment
variables:

```bash
export BARK_ANDROID_KEYSTORE_PATH="$PWD/bark-release.keystore"
export BARK_ANDROID_KEYSTORE_PASSWORD="..."
export BARK_ANDROID_KEY_ALIAS="bark"
export BARK_ANDROID_KEY_PASSWORD="..."
./gradlew --console=plain :app:assembleRelease :app:bundleRelease -x lintVitalAnalyzeRelease
```

The release workflow skips `lintVitalAnalyzeRelease` during packaging because
tests run separately and lint-only dependency downloads can be affected by
Google Maven connectivity. Run lint separately when that dependency path is
stable.

Google Play expects app identity continuity through app signing. For a new Play
app, prefer Play App Signing and upload the signed AAB with an upload key. For
multi-store distribution, keep the app signing key under your control and reuse
the same signing identity across APK/AAB outputs for every store that needs to
accept updates to the same package name.
