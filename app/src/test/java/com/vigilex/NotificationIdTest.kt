package com.vigilex

import org.junit.Assert.*
import org.junit.Test

class NotificationIdTest {

    @Test fun `notification ID does not overflow Int`() {
        // Simulate timestamps far in the future (year 2100+)
        val farFuture = 4_102_444_800_000L  // Unix ms for year 2100
        val id = (farFuture % Int.MAX_VALUE).toInt()
        assertTrue("Notification ID must be non-negative", id >= 0)
    }

    @Test fun `notification IDs from consecutive timestamps are different`() {
        val t1 = 1_700_000_000_000L
        val t2 = 1_700_000_001_000L
        val id1 = (t1 % Int.MAX_VALUE).toInt()
        val id2 = (t2 % Int.MAX_VALUE).toInt()
        assertNotEquals(id1, id2)
    }
}
