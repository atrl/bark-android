# Bark Android

Kotlin Android implementation of Bark with compatibility for the Bark push
parameter surface and the Android delivery extension used by the paired
`bark-server` build.

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
