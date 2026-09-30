package day.bark.android.projects

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BarkProjectCatalogTest {
    private val first = BarkProject("one", "第一个项目", "https://one.example/", "项目简介", "项目提醒")
    private val second = BarkProject("two", "第二个项目", "https://two.example/dashboard?q=a%20b#chart")

    @Test
    fun `new installs contain a stable IC entry`() {
        val projects = BarkProjectCatalog.fromJson(null).projects
        assertEquals(listOf("ic-basis"), projects.map { it.id })
        assertEquals("https://basis.atrl.me/", projects.single().url)
        assertEquals("ic-basis", projects.single().group)
    }

    @Test
    fun `add edit reorder and remove survive storage without changing ids`() {
        val added = BarkProjectCatalog(emptyList()).save(first).save(second)
        val edited = added.save(first.copy(name = " 新名称 ", description = " 新简介 ", group = " "))
        val moved = edited.move("two", -1)
        val restored = BarkProjectCatalog.fromJson(moved.toJson())
        assertEquals(listOf("two", "one"), restored.projects.map { it.id })
        assertEquals("新名称", restored.projects.last().name)
        assertEquals("新简介", restored.projects.last().description)
        assertEquals(null, restored.projects.last().group)
        assertEquals(second.url, restored.projects.first().url)
        assertEquals(listOf(second), BarkProjectCatalog.fromJson(restored.remove("one").toJson()).projects)
    }

    @Test
    fun `removing the last project does not resurrect default projects`() {
        val empty = BarkProjectCatalog.defaults().remove("ic-basis")
        assertTrue(BarkProjectCatalog.fromJson(empty.toJson()).projects.isEmpty())
    }

    @Test
    fun `move clamps safely and absent ids leave ordering unchanged`() {
        val catalog = BarkProjectCatalog(listOf(first, second))
        assertEquals(catalog, catalog.move("one", Int.MIN_VALUE))
        assertEquals(catalog, catalog.move("two", Int.MAX_VALUE))
        assertEquals(catalog, catalog.move("absent", -1))
        assertEquals(listOf(second, first), catalog.move("one", Int.MAX_VALUE).projects)
    }

    @Test
    fun `catalog roundtrip preserves Unicode quotes newlines and group`() {
        val project = first.copy(description = "图表 \"引用\"\n第二行 🔔")
        val catalog = BarkProjectCatalog(listOf(project, second))
        assertEquals(catalog, BarkProjectCatalog.fromJson(catalog.toJson()))
    }

    @Test
    fun `malformed records do not hide valid records and duplicate ids are ignored`() {
        val json = """{"version":1,"projects":[
            {"id":"bad","name":"危险网址","url":"javascript:alert(1)"},
            {"id":"one","name":"正确","url":"https://one.example"},
            {"id":"one","name":"重复","url":"https://replacement.example"},
            {"id":"wrong-type","name":123,"url":"https://number.example"},
            {"id":"two","name":"另一个","url":"https://two.example","group":null}
        ]}"""
        val projects = BarkProjectCatalog.fromJson(json).projects
        assertEquals(listOf("one", "two"), projects.map { it.id })
        assertEquals("https://one.example/", projects.first().url)
        assertEquals(null, projects.last().group)
    }

    @Test
    fun `corrupt or unsupported schema never becomes trusted project URLs`() {
        listOf("broken", "[]", "{}", """{"version":2,"projects":[]}""",
            """{"version":1,"projects":"wrong"}""").forEach { json ->
            assertTrue(BarkProjectCatalog.fromJson(json).projects.isEmpty(), json)
        }
    }

    @Test
    fun `blank id and name fail before storage`() {
        assertFailsWith<IllegalArgumentException> { BarkProjectCatalog(emptyList()).save(first.copy(id = " ")) }
        assertFailsWith<IllegalArgumentException> { BarkProjectCatalog(emptyList()).save(first.copy(name = " ")) }
    }

    @Test
    fun `URL normalization preserves encoded paths query and fragment`() {
        assertEquals("https://example.com/a%20b?q=c%2Fd#chart", BarkProjectUrl.normalize(" HTTPS://EXAMPLE.COM:443/a%20b?q=c%2Fd#chart "))
        assertEquals("https://example.com/", BarkProjectUrl.normalize("https://example.com"))
        assertEquals("https://example.com:8443/", BarkProjectUrl.normalize("https://example.com:8443"))
    }

    @Test
    fun `unsafe schemes credentials and malformed authorities are rejected`() {
        listOf(
            "http://basis.atrl.me/", "http://localhost:8080", "file:///private/data", "content://contacts/1",
            "javascript:alert(1)", "data:text/html,test", "//basis.atrl.me", "https:relative",
            "https://user:password@basis.atrl.me/", "https://basis.atrl.me@evil.example/",
            "https://basis.atrl.me:0/", "https://basis.atrl.me:65536/", "https://bad host/",
            "https://basis.atrl.me\\@evil.example/", "https://", "",
        ).forEach { url -> assertFailsWith<IllegalArgumentException>(url) { BarkProjectUrl.normalize(url) } }
    }

    @Test
    fun `same origin includes paths but rejects host suffix userinfo scheme and port confusion`() {
        val project = "https://basis.atrl.me/dashboard"
        assertTrue(BarkProjectUrl.sameOrigin(project, "HTTPS://BASIS.ATRL.ME:443/another?q=1#chart"))
        listOf(
            "https://basis.atrl.me.evil.example/", "https://evilbasis.atrl.me/",
            "https://basis.atrl.me@evil.example/", "https://evil@basis.atrl.me/",
            "https://basis.atrl.me:8443/", "http://basis.atrl.me/", "javascript:alert(1)",
        ).forEach { url -> assertFalse(BarkProjectUrl.sameOrigin(project, url), url) }
        assertTrue(BarkProjectUrl.sameOrigin("https://host.example:8443/", "https://host.example:8443/path"))
    }
}
