package day.bark.android

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object AesCipher {
    fun encrypt(plainText: String, settings: CryptoSettings): String {
        validateKey(settings)
        val cipher = Cipher.getInstance(transformation(settings))
        val keySpec = SecretKeySpec(settings.key.toByteArray(Charsets.UTF_8), "AES")
        initCipher(cipher, Cipher.ENCRYPT_MODE, keySpec, settings)
        return Base64.getEncoder().encodeToString(cipher.doFinal(plainText.toByteArray(Charsets.UTF_8)))
    }

    fun decrypt(ciphertext: String, settings: CryptoSettings): String {
        validateKey(settings)
        val cipher = Cipher.getInstance(transformation(settings))
        val keySpec = SecretKeySpec(settings.key.toByteArray(Charsets.UTF_8), "AES")
        initCipher(cipher, Cipher.DECRYPT_MODE, keySpec, settings)
        val decoded = Base64.getMimeDecoder().decode(ciphertext)
        return String(cipher.doFinal(decoded), Charsets.UTF_8)
    }

    private fun initCipher(
        cipher: Cipher,
        operationMode: Int,
        keySpec: SecretKeySpec,
        settings: CryptoSettings,
    ) {
        when (settings.mode.uppercase()) {
            "CBC" -> cipher.init(operationMode, keySpec, IvParameterSpec(requiredIv(settings, 16)))
            "ECB" -> cipher.init(operationMode, keySpec)
            "GCM" -> cipher.init(operationMode, keySpec, GCMParameterSpec(128, requiredIv(settings, 12)))
            else -> throw IllegalArgumentException("Invalid Mode")
        }
    }

    private fun transformation(settings: CryptoSettings): String {
        val mode = when (settings.mode.uppercase()) {
            "CBC", "ECB", "GCM" -> settings.mode.uppercase()
            else -> throw IllegalArgumentException("Invalid Mode")
        }
        val padding = when (settings.padding) {
            "pkcs7" -> if (mode == "GCM") "NoPadding" else "PKCS5Padding"
            "noPadding" -> "NoPadding"
            else -> throw IllegalArgumentException("Invalid Padding")
        }
        return "AES/$mode/$padding"
    }

    private fun validateKey(settings: CryptoSettings) {
        val expectedLength = when (settings.algorithm.uppercase()) {
            "AES128" -> 16
            "AES192" -> 24
            "AES256" -> 32
            else -> throw IllegalArgumentException("Invalid algorithm")
        }
        require(settings.key.length == expectedLength) {
            "Key length must be $expectedLength"
        }
    }

    private fun requiredIv(settings: CryptoSettings, expectedLength: Int): ByteArray {
        val iv = settings.iv ?: throw IllegalArgumentException("IV is missing")
        require(iv.length == expectedLength) {
            "IV length must be $expectedLength"
        }
        return iv.toByteArray(Charsets.UTF_8)
    }
}
