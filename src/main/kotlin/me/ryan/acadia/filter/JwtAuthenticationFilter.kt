package me.ryan.acadia.filter

import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
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
    }

    override fun filter(
        exchange: ServerWebExchange,
        chain: GatewayFilterChain,
    ): Mono<Void> {
        val authHeader =
            exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)
                ?: return unauthorized(exchange)

        val token = extractToken(authHeader) ?: return unauthorized(exchange)

        if (!isValidToken(token)) {
            return unauthorized(exchange)
        }

        return chain.filter(exchange)
    }

    private fun extractToken(authHeader: String): String? =
        authHeader
            .takeIf { it.startsWith(BEARER_PREFIX) }
            ?.removePrefix(BEARER_PREFIX)

    private fun isValidToken(token: String): Boolean =
        try {
            Jwts
                .parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
            true
        } catch (_: JwtException) {
            false
        }

    private fun unauthorized(exchange: ServerWebExchange): Mono<Void> {
        exchange.response.statusCode = HttpStatus.UNAUTHORIZED
        return exchange.response.setComplete()
    }

    override fun getOrder(): Int = -100
}
