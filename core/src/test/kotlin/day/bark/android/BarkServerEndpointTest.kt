package day.bark.android

import kotlin.test.Test
import kotlin.test.assertEquals

class BarkServerEndpointTest {
    @Test
    fun `server url with user info strips credentials and exposes basic auth header`() {
        val endpoint = BarkServerEndpoint.from("https://bark-user:bark-pass@push.example.com:8443/bark/")

        assertEquals("https://push.example.com:8443/bark", endpoint.baseUrl)
        assertEquals("Basic YmFyay11c2VyOmJhcmstcGFzcw==", endpoint.authorizationHeader)
    }

    @Test
    fun `server url without user info does not expose authorization header`() {
        val endpoint = BarkServerEndpoint.from("http://10.0.2.2:8080/bark/")

        assertEquals("http://10.0.2.2:8080/bark", endpoint.baseUrl)
        assertEquals(null, endpoint.authorizationHeader)
    }

    @Test
    fun `percent encoded user info is decoded before basic auth encoding`() {
        val endpoint = BarkServerEndpoint.from("https://bark%40user:pa%3Ass@push.example.com")

        assertEquals("https://push.example.com", endpoint.baseUrl)
        assertEquals("Basic YmFya0B1c2VyOnBhOnNz", endpoint.authorizationHeader)
    }
}
