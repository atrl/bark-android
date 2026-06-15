package day.bark.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BarkPushExampleCatalogTest {
    @Test
    fun `examples mirror upstream Bark home preview parameters`() {
        val queries = BarkPushExampleCatalog.examples.map { it.queryParameter }

        assertTrue(queries.contains(null))
        assertTrue(queries.contains("sound=minuet"))
        assertTrue(queries.contains("call=1"))
        assertTrue(queries.contains("isArchive=1"))
        assertTrue(queries.contains("icon=https://day.app/assets/images/avatar.jpg"))
        assertTrue(queries.contains("group=groupName"))
        assertTrue(queries.contains("ciphertext=ciphertext"))
        assertTrue(queries.contains("level=critical&volume=5"))
        assertTrue(queries.contains("level=timeSensitive"))
        assertTrue(queries.contains("url=https://www.baidu.com"))
        assertTrue(queries.contains("image=https://day.app/assets/images/avatar.jpg"))
        assertTrue(queries.contains("copy=test"))
        assertTrue(queries.contains("badge=1"))
        assertTrue(queries.contains("autoCopy=1&copy=optional"))
    }

    @Test
    fun `example url uses current server key and query parameter`() {
        val profile = BarkServerProfile(
            id = "server",
            address = "https://push.example.com/bark/",
            key = "abc123",
        )
        val sound = BarkPushExampleCatalog.examples.first { it.queryParameter == "sound=minuet" }

        assertEquals(
            "https://push.example.com/bark/abc123/Notification%20Sound?sound=minuet",
            sound.url(profile),
        )
    }

    @Test
    fun `example url shows placeholder key before registration`() {
        val profile = BarkServerProfile(
            id = "server",
            address = "http://10.0.2.2:8080",
            key = "",
        )
        val simple = BarkPushExampleCatalog.examples.first()

        assertEquals(
            "http://10.0.2.2:8080/Your%20Key/Custom%20Notification%20Content",
            simple.url(profile),
        )
    }
}
