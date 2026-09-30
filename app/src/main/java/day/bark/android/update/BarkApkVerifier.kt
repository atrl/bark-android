@file:Suppress("DEPRECATION")

package day.bark.android.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import day.bark.android.BarkApkIdentity
import day.bark.android.BarkAppRelease
import day.bark.android.BarkUpdatePolicy
import day.bark.android.toHex
import java.io.File
import java.security.MessageDigest

class BarkApkVerifier(private val context: Context) {
    private val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

    fun installed(): BarkApkIdentity = identity(context.packageManager.getPackageInfo(context.packageName, flags))

    fun verify(release: BarkAppRelease, file: File) {
        file.inputStream().use { BarkUpdatePolicy.verifyIntegrity(it, release.sizeBytes, release.sha256) }
        val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: error("Downloaded file is not a readable APK")
        BarkUpdatePolicy.verifyIdentity(release, installed(), identity(archive), Build.VERSION.SDK_INT)
    }

    private fun identity(info: PackageInfo): BarkApkIdentity {
        // Only the currently active signer set is trusted; past signing lineage
        // is not sufficient to authorize a replacement package.
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        return BarkApkIdentity(
            info.packageName,
            if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong(),
            info.versionName.orEmpty(),
            info.applicationInfo?.minSdkVersion ?: error("APK has no application metadata"),
            signatures.orEmpty().map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).toHex() }.toSet(),
        )
    }
}
