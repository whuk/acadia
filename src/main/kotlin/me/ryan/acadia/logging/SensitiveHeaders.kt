package me.ryan.acadia.logging

/**
 * SEC-6: masks sensitive headers in request/response logs.
 * The header is kept (presence is useful) but its value is redacted.
 */
object SensitiveHeaders {
    private val NAMES =
        setOf(
            "authorization",
            "cookie",
            "set-cookie",
            "x-api-key",
            "x-user-id",
            "x-user-roles",
        )

    private const val REDACTED = "***"

    fun mask(headers: Map<String, String>): Map<String, String> =
        headers.mapValues { (key, value) ->
            if (key.lowercase() in NAMES) REDACTED else value
        }
}
