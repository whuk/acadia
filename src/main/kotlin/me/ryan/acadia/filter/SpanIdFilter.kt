package me.ryan.acadia.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import me.ryan.acadia.common.B3Ids
import me.ryan.acadia.common.GatewayHeaders
import me.ryan.acadia.common.HeaderInjectingRequestWrapper
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
@Order(FilterOrders.SPAN_ID)
class SpanIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        // Rule 5: always generate a fresh SpanId for this hop. A valid inbound SpanId becomes the
        // parent; otherwise any (possibly spoofed) inbound ParentSpanId is stripped.
        val spanId = generateSpanId()
        val injected = mutableMapOf(GatewayHeaders.X_B3_SPAN_ID to spanId)
        val removed = mutableSetOf<String>()

        val inboundSpanId = request.getHeader(GatewayHeaders.X_B3_SPAN_ID)
        if (inboundSpanId != null && B3Ids.isValidSpanId(inboundSpanId)) {
            injected[GatewayHeaders.X_B3_PARENT_SPAN_ID] = inboundSpanId
        } else {
            removed.add(GatewayHeaders.X_B3_PARENT_SPAN_ID)
        }

        val wrapped = HeaderInjectingRequestWrapper(request, injected, removed)
        filterChain.doFilter(wrapped, response)
    }

    private fun generateSpanId(): String =
        UUID
            .randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 16)
}
