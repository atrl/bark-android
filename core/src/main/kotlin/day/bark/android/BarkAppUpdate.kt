package day.bark.android

import java.io.InputStream
import java.net.URI
import java.security.MessageDigest
import java.time.Instant
import org.json.JSONObject

/** The update authority is independent of configurable notification servers. */
object BarkUpdatePolicy {
    const val MANIFEST_URL = "https://bark.atrl.me/android/releases/stable.json"
    const val PACKAGE_NAME = "day.bark.android"
    const val RELEASE_SIGNER = "60408b509647dc7689bca6435a3eef93d475d868232fe88d848e853c03ffe896"
    const val MAX_APK_BYTES = 256L * 1024 * 1024
    const val MAX_MANIFEST_BYTES = 64 * 1024

    fun validateApkUrl(value: String): URI {
        val uri = URI(value)
        require(uri.scheme == "https" && uri.host == "bark.atrl.me" && uri.port in setOf(-1, 443)) { "Untrusted APK origin" }
        require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) { "Unexpected APK URL credentials or parameters" }
        require(Regex("/android/releases/bark-android-[A-Za-z0-9][A-Za-z0-9._+-]*\\.apk").matches(uri.rawPath.orEmpty())) { "Invalid APK path" }
        require(".." !in uri.rawPath) { "Invalid APK path" }
        return uri
    }

    fun verifyIntegrity(input: InputStream, expectedSize: Long, expectedSha256: String) {
        require(expectedSize in 1..MAX_APK_BYTES) { "Invalid APK size" }
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= expectedSize) { "APK exceeds published size" }
            digest.update(buffer, 0, count)
        }
        require(total == expectedSize) { "APK size does not match release" }
        require(digest.digest().toHex() == expectedSha256.lowercase()) { "APK checksum does not match release" }
    }

    fun verifyIdentity(release: BarkAppRelease, installed: BarkApkIdentity, candidate: BarkApkIdentity, sdk: Int) {
        require(installed.packageName == PACKAGE_NAME && candidate.packageName == PACKAGE_NAME) { "APK belongs to another application" }
        require(candidate.versionCode == release.versionCode && candidate.versionName == release.versionName) { "APK version does not match release" }
        require(candidate.versionCode > installed.versionCode) { "Update is not newer than the installed version" }
        require(candidate.minSdk == release.minSdk && sdk >= candidate.minSdk) { "APK is not compatible with this Android version" }
        require(installed.signers == setOf(RELEASE_SIGNER)) { "This installation uses a different signing key" }
        require(candidate.signers == installed.signers && candidate.signers == setOf(release.signingCertificateSha256)) { "APK signer does not match the installed app" }
    }
}

data class BarkApkIdentity(
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val minSdk: Int,
    val signers: Set<String>,
)

data class BarkAppRelease(
    val versionCode: Long,
    val versionName: String,
    val minSdk: Int,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val signingCertificateSha256: String,
    val releaseNotes: String,
    val publishedAt: String,
) {
    val fileName: String get() = "update-$versionCode-${sha256.take(16)}.apk"
    fun isNewerThan(version: Long, sdk: Int): Boolean = versionCode > version && minSdk <= sdk

    fun toJson(): String = JSONObject().put("schema_version", 1).put("package_name", BarkUpdatePolicy.PACKAGE_NAME)
        .put("version_code", versionCode).put("version_name", versionName).put("min_sdk", minSdk)
        .put("apk_url", apkUrl).put("sha256", sha256).put("size_bytes", sizeBytes)
        .put("signing_certificate_sha256", signingCertificateSha256).put("release_notes", releaseNotes)
        .put("published_at", publishedAt).toString()

    companion object {
        fun parse(text: String): BarkAppRelease {
            require(text.toByteArray(Charsets.UTF_8).size <= BarkUpdatePolicy.MAX_MANIFEST_BYTES) { "Release manifest is too large" }
            val json = JSONObject(text)
            fun string(name: String): String = (json.opt(name) as? String) ?: error("Missing release field: $name")
            fun positiveLong(name: String): Long {
                val raw = json.opt(name)
                require(raw is Number && Regex("[0-9]+").matches(raw.toString())) { "Invalid release field: $name" }
                return raw.toString().toLong().also { require(it > 0) { "Invalid release field: $name" } }
            }
            require(positiveLong("schema_version") == 1L) { "Unsupported release manifest" }
            require(string("package_name") == BarkUpdatePolicy.PACKAGE_NAME) { "Release belongs to another application" }
            val minSdk = positiveLong("min_sdk").also { require(it <= Int.MAX_VALUE) }.toInt()
            val sha = string("sha256").lowercase()
            require(Regex("[0-9a-f]{64}").matches(sha)) { "Invalid release checksum" }
            val signer = string("signing_certificate_sha256").lowercase()
            require(signer == BarkUpdatePolicy.RELEASE_SIGNER) { "Untrusted release signing key" }
            val url = string("apk_url").also(BarkUpdatePolicy::validateApkUrl)
            val size = positiveLong("size_bytes").also { require(it <= BarkUpdatePolicy.MAX_APK_BYTES) { "Release APK is too large" } }
            val versionName = string("version_name").also { require(it.isNotBlank() && it.length <= 80) }
            val notes = string("release_notes").also { require(it.toByteArray(Charsets.UTF_8).size <= 4096) { "Release notes are too long" } }
            val published = string("published_at").also { Instant.parse(it) }
            return BarkAppRelease(positiveLong("version_code"), versionName, minSdk, url, sha, size, signer, notes, published)
        }
    }
}

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

enum class BarkUpdateRecovery { INSTALLED, RETRY_STAGING, CONTINUE_CONFIRMATION, WAIT_SYSTEM }

fun recoverBarkUpdate(installedVersion: Long, expectedVersion: Long, sessionExists: Boolean, sealed: Boolean, active: Boolean): BarkUpdateRecovery = when {
    installedVersion >= expectedVersion -> BarkUpdateRecovery.INSTALLED
    !sessionExists || !sealed -> BarkUpdateRecovery.RETRY_STAGING
    !active -> BarkUpdateRecovery.CONTINUE_CONFIRMATION
    else -> BarkUpdateRecovery.WAIT_SYSTEM
}
