package me.ryan.acadia.filter

import io.jsonwebtoken.Claims
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
        private const val X_USER_ID_HEADER = "X-User-Id"
    }

    override fun filter(
        exchange: ServerWebExchange,
        chain: GatewayFilterChain,
    ): Mono<Void> {
        val authHeader =
            exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)
                ?: return unauthorized(exchange)

        val token = extractToken(authHeader) ?: return unauthorized(exchange)

        val claims = parseToken(token) ?: return unauthorized(exchange)

        val mutatedExchange =
            exchange
                .mutate()
                .request { request ->
                    request.header(X_USER_ID_HEADER, claims.subject)
                }.build()

        return chain.filter(mutatedExchange)
    }

    private fun extractToken(authHeader: String): String? =
        authHeader
            .takeIf { it.startsWith(BEARER_PREFIX) }
            ?.removePrefix(BEARER_PREFIX)

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
