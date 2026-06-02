package me.ryan.acadia.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletResponseWrapper
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Converts backend 5xx responses to 502 Bad Gateway, preserving gateway-originated
 * 502/503/504 (circuit breaker open -> 503, timeout -> 504). The conversion is done at
 * the servlet status level because Gateway MVC writes the proxied response status directly.
 */
@Component
@Order(FilterOrders.BACKEND_ERROR)
class BackendErrorFilter : OncePerRequestFilter() {
    companion object {
        private val PASSTHROUGH = setOf(502, 503, 504)
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        filterChain.doFilter(request, StatusConvertingResponse(response))
    }

    private class StatusConvertingResponse(
        response: HttpServletResponse,
    ) : HttpServletResponseWrapper(response) {
        override fun setStatus(sc: Int) {
            super.setStatus(convert(sc))
        }

        override fun sendError(sc: Int) {
            super.sendError(convert(sc))
        }

        override fun sendError(
            sc: Int,
            msg: String?,
        ) {
            super.sendError(convert(sc), msg)
        }

        private fun convert(sc: Int): Int = if (sc in 500..599 && sc !in PASSTHROUGH) HttpStatus.BAD_GATEWAY.value() else sc
    }
}
