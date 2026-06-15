package day.bark.android

import kotlin.test.Test
import kotlin.test.assertEquals

class BarkServerProfilesTest {
    @Test
    fun `current profile falls back to first profile when selected id is missing`() {
        val state = BarkServerProfiles(
            profiles = listOf(
                BarkServerProfile(id = "first", address = "https://api.day.app", key = "a"),
                BarkServerProfile(id = "second", address = "https://bark.example", key = "b"),
            ),
            currentId = "missing",
        )

        assertEquals("first", state.normalized().currentId)
        assertEquals("https://api.day.app", state.current().address)
    }

    @Test
    fun `adding a profile selects it and preserves existing profiles`() {
        val state = BarkServerProfiles.empty(defaultAddress = "https://api.day.app")
            .add(BarkServerProfile(id = "custom", address = "https://bark.example/", key = "key"))

        assertEquals(listOf("default", "custom"), state.profiles.map { it.id })
        assertEquals("custom", state.currentId)
        assertEquals("https://bark.example", state.current().address)
    }

    @Test
    fun `adding same normalized address and key selects existing profile without duplicating`() {
        val state = BarkServerProfiles.empty(defaultAddress = "https://api.day.app")
            .add(BarkServerProfile(id = "first", address = "https://push.example.com/deep", key = ""))
            .add(BarkServerProfile(id = "replayed", address = "https://push.example.com/deep/", key = ""))

        assertEquals(listOf("default", "first"), state.profiles.map { it.id })
        assertEquals("first", state.currentId)
    }

    @Test
    fun `current profile can be updated from editable fields`() {
        val state = BarkServerProfiles.empty(defaultAddress = "https://api.day.app")
            .updateCurrent(address = "https://self.hosted/", key = "new-key")

        assertEquals("https://self.hosted", state.current().address)
        assertEquals("new-key", state.current().key)
    }

    @Test
    fun `profile copy url matches bark address key slash format`() {
        val profile = BarkServerProfile(
            id = "custom",
            address = "https://bark.example/",
            key = "device-key",
        )

        assertEquals("https://bark.example/device-key/", profile.pushBaseUrl)
    }

    @Test
    fun `blank current profile address falls back to supplied default address`() {
        val state = BarkServerProfiles.empty(defaultAddress = "http://10.0.2.2:8080")
            .updateCurrent(address = " ", key = "key", defaultAddress = "http://10.0.2.2:8080")

        assertEquals("http://10.0.2.2:8080", state.current(defaultAddress = "http://10.0.2.2:8080").address)
    }

    @Test
    fun `empty profiles default to official Bark API`() {
        val state = BarkServerProfiles.empty()

        assertEquals("https://api.day.app", state.current().address)
        assertEquals(
            "https://api.day.app/Your%20Key/Custom%20Notification%20Content",
            BarkPushExampleCatalog.examples.first().url(state.current()),
        )
    }

    @Test
    fun `removing current profile selects the first remaining profile`() {
        val state = BarkServerProfiles.empty(defaultAddress = "https://api.day.app")
            .add(BarkServerProfile(id = "custom", address = "https://bark.example", key = "key"))
            .remove("custom", defaultAddress = "https://api.day.app")

        assertEquals("default", state.currentId)
        assertEquals(listOf("default"), state.profiles.map { it.id })
    }

    @Test
    fun `removing the last profile restores a default profile`() {
        val state = BarkServerProfiles.empty(defaultAddress = "https://api.day.app")
            .remove("default", defaultAddress = "https://api.day.app")

        assertEquals("default", state.currentId)
        assertEquals("https://api.day.app", state.current().address)
    }

    @Test
    fun `profiles round trip through json`() {
        val state = BarkServerProfiles(
            profiles = listOf(
                BarkServerProfile(id = "default", address = "https://api.day.app", key = "a"),
                BarkServerProfile(id = "custom", address = "https://bark.example", key = "b", name = "Home"),
            ),
            currentId = "custom",
        )

        val decoded = BarkServerProfiles.fromJson(state.toJson(), defaultAddress = "https://api.day.app")

        assertEquals(state, decoded)
    }

    @Test
    fun `json null-like profile name is decoded as absent name`() {
        val decoded = BarkServerProfiles.fromJson(
            """
            {
              "currentId": "default",
              "profiles": [
                {"id": "default", "address": "http://10.0.2.2:8080", "key": "", "name": "null"}
              ]
            }
            """.trimIndent(),
            defaultAddress = "http://10.0.2.2:8080",
        )

        assertEquals(null, decoded.current(defaultAddress = "http://10.0.2.2:8080").name)
        assertEquals("http://10.0.2.2:8080", decoded.current(defaultAddress = "http://10.0.2.2:8080").displayName)
    }

    @Test
    fun `profile name can be renamed and cleared by id`() {
        val state = BarkServerProfiles.empty(defaultAddress = "https://api.day.app")
            .add(BarkServerProfile(id = "custom", address = "https://bark.example", key = "key"))
            .rename("custom", " Home Bark ")

        assertEquals("Home Bark", state.profiles.first { it.id == "custom" }.name)
        assertEquals("Home Bark", state.profiles.first { it.id == "custom" }.displayName)

        val cleared = state.rename("custom", " ")

        assertEquals(null, cleared.profiles.first { it.id == "custom" }.name)
        assertEquals("https://bark.example", cleared.profiles.first { it.id == "custom" }.displayName)
    }

    @Test
    fun `profile key can be updated by id without changing selection`() {
        val state = BarkServerProfiles.empty(defaultAddress = "https://api.day.app")
            .add(BarkServerProfile(id = "custom", address = "https://bark.example", key = "old"))
            .select("default")
            .updateKey("custom", "new")

        assertEquals("default", state.currentId)
        assertEquals("new", state.profiles.first { it.id == "custom" }.key)
    }

    @Test
    fun `poll targets include every profile with a device key`() {
        val state = BarkServerProfiles(
            profiles = listOf(
                BarkServerProfile(id = "default", address = "https://api.day.app", key = " "),
                BarkServerProfile(id = "home", address = "https://home.example/", key = "home-key"),
                BarkServerProfile(id = "work", address = "https://work.example", key = "work-key"),
            ),
            currentId = "work",
        )

        assertEquals(
            listOf(
                BarkPollTarget(id = "home", address = "https://home.example", key = "home-key"),
                BarkPollTarget(id = "work", address = "https://work.example", key = "work-key"),
            ),
            state.pollTargets(),
        )
    }
}
