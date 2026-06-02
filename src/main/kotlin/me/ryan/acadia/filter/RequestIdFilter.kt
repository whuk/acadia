package me.ryan.acadia.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import me.ryan.acadia.common.GatewayHeaders
import me.ryan.acadia.common.HeaderInjectingRequestWrapper
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
@Order(FilterOrders.REQUEST_ID)
class RequestIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val existingRequestId = request.getHeader(GatewayHeaders.X_REQUEST_ID)
        val requestId = existingRequestId ?: UUID.randomUUID().toString()

        // Add X-Request-Id to response headers for client
        response.addHeader(GatewayHeaders.X_REQUEST_ID, requestId)

        val effectiveRequest =
            if (existingRequestId == null) {
                HeaderInjectingRequestWrapper(request, mapOf(GatewayHeaders.X_REQUEST_ID to requestId))
            } else {
                request
            }

        filterChain.doFilter(effectiveRequest, response)
    }
}
