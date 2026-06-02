package me.ryan.acadia.common

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import java.util.Collections
import java.util.Enumeration

/**
 * Servlet request wrapper that injects/overrides or removes headers passed to downstream routing.
 *
 * - Injected headers take precedence over (override) any inbound header with the same name.
 * - Removed headers are hidden from downstream entirely.
 *
 * Both are required to neutralize client-spoofed trusted headers (e.g. X-User-*).
 */
class HeaderInjectingRequestWrapper(
    request: HttpServletRequest,
    extraHeaders: Map<String, String> = emptyMap(),
    removedHeaders: Set<String> = emptySet(),
) : HttpServletRequestWrapper(request) {
    // Case-insensitive header name handling, consistent with HTTP semantics.
    private val injected: Map<String, String> = extraHeaders.mapKeys { it.key.lowercase() }
    private val removed: Set<String> = removedHeaders.map { it.lowercase() }.toSet()

    override fun getHeader(name: String): String? {
        val lower = name.lowercase()
        if (lower in removed) return null
        return injected[lower] ?: super.getHeader(name)
    }

    override fun getHeaders(name: String): Enumeration<String> {
        val lower = name.lowercase()
        if (lower in removed) return Collections.emptyEnumeration()
        return injected[lower]
            ?.let { Collections.enumeration(listOf(it)) }
            ?: super.getHeaders(name)
    }

    override fun getHeaderNames(): Enumeration<String> {
        val names = LinkedHashSet<String>()
        super.getHeaderNames().asIterator().forEach { name ->
            if (name.lowercase() !in removed) names.add(name)
        }
        injected.keys.forEach { lower ->
            if (names.none { it.lowercase() == lower }) names.add(lower)
        }
        return Collections.enumeration(names)
    }
}
