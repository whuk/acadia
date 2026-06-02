package me.ryan.acadia.error

import jakarta.servlet.RequestDispatcher
import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.webmvc.error.ErrorController
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

/**
 * B3: unify the servlet error-dispatch body to RFC 9457 ProblemDetail (application/problem+json),
 * replacing Spring's default error JSON. Covers proxy 5xx (mapped to 502 by BackendErrorFilter),
 * undefined routes (404), and other gateway-originated errors that reach `/error`.
 *
 * Providing an ErrorController bean disables Spring Boot's BasicErrorController. The detail is kept
 * generic (status reason phrase) so no internal information is exposed.
 */
@RestController
class GatewayErrorController : ErrorController {
    @RequestMapping("\${server.error.path:\${error.path:/error}}")
    fun handleError(request: HttpServletRequest): ResponseEntity<ProblemDetail> {
        val statusCode = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) as? Int
        val status = statusCode?.let { HttpStatus.resolve(it) } ?: HttpStatus.INTERNAL_SERVER_ERROR

        val problem = ProblemDetail.forStatus(status)
        (request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI) as? String)?.let { uri ->
            problem.instance = URI.create(uri)
        }

        return ResponseEntity
            .status(status)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problem)
    }
}
