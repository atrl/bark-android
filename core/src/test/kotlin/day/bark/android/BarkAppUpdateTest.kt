package day.bark.android

import java.security.MessageDigest
import org.json.JSONObject
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BarkAppUpdateTest {
    private fun release() = BarkAppRelease(5, "0.2.2", 26,
        "https://bark.atrl.me/android/releases/bark-android-0.2.2.apk", "a".repeat(64), 100,
        BarkUpdatePolicy.RELEASE_SIGNER, "Update notes", "2026-09-30T05:00:00Z")
    private fun installed() = BarkApkIdentity(BarkUpdatePolicy.PACKAGE_NAME, 4, "0.2.1", 26, setOf(BarkUpdatePolicy.RELEASE_SIGNER))
    private fun candidate() = installed().copy(versionCode = 5, versionName = "0.2.2")

    @Test fun validManifestRoundTripsAndSelectsOnlyCompatibleNewerVersions() {
        val value = release()
        assertEquals(value, BarkAppRelease.parse(value.toJson()))
        assertTrue(value.isNewerThan(4, 26))
        assertFalse(value.isNewerThan(5, 36))
        assertFalse(value.isNewerThan(6, 36))
        assertFalse(value.isNewerThan(4, 25))
    }

    @Test fun rejectUntrustedUrlSchemesHostsPathsCredentialsAndRedirectTargets() {
        listOf(
            "http://bark.atrl.me/android/releases/bark-android-0.2.2.apk",
            "https://bark.atrl.me.evil.test/android/releases/bark-android-0.2.2.apk",
            "https://user@bark.atrl.me/android/releases/bark-android-0.2.2.apk",
            "https://bark.atrl.me:8443/android/releases/bark-android-0.2.2.apk",
            "https://bark.atrl.me/android/releases/../bark-android-0.2.2.apk",
            "https://bark.atrl.me/android/releases/bark-android-%2e%2e.apk",
            "https://bark.atrl.me/android/releases/bark-android-0.2.2.apk?redirect=evil",
            "https://bark.atrl.me/android/releases/bark-android-0.2.2.apk#fragment",
            "https://bark.atrl.me/other/bark-android-0.2.2.apk",
        ).forEach { url -> assertFailsWith<IllegalArgumentException>(url) { BarkUpdatePolicy.validateApkUrl(url) } }
        BarkUpdatePolicy.validateApkUrl("https://bark.atrl.me/android/releases/bark-android-0.2.2+build.1.apk")
    }

    @Test fun rejectMalformedOrUntrustedManifestMetadata() {
        listOf("schema_version" to 2, "package_name" to "another.app", "version_code" to 4.5,
            "version_code" to -1, "version_code" to "5", "min_sdk" to 0, "sha256" to "bad",
            "size_bytes" to 0, "size_bytes" to BarkUpdatePolicy.MAX_APK_BYTES + 1,
            "signing_certificate_sha256" to "b".repeat(64), "published_at" to "yesterday").forEach { (key, value) ->
            val json = JSONObject(release().toJson()).put(key, value).toString()
            assertFailsWith<Exception>(key) { BarkAppRelease.parse(json) }
        }
    }

    @Test fun integrityChecksRejectTruncationExtraBytesAndTampering() {
        val bytes = "release APK bytes".toByteArray()
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
        BarkUpdatePolicy.verifyIntegrity(bytes.inputStream(), bytes.size.toLong(), sha)
        assertFailsWith<IllegalArgumentException> { BarkUpdatePolicy.verifyIntegrity(bytes.dropLast(1).toByteArray().inputStream(), bytes.size.toLong(), sha) }
        assertFailsWith<IllegalArgumentException> { BarkUpdatePolicy.verifyIntegrity((bytes + 1).inputStream(), bytes.size.toLong(), sha) }
        assertFailsWith<IllegalArgumentException> { BarkUpdatePolicy.verifyIntegrity(bytes.inputStream(), bytes.size.toLong(), "b".repeat(64)) }
    }

    @Test fun actualApkMustMatchPublishedIdentityAndCurrentSignerExactly() {
        BarkUpdatePolicy.verifyIdentity(release(), installed(), candidate(), 36)
        listOf(candidate().copy(packageName = "malicious.app"), candidate().copy(versionCode = 6),
            candidate().copy(versionName = "different"), candidate().copy(minSdk = 25),
            candidate().copy(signers = setOf("b".repeat(64))),
            candidate().copy(signers = setOf(BarkUpdatePolicy.RELEASE_SIGNER, "b".repeat(64)))).forEach { apk ->
            assertFailsWith<IllegalArgumentException> { BarkUpdatePolicy.verifyIdentity(release(), installed(), apk, 36) }
        }
        assertFailsWith<IllegalArgumentException> { BarkUpdatePolicy.verifyIdentity(release(), installed().copy(signers = setOf("b".repeat(64))), candidate(), 36) }
    }

    @Test fun concurrentUpgradeOrDowngradePreventsCommitEvenForCorrectlySignedApk() {
        assertFailsWith<IllegalArgumentException> { BarkUpdatePolicy.verifyIdentity(release(), installed().copy(versionCode = 5), candidate(), 36) }
        assertFailsWith<IllegalArgumentException> { BarkUpdatePolicy.verifyIdentity(release(), installed().copy(versionCode = 6), candidate(), 36) }
        assertFailsWith<IllegalArgumentException> { BarkUpdatePolicy.verifyIdentity(release(), installed(), candidate(), 25) }
    }

    @Test fun interruptedStagingAndCommitWindowRemainRetryable() {
        assertEquals(BarkUpdateRecovery.RETRY_STAGING, recoverBarkUpdate(4, 5, false, false, false))
        assertEquals(BarkUpdateRecovery.RETRY_STAGING, recoverBarkUpdate(4, 5, true, false, false))
        assertEquals(BarkUpdateRecovery.CONTINUE_CONFIRMATION, recoverBarkUpdate(4, 5, true, true, false))
        assertEquals(BarkUpdateRecovery.WAIT_SYSTEM, recoverBarkUpdate(4, 5, true, true, true))
        assertEquals(BarkUpdateRecovery.INSTALLED, recoverBarkUpdate(5, 5, false, false, false))
        assertEquals(BarkUpdateRecovery.INSTALLED, recoverBarkUpdate(6, 5, true, true, false))
    }
}
