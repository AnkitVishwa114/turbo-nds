package com.appkraft.turbonds

import com.appkraft.turbonds.player.Cheat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CheatTest {
    @Test fun acceptsPastedCodesWithAnySpacing() {
        assertEquals("94000130 FCFF0000+12345678 9ABCDEF0", Cheat.normalise("94000130 fcff0000\n12345678  9abcdef0"))
        assertEquals("94000130 FCFF0000", Cheat.normalise("94000130FCFF0000"))
        assertEquals("94000130 FCFF0000+12345678 9ABCDEF0", Cheat.normalise("94000130 FCFF0000+12345678 9ABCDEF0"))
    }

    @Test fun rejectsBrokenCodes() {
        assertNull(Cheat.normalise(""))
        assertNull(Cheat.normalise("1234 5678"))
        assertNull(Cheat.normalise("hello"))
    }
}
