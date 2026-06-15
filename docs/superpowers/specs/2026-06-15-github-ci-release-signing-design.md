# GitHub CI Release Signing Design

## Goal

Add GitHub Actions automation that builds Bark Android on every push and pull
request, publishes debug APK artifacts for inspection, and supports manually
triggered signed release APK/AAB artifacts for app store distribution.

## Approach

The repository will use two workflows. `ci.yml` runs unit tests and creates a
debug APK artifact without reading signing secrets. `release.yml` runs the same
checks, decodes a base64 keystore from GitHub Secrets, and builds `bundleRelease`
plus `assembleRelease`.

Gradle release signing will be environment-driven. The release build type will
only attach a signing config when all required variables are present:
`BARK_ANDROID_KEYSTORE_PATH`, `BARK_ANDROID_KEYSTORE_PASSWORD`,
`BARK_ANDROID_KEY_ALIAS`, and `BARK_ANDROID_KEY_PASSWORD`. This keeps private
keys out of the repo while letting CI and local signed builds use the same
secret names.

The release artifact workflow skips `lintVitalAnalyzeRelease` during APK/AAB
packaging. Tests and debug CI still run, while release packaging is not blocked
by transient Google Maven downloads for lint-only dependencies.

## Store Signing Guidance

Google Play distribution should use the signed AAB. For Play App Signing, the
key in CI is the upload key. Non-Play Android stores commonly accept signed APKs
or AABs, but APK remains the broad compatibility artifact. If one app identity
must be distributed through multiple stores, the same app signing key should be
kept under owner control and injected into CI as secrets.

## Verification

Configuration will be covered by a Java unit test that reads:

- `app/build.gradle.kts`
- `.github/workflows/ci.yml`
- `.github/workflows/release.yml`
- `README.md`

The final verification command is:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  ./gradlew --console=plain --rerun-tasks :core:test :app:testDebugUnitTest :app:assembleDebug
```
