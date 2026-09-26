package com.example.iptvpreview.ui.components

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.MediaPlayer

@RunWith(AndroidJUnit4::class)
class VlcControllerAndroidTest {
    @Test fun nativeControllerReleasesOnceAndIgnoresLaterCommands() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val vlc = LibVLC(instrumentation.targetContext)
            val controller = VlcPlayerController(MediaPlayer(vlc), vlc)
            assertEquals(PlayerStatus.IDLE, controller.status.value)
            assertTrue(controller.getTracks().isEmpty())
            assertFalse(controller.selectTrack(-1))
            assertFalse(controller.selectTrack(99))
            controller.stop()
            controller.release()
            controller.release()
            controller.play()
            controller.pause()
            controller.stop()
            controller.setVolume(200)
            assertTrue(controller.isReleased)
            assertFalse(controller.isPlaying())
            assertEquals(0, controller.getVolume())
            assertTrue(controller.getTracks().isEmpty())
            assertFalse(controller.selectTrack(0))
            assertFalse(controller.selectTrack(VlcTrack(1, VlcTrackType.AUDIO, "Audio", false)))
            assertFalse(controller.disableTrack(0))
            assertFalse(controller.disableSubtitles())
            assertEquals(PlayerStatus.IDLE, controller.status.value)
        }
    }
}
