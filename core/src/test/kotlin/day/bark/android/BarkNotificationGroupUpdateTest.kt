package day.bark.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BarkNotificationGroupUpdateTest {
    @Test
    fun `same id moving to another group cancels previous summary context`() {
        assertEquals(
            "old-group",
            BarkNotificationGroupUpdate.staleGroupToCancel(
                previousGroup = "old-group",
                nextGroup = "new-group",
            ),
        )
    }

    @Test
    fun `same id staying in the same group keeps current notification context`() {
        assertNull(
            BarkNotificationGroupUpdate.staleGroupToCancel(
                previousGroup = "ops",
                nextGroup = "ops",
            ),
        )
    }
}
