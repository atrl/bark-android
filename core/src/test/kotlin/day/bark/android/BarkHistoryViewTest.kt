package day.bark.android

import kotlin.test.Test
import kotlin.test.assertEquals

class BarkHistoryViewTest {
    @Test
    fun `available groups preserve recent first order and include default group`() {
        val groups = BarkHistoryView.groups(
            listOf(
                message(id = "1", group = "ops", createdAt = 3000),
                message(id = "2", group = null, createdAt = 2000),
                message(id = "3", group = "ops", createdAt = 1000),
                message(id = "4", group = "build", createdAt = 500),
            ),
            previewLimit = 5,
        )

        assertEquals(listOf("ops", null, "build"), groups.map { it.group })
        assertEquals(listOf("ops", "Default", "build"), groups.map { it.displayName })
        assertEquals(listOf(2, 1, 1), groups.map { it.totalCount })
    }

    @Test
    fun `group preview is capped but total count keeps full group size`() {
        val groups = BarkHistoryView.groups(
            listOf(
                message(id = "new", group = "ops", createdAt = 3000),
                message(id = "mid", group = "ops", createdAt = 2000),
                message(id = "old", group = "ops", createdAt = 1000),
            ),
            previewLimit = 2,
        )

        assertEquals(3, groups.single().totalCount)
        assertEquals(listOf("new", "mid"), groups.single().messages.map { it.id })
    }

    @Test
    fun `empty filter returns all messages in recent order`() {
        val messages = listOf(
            message(id = "old", group = "ops", createdAt = 1000),
            message(id = "new", group = "build", createdAt = 3000),
        )

        assertEquals(listOf("new", "old"), BarkHistoryView.filter(messages, emptySet()).map { it.id })
    }

    @Test
    fun `null filter returns default group messages only`() {
        val messages = listOf(
            message(id = "default", group = null, createdAt = 2000),
            message(id = "named", group = "ops", createdAt = 1000),
            message(id = "blank", group = " ", createdAt = 3000),
        )

        assertEquals(listOf("blank", "default"), BarkHistoryView.filter(messages, setOf(null)).map { it.id })
    }

    @Test
    fun `named filter returns matching group messages only`() {
        val messages = listOf(
            message(id = "ops-new", group = "ops", createdAt = 3000),
            message(id = "build", group = "build", createdAt = 2000),
            message(id = "ops-old", group = "ops", createdAt = 1000),
        )

        assertEquals(listOf("ops-new", "ops-old"), BarkHistoryView.filter(messages, setOf("ops")).map { it.id })
    }

    @Test
    fun `search matches title subtitle body and display body case insensitively`() {
        val messages = listOf(
            message(id = "title", group = null, createdAt = 1000).copy(title = "Deploy Finished"),
            message(id = "subtitle", group = null, createdAt = 2000).copy(subtitle = "Ops Window"),
            message(id = "body", group = null, createdAt = 3000).copy(body = "database backup"),
            message(id = "display", group = null, createdAt = 4000).copy(displayBody = "Markdown alert"),
            message(id = "miss", group = null, createdAt = 5000).copy(title = "nothing"),
        )

        assertEquals(listOf("display"), BarkHistoryView.search(messages, "markdown").map { it.id })
        assertEquals(listOf("body"), BarkHistoryView.search(messages, "BACKUP").map { it.id })
        assertEquals(listOf("subtitle"), BarkHistoryView.search(messages, "window").map { it.id })
        assertEquals(listOf("title"), BarkHistoryView.search(messages, "deploy").map { it.id })
    }

    @Test
    fun `blank search returns recent messages`() {
        val messages = listOf(
            message(id = "old", group = null, createdAt = 1000),
            message(id = "new", group = null, createdAt = 2000),
        )

        assertEquals(listOf("new", "old"), BarkHistoryView.search(messages, " ").map { it.id })
    }

    private fun message(id: String, group: String?, createdAt: Long): BarkMessage =
        BarkMessage(
            id = id,
            title = id,
            subtitle = null,
            body = "body $id",
            displayBody = "body $id",
            bodyType = null,
            url = null,
            image = null,
            icon = null,
            group = group,
            sound = null,
            badge = null,
            level = null,
            volume = null,
            call = false,
            autoCopy = false,
            copy = null,
            action = null,
            isDelete = false,
            shouldArchive = true,
            createAtMillis = createdAt,
            expireAtMillis = null,
            extras = emptyMap(),
        )
}
