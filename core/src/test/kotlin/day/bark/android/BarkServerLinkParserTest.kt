package day.bark.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BarkServerLinkParserTest {
    @Test
    fun `parses copied default Bark push url`() {
        val link = BarkServerLinkParser.parse("https://api.day.app/abc123/")

        assertEquals("https://api.day.app", link?.address)
        assertEquals("abc123", link?.key)
    }

    @Test
    fun `parses copied self hosted push url with path prefix`() {
        val link = BarkServerLinkParser.parse("https://push.example.com/bark/abc123?title=Hi")

        assertEquals("https://push.example.com/bark", link?.address)
        assertEquals("abc123", link?.key)
    }

    @Test
    fun `parses credentialed self hosted push url without dropping basic auth user info`() {
        val link = BarkServerLinkParser.parse("https://bark-user:bark-pass@push.example.com/bark/abc123?title=Hi")

        assertEquals("https://bark-user:bark-pass@push.example.com/bark", link?.address)
        assertEquals("abc123", link?.key)
    }

    @Test
    fun `parses documented default server push url with body path`() {
        val link = BarkServerLinkParser.parse("https://api.day.app/ynJ5Ft4atkMkWeo2PAvFhF/body?group=ops")

        assertEquals("https://api.day.app", link?.address)
        assertEquals("ynJ5Ft4atkMkWeo2PAvFhF", link?.key)
    }

    @Test
    fun `parses self hosted root push url with short custom key and body path`() {
        val link = BarkServerLinkParser.parse("https://push.example.com/team-key/body?group=ops")

        assertEquals("https://push.example.com", link?.address)
        assertEquals("team-key", link?.key)
    }

    @Test
    fun `parses self hosted prefixed push url with generated key and body path`() {
        val link = BarkServerLinkParser.parse("https://push.example.com/bark/ynJ5Ft4atkMkWeo2PAvFhF/title/body")

        assertEquals("https://push.example.com/bark", link?.address)
        assertEquals("ynJ5Ft4atkMkWeo2PAvFhF", link?.key)
    }

    @Test
    fun `parses self hosted bark prefixed push url with short custom key and body path`() {
        val link = BarkServerLinkParser.parse("https://push.example.com/bark/team-key/title/body?group=ops")

        assertEquals("https://push.example.com/bark", link?.address)
        assertEquals("team-key", link?.key)
    }

    @Test
    fun `parses credentialed self hosted push url with generated key and body path`() {
        val link = BarkServerLinkParser.parse("https://bark%40user:pa%3Ass@push.example.com/bark/ynJ5Ft4atkMkWeo2PAvFhF/body")

        assertEquals("https://bark%40user:pa%3Ass@push.example.com/bark", link?.address)
        assertEquals("ynJ5Ft4atkMkWeo2PAvFhF", link?.key)
    }

    @Test
    fun `parses bark addserver deep link address`() {
        val link = BarkServerLinkParser.parse("bark://addserver?address=https%3A%2F%2Fpush.example.com%2Fbark")

        assertEquals("https://push.example.com/bark", link?.address)
        assertEquals("", link?.key)
    }

    @Test
    fun `ignores invalid or unsupported text`() {
        assertNull(BarkServerLinkParser.parse("not a url"))
        assertNull(BarkServerLinkParser.parse("ftp://api.day.app/key"))
    }
}
