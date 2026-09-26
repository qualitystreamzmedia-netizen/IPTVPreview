package com.example.iptvpreview

import com.example.iptvpreview.ui.components.PlaybackRetryPolicy
import org.junit.Assert.*
import org.junit.Test

class PlaybackRetryPolicyTest {
    @Test fun retriesThreeTimesWithExponentialDelayAndResets() {
        val policy = PlaybackRetryPolicy()
        assertEquals(listOf(1000L, 2000L, 4000L), List(3) { policy.nextDelayMillis() })
        repeat(3) { assertNull(policy.nextDelayMillis()) }
        policy.reset()
        assertEquals(1000L, policy.nextDelayMillis())
    }
}
