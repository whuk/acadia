package me.ryan.acadia.common

import jakarta.servlet.http.HttpServletRequest

/**
 * Resolves the gateway-relative request path (context path removed).
 *
 * SEC-1a: [HttpServletRequest.getRequestURI] includes the servlet context path, while gateway
 * routing matches the context-relative path. Path-based auth decisions must use the relative
 * path; otherwise setting `server.servlet.context-path` would let a prefixed API path evade the
 * API authentication boundary.
 */
object RequestPaths {
    fun gatewayRelative(request: HttpServletRequest): String = request.requestURI.removePrefix(request.contextPath)
}
