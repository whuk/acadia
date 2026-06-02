package me.ryan.acadia.common

/**
 * Validates inbound B3 (Zipkin) trace identifiers before they are trusted and propagated.
 *
 * Per gateway-observability rule section 5: a TraceId is 16 or 32 lowercase hex chars, a SpanId
 * is 16 lowercase hex chars. Inbound headers failing validation are not preserved (they are
 * regenerated/stripped) to prevent log injection and trace pollution.
 */
object B3Ids {
    private val TRACE_ID = Regex("[a-f0-9]{16}|[a-f0-9]{32}")
    private val SPAN_ID = Regex("[a-f0-9]{16}")

    fun isValidTraceId(value: String): Boolean = TRACE_ID.matches(value)

    fun isValidSpanId(value: String): Boolean = SPAN_ID.matches(value)
}
