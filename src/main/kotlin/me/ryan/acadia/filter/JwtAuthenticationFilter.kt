package me.ryan.acadia.filter

import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import me.ryan.acadia.common.GatewayHeaders
import me.ryan.acadia.config.JwtProperties
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import javax.crypto.SecretKey

@Component
class JwtAuthenticationFilter(
    private val jwtProperties: JwtProperties,
) : GlobalFilter,
    Ordered {
    private val secretKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray())
    }

    companion object {
        private const val BEARER_PREFIX = "Bearer "
        private const val ROLES_CLAIM = "roles"
        private const val PUBLIC_PATH_PREFIX = "/api/public/"
    }

    override fun filter(
        exchange: ServerWebExchange,
        chain: GatewayFilterChain,
    ): Mono<Void> {
        val path = exchange.request.path.value()
        if (path.startsWith(PUBLIC_PATH_PREFIX)) {
            return chain.filter(exchange)
        }

        val authHeader =
            exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)
                ?: return unauthorized(exchange)

        val token = extractToken(authHeader) ?: return unauthorized(exchange)

        val claims = parseToken(token) ?: return unauthorized(exchange)

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

    private fun parseToken(token: String): Claims? =
        try {
            Jwts
                .parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .payload
        } catch (_: JwtException) {
            null
        }

    private fun unauthorized(exchange: ServerWebExchange): Mono<Void> {
        exchange.response.statusCode = HttpStatus.UNAUTHORIZED
        return exchange.response.setComplete()
    }

    override fun getOrder(): Int = -100
}
