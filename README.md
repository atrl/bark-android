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
