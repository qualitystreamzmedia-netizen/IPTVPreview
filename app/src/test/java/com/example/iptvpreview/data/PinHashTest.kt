package com.example.iptvpreview.data

import org.junit.Assert.*
import org.junit.Test

class PinHashTest {
    @Test fun randomSaltsAndVerification() {
        val first = PinHash.create("1234")
        assertNotEquals(first, PinHash.create("1234"))
        assertTrue(PinHash.verify("1234", first))
        assertFalse(PinHash.verify("4321", first))
        assertFalse(PinHash.verify("1234", "malformed"))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsInvalidPin() { PinHash.create("12a4") }
}
