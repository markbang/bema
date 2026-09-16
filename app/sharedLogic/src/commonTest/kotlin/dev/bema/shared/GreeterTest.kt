package dev.bema.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GreeterTest {
    @Test
    fun greetsWithNameAndPlatform() {
        val message = Greeter(platform = "UnitTest").greet("Bema")
        assertEquals("Hello, Bema! (shared logic running on UnitTest)", message)
    }

    @Test
    fun defaultConstructorUsesPlatformName() {
        assertTrue(Greeter().greet("Bema").contains(platformName()))
    }
}
