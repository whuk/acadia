package me.ryan.acadia.filter

import com.fasterxml.jackson.databind.ObjectMapper
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import me.ryan.acadia.common.GatewayHeaders
import me.ryan.acadia.common.GatewayPaths
import me.ryan.acadia.common.HeaderInjectingRequestWrapper
import me.ryan.acadia.config.JwtProperties
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Instant
import javax.crypto.SecretKey

@Component
@Order(FilterOrders.JWT_AUTH)
class JwtAuthenticationFilter(
    private val jwtProperties: JwtProperties,
) : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)
    private val objectMapper = ObjectMapper().findAndRegisterModules()

    private val secretKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray())
    }

    companion object {
        private const val BEARER_PREFIX = "Bearer "
        private const val ROLES_CLAIM = "roles"

        // Trusted headers the gateway controls; always neutralized from inbound requests (SEC-2).
        private val TRUSTED_HEADERS = setOf(GatewayHeaders.X_USER_ID, GatewayHeaders.X_USER_ROLES)
    }

    // Only gateway-proxied API paths are authenticated. Gateway's own endpoints
    // (actuator, swagger-ui, /v3/api-docs) and CORS preflight are not.
    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        if (request.method.equals("OPTIONS", ignoreCase = true)) return true
        return !request.requestURI.startsWith(GatewayPaths.API_PREFIX)
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val path = request.requestURI

        if (isPublicPath(path)) {
            // Even on public paths, strip client-supplied trusted headers (SEC-2).
            filterChain.doFilter(stripTrustedHeaders(request), response)
            return
        }

        val authHeader = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (authHeader == null) {
            unauthorized(response, path, "Missing Authorization header")
            return
        }

        val token = extractToken(authHeader)
        if (token == null) {
            unauthorized(response, path, "Invalid Authorization header format")
            return
        }

        val claims =
            try {
                parseToken(token)
            } catch (e: ExpiredJwtException) {
                unauthorized(response, path, "Token has expired")
                return
            } catch (e: JwtException) {
                unauthorized(response, path, "Invalid token")
                return
            }

        val roles = extractRoles(claims)

        // SEC-2: always override X-User-Id, and remove X-User-Roles unless derived from the verified token.
        val injected = mutableMapOf(GatewayHeaders.X_USER_ID to claims.subject)
        val removed = mutableSetOf<String>()
        if (roles.isNotEmpty()) {
            injected[GatewayHeaders.X_USER_ROLES] = roles.joinToString(",")
        } else {
            removed.add(GatewayHeaders.X_USER_ROLES)
        }

        filterChain.doFilter(HeaderInjectingRequestWrapper(request, injected, removed), response)
    }

    private fun isPublicPath(path: String): Boolean = path.startsWith(GatewayPaths.PUBLIC_PREFIX)

    private fun stripTrustedHeaders(request: HttpServletRequest): HttpServletRequest =
        HeaderInjectingRequestWrapper(request, removedHeaders = TRUSTED_HEADERS)

    private fun extractToken(authHeader: String): String? =
        authHeader
            .takeIf { it.startsWith(BEARER_PREFIX) }
            ?.removePrefix(BEARER_PREFIX)

    private fun extractRoles(claims: Claims): List<String> {
        val roles = claims[ROLES_CLAIM] ?: return emptyList()
        return when (roles) {
            is List<*> -> roles.filterIsInstance<String>()
            else -> emptyList()
        }
    }

    private fun parseToken(token: String): Claims =
        Jwts
            .parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .payload

    private fun unauthorized(
        response: HttpServletResponse,
        path: String,
        message: String,
    ) {
        log.warn("Authentication failed for path {}: {}", path, message)

        response.status = HttpStatus.UNAUTHORIZED.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE

        // SEC-5: build the body via JSON serialization, not string interpolation.
        val body =
            objectMapper.writeValueAsString(
                linkedMapOf(
                    "timestamp" to Instant.now().toString(),
                    "status" to HttpStatus.UNAUTHORIZED.value(),
                    "error" to "Unauthorized",
                    "message" to message,
                    "path" to path,
                ),
            )
        response.writer.write(body)
    }
}
