package me.ryan.acadia.filter

/**
 * Centralized, deterministic servlet filter ordering (QUAL-1).
 * Lower value runs earlier (outermost).
 *
 * Rate limiting is outermost (DoS protection applies to all requests).
 * Request-id/trace/span are assigned before authentication so every response
 * (including 401) is traceable. Logging captures all requests, then JWT
 * authentication injects trusted X-User-* headers for downstream routing.
 */
object FilterOrders {
    const val RATE_LIMIT = 0
    const val REQUEST_ID = 10
    const val TRACE_ID = 20
    const val SPAN_ID = 30
    const val RESPONSE_LOGGING = 35
    const val CACHED_BODY = 40
    const val REQUEST_LOGGING = 50
    const val BACKEND_ERROR = 55
    const val JWT_AUTH = 60
}
