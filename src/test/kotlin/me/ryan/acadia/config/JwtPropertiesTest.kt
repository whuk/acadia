package me.ryan.acadia.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class JwtPropertiesTest {
    @Test
    fun `짧은 secret은 거부된다`() {
        assertThrows<IllegalArgumentException> {
            JwtProperties("short")
        }
    }

    @Test
    fun `빈 secret은 거부된다`() {
        assertThrows<IllegalArgumentException> {
            JwtProperties("")
        }
    }

    @Test
    fun `256비트 이상 secret은 허용된다`() {
        val secret = "default-secret-key-for-testing-purposes-only-32bytes"
        val properties = JwtProperties(secret)
        assertEquals(secret, properties.secret)
    }
}
