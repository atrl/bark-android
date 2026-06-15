package day.bark.android

import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownTextTest {
    @Test
    fun `plain text keeps bark markdown notification content readable`() {
        val markdown = """
            # Deploy **pre-market** update

            - Keep [runbook](https://day.app) and `code` visible
            ![chart](https://day.app/chart.png)

            ```json
            {"ok":true}
            ```
        """.trimIndent()

        assertEquals(
            "Deploy pre-market update\n• Keep runbook and code visible\n[chart]\n{\"ok\":true}",
            MarkdownText.plainText(markdown),
        )
    }

    @Test
    fun `soft line breaks inside a paragraph render as spaces`() {
        val markdown = """
            First line
            second line

            Third line
        """.trimIndent()

        assertEquals(
            "First line second line\nThird line",
            MarkdownText.plainText(markdown),
        )
    }

    @Test
    fun `ordered list items remain separate notification lines`() {
        val markdown = """
            1. First step
            2. Second step
        """.trimIndent()

        assertEquals(
            "1. First step\n2. Second step",
            MarkdownText.plainText(markdown),
        )
    }
}
