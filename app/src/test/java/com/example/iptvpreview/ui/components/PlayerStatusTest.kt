package com.example.iptvpreview.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerStatusTest {
    @Test fun bufferingCompletionRestoresActivePlayback() {
        assertEquals(PlayerStatus.BUFFERING, bufferingStatus(PlayerStatus.PLAYING, 25f))
        assertEquals(PlayerStatus.PLAYING, bufferingStatus(PlayerStatus.PLAYING, 100f))
    }

    @Test fun bufferingCannotOverridePausedStoppedOrError() {
        for (state in listOf(PlayerStatus.PAUSED, PlayerStatus.IDLE, PlayerStatus.ERROR)) {
            assertEquals(state, bufferingStatus(state, 20f))
            assertEquals(state, bufferingStatus(state, 100f))
        }
    }

    @Test fun initialBufferCompletionWaitsForPlayingEvent() {
        assertEquals(PlayerStatus.BUFFERING, bufferingStatus(PlayerStatus.BUFFERING, 100f))
    }
}
