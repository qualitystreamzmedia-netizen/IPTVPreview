package com.example.iptvpreview

import com.example.iptvpreview.ui.navigation.FocusArea
import com.example.iptvpreview.ui.utils.adjacentFocusArea
import com.example.iptvpreview.ui.utils.remoteScrollTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class PaneNavigationTest {
    @Test fun controllerClampsDirectionsAndKeepsObservableStateInSync() {
        val controller = com.example.iptvpreview.ui.navigation.IptvNavController()
        controller.switchPane(-1)
        assertEquals(FocusArea.CATEGORY_LIST, controller.currentArea)
        repeat(6) { controller.switchPane(Int.MIN_VALUE) }
        assertEquals(FocusArea.NAV_RAIL, controller.focusArea.value)
        controller.switchPane(0)
        assertEquals(FocusArea.NAV_RAIL, controller.currentArea)
        repeat(6) { controller.switchPane(Int.MAX_VALUE) }
        assertEquals(FocusArea.PLAYER_CONTROLS, controller.currentArea)
        assertEquals(controller.currentArea, controller.focusArea.value)
    }
    @Test fun scrollTargetsHandleEmptyListsAndShrinkingBounds() {
        assertEquals(null, remoteScrollTarget(0, 1, 0))
        assertEquals(0, remoteScrollTarget(0, -1, 5))
        assertEquals(4, remoteScrollTarget(4, 1, 5))
        assertEquals(1, remoteScrollTarget(99, -1, 3))
        assertEquals(2, remoteScrollTarget(1, 1, 5))
    }
    @Test fun horizontalMovementFollowsPaneOrder() {
        assertEquals(FocusArea.CATEGORY_LIST, adjacentFocusArea(FocusArea.NAV_RAIL, true))
        assertEquals(FocusArea.CHANNEL_LIST, adjacentFocusArea(FocusArea.CATEGORY_LIST, true))
        assertEquals(FocusArea.PLAYER_CONTROLS, adjacentFocusArea(FocusArea.CHANNEL_LIST, true))
        assertEquals(FocusArea.CHANNEL_LIST, adjacentFocusArea(FocusArea.PLAYER_CONTROLS, false))
        assertEquals(FocusArea.CATEGORY_LIST, adjacentFocusArea(FocusArea.CHANNEL_LIST, false))
    }
    @Test fun outerBoundariesStayInPlace() {
        assertEquals(FocusArea.NAV_RAIL, adjacentFocusArea(FocusArea.NAV_RAIL, false))
        assertEquals(FocusArea.PLAYER_CONTROLS, adjacentFocusArea(FocusArea.PLAYER_CONTROLS, true))
    }
}
