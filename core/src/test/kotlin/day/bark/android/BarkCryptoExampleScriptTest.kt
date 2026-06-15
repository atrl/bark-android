package day.bark.android

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BarkCryptoExampleScriptTest {
    @Test
    fun `builds openssl cbc example script from profile and crypto settings`() {
        val settings = CryptoSettings(
            algorithm = "AES128",
            mode = "CBC",
            padding = "pkcs7",
            key = "1234567890123456",
            iv = "abcdefghijklmnop",
        )

        val script = BarkCryptoExampleScript.create(
            profile = BarkServerProfile(id = "default", address = "https://push.example/", key = "device-key"),
            settings = settings,
        )

        assertTrue(script.contains("#!/usr/bin/env bash"))
        assertTrue(script.contains("deviceKey='device-key'"))
        assertTrue(script.contains("json='{\"body\": \"test\", \"sound\": \"birdsong\"}'"))
        assertTrue(script.contains("key='1234567890123456'"))
        assertTrue(script.contains("iv='abcdefghijklmnop'"))
        assertTrue(script.contains("openssl enc -aes-128-cbc"))
        assertTrue(script.contains("--data-urlencode \"ciphertext=${'$'}ciphertext\""))
        assertTrue(script.contains("--data-urlencode \"iv=${'$'}iv\""))
        assertTrue(script.contains("https://push.example/${'$'}deviceKey"))
        assertTrue(script.contains(AesCipher.encrypt("{\"body\": \"test\", \"sound\": \"birdsong\"}", settings)))
    }

    @Test
    fun `builds node gcm example script with auth tag and encoded iv query`() {
        val settings = CryptoSettings(
            algorithm = "AES128",
            mode = "GCM",
            padding = "noPadding",
            key = "1234567890123456",
            iv = "abcdefghijkl",
        )

        val script = BarkCryptoExampleScript.create(
            profile = BarkServerProfile(id = "default", address = "https://push.example", key = "device-key"),
            settings = settings,
        )

        assertTrue(script.contains("const crypto = require('crypto');"))
        assertTrue(script.contains("const deviceKey = \"device-key\";"))
        assertTrue(script.contains("crypto.createCipheriv('aes-128-gcm'"))
        assertTrue(script.contains("cipher.getAuthTag()"))
        assertTrue(script.contains("Buffer.concat([encrypted, tag])"))
        assertTrue(script.contains("ciphertext=${'$'}{encodeURIComponent(ciphertext)}&iv=${'$'}{encodeURIComponent(iv)}"))
        assertTrue(script.contains("https://push.example/${'$'}{deviceKey}"))
        assertTrue(script.contains(AesCipher.encrypt("{\"body\":\"test\",\"sound\":\"birdsong\"}", settings)))
    }

    @Test
    fun `rejects invalid crypto settings before returning a script`() {
        assertFailsWith<IllegalArgumentException> {
            BarkCryptoExampleScript.create(
                profile = BarkServerProfile(id = "default", address = "https://push.example", key = "device-key"),
                settings = CryptoSettings(
                    algorithm = "AES128",
                    mode = "CBC",
                    padding = "pkcs7",
                    key = "short",
                    iv = "abcdefghijklmnop",
                ),
            )
        }
    }
}
