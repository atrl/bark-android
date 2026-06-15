package day.bark.android

import org.json.JSONObject

object BarkCryptoExampleScript {
    private const val OPENSSL_SAMPLE_PAYLOAD = "{\"body\": \"test\", \"sound\": \"birdsong\"}"
    private const val GCM_SAMPLE_PAYLOAD = "{\"body\":\"test\",\"sound\":\"birdsong\"}"
    private const val DOCUMENTATION_URL = "https://bark.day.app/#/en-us/encryption"

    fun create(profile: BarkServerProfile, settings: CryptoSettings): String {
        val bits = aesBits(settings)
        val mode = settings.mode.uppercase()
        val previewPayload = if (mode == "GCM") GCM_SAMPLE_PAYLOAD else OPENSSL_SAMPLE_PAYLOAD
        val previewCiphertext = AesCipher.encrypt(previewPayload, settings)
        val address = profile.address.trimEnd('/')
        val deviceKey = profile.key.ifBlank { "Your Key" }
        return if (mode == "GCM") {
            gcmScript(address, deviceKey, settings, bits, previewCiphertext)
        } else {
            opensslScript(address, deviceKey, settings, bits, mode, previewCiphertext)
        }
    }

    private fun opensslScript(
        address: String,
        deviceKey: String,
        settings: CryptoSettings,
        bits: Int,
        mode: String,
        previewCiphertext: String,
    ): String {
        val iv = settings.iv.orEmpty()
        val ivHexAssignment = if (iv.isBlank()) {
            ""
        } else {
            "ivHex=\$(printf %s \"\$iv\" | xxd -ps -c 200)\n"
        }
        val ivOption = if (iv.isBlank()) "" else " -iv \"\$ivHex\""
        val ivCurl = if (iv.isBlank()) "" else " --data-urlencode \"iv=\$iv\""
        return """
            |#!/usr/bin/env bash
            |
            |# Documentation: $DOCUMENTATION_URL
            |
            |set -e
            |
            |# bark key
            |deviceKey=${shellQuote(deviceKey)}
            |# push payload
            |json=${shellQuote(OPENSSL_SAMPLE_PAYLOAD)}
            |
            |# AES-$bits key
            |key=${shellQuote(settings.key)}
            |# AES IV
            |iv=${shellQuote(iv)}
            |
            |keyHex=${'$'}(printf %s "${'$'}key" | xxd -ps -c 200)
            |$ivHexAssignment|ciphertext=${'$'}(printf %s "${'$'}json" | openssl enc -aes-$bits-${mode.lowercase()} -K "${'$'}keyHex"$ivOption | base64)
            |
            |# Expected example ciphertext: "$previewCiphertext"
            |echo "${'$'}ciphertext"
            |
            |curl --data-urlencode "ciphertext=${'$'}ciphertext"$ivCurl $address/${'$'}deviceKey
            |""".trimMargin()
    }

    private fun gcmScript(
        address: String,
        deviceKey: String,
        settings: CryptoSettings,
        bits: Int,
        previewCiphertext: String,
    ): String =
        """
        |// Documentation: $DOCUMENTATION_URL
        |
        |const crypto = require('crypto');
        |
        |// bark key
        |const deviceKey = ${jsonString(deviceKey)};
        |// push payload
        |const json = JSON.stringify({ body: "test", sound: "birdsong" });
        |
        |// AES-$bits key
        |const key = ${jsonString(settings.key)};
        |// AES-GCM IV
        |const iv = ${jsonString(settings.iv.orEmpty())};
        |
        |const cipher = crypto.createCipheriv('aes-$bits-gcm', Buffer.from(key, 'utf8'), Buffer.from(iv, 'utf8'));
        |const encrypted = Buffer.concat([
        |  cipher.update(json, 'utf8'),
        |  cipher.final()
        |]);
        |const tag = cipher.getAuthTag();
        |const ciphertext = Buffer.concat([encrypted, tag]).toString('base64');
        |
        |// Expected example ciphertext: "$previewCiphertext"
        |console.log(ciphertext);
        |
        |const pushUrl = `$address/${'$'}{deviceKey}?ciphertext=${'$'}{encodeURIComponent(ciphertext)}&iv=${'$'}{encodeURIComponent(iv)}`;
        |console.log(pushUrl);
        |""".trimMargin()

    private fun aesBits(settings: CryptoSettings): Int =
        when (settings.algorithm.uppercase()) {
            "AES128" -> 128
            "AES192" -> 192
            "AES256" -> 256
            else -> throw IllegalArgumentException("Invalid algorithm")
        }

    private fun shellQuote(value: String): String =
        "'${value.replace("'", "'\"'\"'")}'"

    private fun jsonString(value: String): String =
        JSONObject.quote(value)
}
