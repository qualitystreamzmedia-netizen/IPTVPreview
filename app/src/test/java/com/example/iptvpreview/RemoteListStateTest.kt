package com.example.iptvpreview

import com.example.iptvpreview.MainActivity.RemoteAction
import com.example.iptvpreview.ui.utils.ListNavigationState
import org.junit.Assert.*
import org.junit.Test

class RemoteListStateTest {
    @Test fun repeatedPressesAndBoundaries() {
        val state = ListNavigationState()
        repeat(8) { state.handle(RemoteAction.DOWN, 3) {} }
        assertEquals(2, state.selectedIndex)
        repeat(8) { state.handle(RemoteAction.UP, 3) {} }
        assertEquals(0, state.selectedIndex)
    }

    @Test fun shrinkingAndEmptyListsCannotActivateInvalidItems() {
        val state = ListNavigationState(10)
        var activated = -1
        state.handle(RemoteAction.OK, 2) { activated = it }
        assertEquals(1, activated)
        activated = -1
        assertFalse(state.handle(RemoteAction.OK, 0) { activated = it })
        assertEquals(-1, activated)
        assertEquals(0, state.selectedIndex)
        assertFalse(state.handle(RemoteAction.MENU, 5) {})
    }
}
