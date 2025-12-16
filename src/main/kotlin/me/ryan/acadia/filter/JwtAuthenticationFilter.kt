package me.ryan.acadia.filter

import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import me.ryan.acadia.common.GatewayHeaders
import me.ryan.acadia.common.GatewayPaths
import me.ryan.acadia.config.JwtProperties
import org.slf4j.LoggerFactory
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.time.Instant
import javax.crypto.SecretKey

@Component
class JwtAuthenticationFilter(
    private val jwtProperties: JwtProperties,
) : GlobalFilter,
    Ordered {
    private val log = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)

    private val secretKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray())
    }

    companion object {
        private const val BEARER_PREFIX = "Bearer "
        private const val ROLES_CLAIM = "roles"
    }

    override fun filter(
        exchange: ServerWebExchange,
        chain: GatewayFilterChain,
    ): Mono<Void> {
        val path = exchange.request.path.value()
        if (path.startsWith(GatewayPaths.PUBLIC_PREFIX) || path.startsWith(GatewayPaths.SWAGGER_DOCS)) {
            return chain.filter(exchange)
        }

        val authHeader =
            exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)
                ?: return unauthorized(exchange, "Missing Authorization header")

        val token =
            extractToken(authHeader)
                ?: return unauthorized(exchange, "Invalid Authorization header format")

        val claims = parseToken(exchange, token) ?: return Mono.empty()

        val roles = extractRoles(claims)

        val mutatedExchange =
            exchange
                .mutate()
                .request { request ->
                    request.header(GatewayHeaders.X_USER_ID, claims.subject)
                    if (roles.isNotEmpty()) {
                        request.header(GatewayHeaders.X_USER_ROLES, roles.joinToString(","))
                    }
                }.build()

        return chain.filter(mutatedExchange)
    }

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

    private fun parseToken(
        exchange: ServerWebExchange,
        token: String,
    ): Claims? =
        try {
            Jwts
                .parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .payload
        } catch (e: ExpiredJwtException) {
            unauthorized(exchange, "Token has expired").subscribe()
            null
        } catch (e: JwtException) {
            unauthorized(exchange, "Invalid token").subscribe()
            null
        }

    private fun unauthorized(
        exchange: ServerWebExchange,
        message: String,
    ): Mono<Void> {
        val path = exchange.request.path.value()
        log.warn("Authentication failed for path {}: {}", path, message)

        exchange.response.statusCode = HttpStatus.UNAUTHORIZED
        exchange.response.headers.contentType = MediaType.APPLICATION_JSON

        val timestamp = Instant.now().toString()
        val body = """{"timestamp":"$timestamp","status":401,"error":"Unauthorized","message":"$message","path":"$path"}"""
        val buffer = exchange.response.bufferFactory().wrap(body.toByteArray())

        return exchange.response.writeWith(Mono.just(buffer))
    }

    override fun getOrder(): Int = -100
}
