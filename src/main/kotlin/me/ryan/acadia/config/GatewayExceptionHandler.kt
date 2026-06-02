package me.ryan.acadia.config

import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import org.springframework.cloud.gateway.server.mvc.filter.CircuitBreakerFilterFunctions.CircuitBreakerStatusCodeException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Unified gateway error responses (QUAL-3) using RFC 9457 Problem Details.
 *
 * - Circuit breaker open  -> 503
 * - Backend 5xx (captured by circuit breaker status codes) -> 502
 */
@RestControllerAdvice
class GatewayExceptionHandler {
    @ExceptionHandler(CallNotPermittedException::class)
    fun handleCircuitOpen(e: CallNotPermittedException): ResponseEntity<ProblemDetail> =
        problem(HttpStatus.SERVICE_UNAVAILABLE, "Service temporarily unavailable (circuit open)")

    @ExceptionHandler(CircuitBreakerStatusCodeException::class)
    fun handleBackendError(e: CircuitBreakerStatusCodeException): ResponseEntity<ProblemDetail> =
        problem(HttpStatus.BAD_GATEWAY, "Bad gateway")

    private fun problem(
        status: HttpStatus,
        detail: String,
    ): ResponseEntity<ProblemDetail> =
        ResponseEntity
            .status(status)
            .body(ProblemDetail.forStatusAndDetail(status, detail))
}
