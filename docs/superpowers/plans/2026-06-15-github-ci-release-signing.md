# GitHub CI Release Signing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build GitHub Actions CI and release signing automation for Bark Android.

**Architecture:** GitHub Actions is split into no-secret CI and secret-backed release jobs. Gradle release signing reads environment variables only, so the repository never stores signing material.

**Tech Stack:** GitHub Actions, Gradle, Android Gradle Plugin, Kotlin/Java tests.

---

### Task 1: Add CI Configuration Guard Test

**Files:**
- Create: `app/src/test/java/day/bark/android/GitHubActionsReleaseWiringTest.java`

- [x] **Step 1: Write the failing test**

Create `app/src/test/java/day/bark/android/GitHubActionsReleaseWiringTest.java` with assertions for Gradle env signing, CI workflow debug artifact, release workflow signed APK/AAB artifacts, and README secrets documentation.

- [x] **Step 2: Run the focused test**

Run:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  ./gradlew --console=plain --rerun-tasks :app:testDebugUnitTest \
  --tests day.bark.android.GitHubActionsReleaseWiringTest
```

Expected: fail because workflows and Gradle signing config do not exist yet.

### Task 2: Implement Release Signing and Workflows

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `.github/workflows/ci.yml`
- Create: `.github/workflows/release.yml`
- Modify: `README.md`

- [x] **Step 1: Add env-backed release signing to Gradle**

Add a release signing config that reads `BARK_ANDROID_KEYSTORE_*` environment variables and attaches it to the `release` build type only when the variables are complete.

- [x] **Step 2: Add CI workflow**

Create `.github/workflows/ci.yml` to run tests and assemble debug APK on push/PR, then upload `app/build/outputs/apk/debug/*.apk`.

- [x] **Step 3: Add release workflow**

Create `.github/workflows/release.yml` to run manually or for version tags, decode `BARK_ANDROID_KEYSTORE_BASE64`, run tests, build release APK/AAB with `-x lintVitalAnalyzeRelease`, and upload both artifacts.

- [x] **Step 4: Document signing choices**

Update `README.md` with secret names, keystore generation/export commands, and store guidance for Google Play AAB versus broad signed APK distribution.

### Task 3: Verify and Publish

**Files:**
- All files above

- [x] **Step 1: Run focused test**

Run the focused wiring test again. Expected: pass.

- [x] **Step 2: Run full Android verification**

Run:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  ./gradlew --console=plain --rerun-tasks :core:test :app:testDebugUnitTest :app:assembleDebug
```

Expected: build success.

- [x] **Step 3: Commit and push**

Commit the CI/signing configuration and push `main` to `origin`.
