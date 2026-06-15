# Bark Android

Kotlin Android implementation of Bark with compatibility for the Bark push
parameter surface and the Android delivery extension used by the paired
`bark-server` build.

## Compatibility

- Supports the official Bark push parameters used by upstream Bark examples:
  `title`, `subtitle`, `body`, `id`, `markdown`, `sound`, `level`, `volume`,
  `badge`, `call`, `autoCopy`, `copy`, `icon`, `image`, `group`, `isArchive`,
  `ttl`, `url`, `action`, `ciphertext`, `iv`, and `delete`.
- Supports Bark server profiles, imported push URLs, Basic Auth URLs, batch
  target keys, custom sounds, notification groups, history/archive, widgets,
  QR import, Android share targets, and shortcut/broadcast push intents.
- Receiving push notifications on Android requires a Bark server that includes
  the Android delivery/polling extension (`/android/poll/:device_key`) and
  registers Android tokens with the `android:` prefix.

The official `api.day.app` parameter format is preserved for outbound push
requests and examples. A live Android receive flow depends on the server side
deploying the Android polling route because APNs cannot deliver to Android
devices.

## Build

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  ./gradlew --console=plain :core:test :app:testDebugUnitTest :app:assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## GitHub CI

`Android CI` runs on every push and pull request to `main`. It runs the unit
tests, builds the debug APK, and uploads `bark-android-debug-apk` as a workflow
artifact. This workflow does not use signing secrets.

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
```

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
