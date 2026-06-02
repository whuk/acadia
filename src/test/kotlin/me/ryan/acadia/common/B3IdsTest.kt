package me.ryan.acadia.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class B3IdsTest {
    @Test
    fun `TraceId는 16 또는 32자리 hex만 유효하다`() {
        assertThat(B3Ids.isValidTraceId("abcdef0123456789")).isTrue() // 16 hex
        assertThat(B3Ids.isValidTraceId("abcdef0123456789abcdef0123456789")).isTrue() // 32 hex

        assertThat(B3Ids.isValidTraceId("ABCDEF0123456789")).isFalse() // uppercase
        assertThat(B3Ids.isValidTraceId("xyz")).isFalse()
        assertThat(B3Ids.isValidTraceId("abcdef012345678")).isFalse() // 15 chars
        assertThat(B3Ids.isValidTraceId("")).isFalse()
        assertThat(B3Ids.isValidTraceId("not-a-valid-trace!")).isFalse()
    }

    @Test
    fun `SpanId는 16자리 hex만 유효하다`() {
        assertThat(B3Ids.isValidSpanId("1111111111111111")).isTrue()

        assertThat(B3Ids.isValidSpanId("abcdef0123456789abcdef0123456789")).isFalse() // 32 hex (trace, not span)
        assertThat(B3Ids.isValidSpanId("short")).isFalse()
        assertThat(B3Ids.isValidSpanId("")).isFalse()
    }
}
