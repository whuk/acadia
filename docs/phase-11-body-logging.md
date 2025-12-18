# Phase 11: 요청/응답 바디 로깅

## 목표
요청 및 응답 바디의 구조화된 로깅 구현 (설정 기반 활성화, 크기 제한, 민감 정보 마스킹)

## PRD 요구사항
- 요청/응답 바디 JSON 로깅
- 설정 기반 활성화/비활성화 (include-body)
- 최대 바디 크기 제한 (max-body-size)
- 민감한 필드 마스킹 (password, token 등)
- multipart/form-data 요청 제외
- 응답 헤더 로깅

## 테스트 목록

### 11.1 요청 바디가 JSON 형식으로 로그에 기록된다
```kotlin
@Test
fun `요청 바디가 JSON 형식으로 로그에 기록된다`(output: CapturedOutput) {
    val requestBody = """{"username": "testuser", "email": "test@example.com"}"""

    wireMock.stubFor(
        post(urlPathMatching("/users"))
            .willReturn(ok().withBody("""{"id": 1}""")),
    )

    webTestClient
        .post()
        .uri("/api/users")
        .header(HttpHeaders.AUTHORIZATION, JwtTestSupport.validAuthHeader())
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(requestBody)
        .exchange()
        .expectStatus()
        .isOk

    val logOutput = output.toString()

    assertThat(logOutput).contains("\"type\":\"REQUEST\"")
    assertThat(logOutput).contains("\"body\":")
    assertThat(logOutput).contains("testuser")
    assertThat(logOutput).contains("test@example.com")
}
```

### 11.2 응답 바디가 JSON 형식으로 로그에 기록된다
```kotlin
@Test
fun `응답 바디가 JSON 형식으로 로그에 기록된다`(output: CapturedOutput) {
    val responseBody = """{"id": 123, "status": "created"}"""

    wireMock.stubFor(
        post(urlPathMatching("/users"))
            .willReturn(
                ok()
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody(responseBody),
            ),
    )

    webTestClient
        .post()
        .uri("/api/users")
        .header(HttpHeaders.AUTHORIZATION, JwtTestSupport.validAuthHeader())
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("""{"username": "test"}""")
        .exchange()
        .expectStatus()
        .isOk

    await untilAsserted {
        val logOutput = output.toString()
        assertThat(logOutput).contains("\"type\":\"RESPONSE\"")
        assertThat(logOutput).contains("\"body\":")
        assertThat(logOutput).contains("123")
        assertThat(logOutput).contains("created")
    }
}
```

### 11.3 바디 로깅은 설정으로 활성화/비활성화할 수 있다
```kotlin
@Test
fun `바디 로깅이 비활성화되면 요청 바디가 로그에 포함되지 않는다`(output: CapturedOutput) {
    // gateway.logging.include-body = false 설정
    val requestBody = """{"username": "testuser", "secret": "password123"}"""

    webTestClient
        .post()
        .uri("/api/users")
        .header(HttpHeaders.AUTHORIZATION, JwtTestSupport.validAuthHeader())
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(requestBody)
        .exchange()
        .expectStatus()
        .isOk

    await untilAsserted {
        val logOutput = output.toString()
        assertThat(logOutput).contains("\"type\":\"REQUEST\"")
        assertThat(logOutput).doesNotContain("testuser")
        assertThat(logOutput).doesNotContain("password123")
    }
}
```

### 11.4 바디 크기가 설정된 최대 크기를 초과하면 잘라서 기록된다
```kotlin
@Test
fun `요청 바디 크기가 최대 크기를 초과하면 잘라서 기록된다`(output: CapturedOutput) {
    // gateway.logging.max-body-size = 50 설정
    val requestBody = """{"username": "verylongusername", "email": "verylongemail@example.com", "extra": "data"}"""

    webTestClient
        .post()
        .uri("/api/users")
        .header(HttpHeaders.AUTHORIZATION, JwtTestSupport.validAuthHeader())
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(requestBody)
        .exchange()
        .expectStatus()
        .isOk

    val logOutput = output.toString()

    assertThat(logOutput).contains("\"type\":\"REQUEST\"")
    assertThat(logOutput).contains("\"body\":")
    assertThat(logOutput).contains("...[TRUNCATED]")
    assertThat(logOutput).doesNotContain("extra")
}
```

### 11.5 민감한 필드는 마스킹 처리된다
```kotlin
@Test
fun `민감한 필드는 마스킹 처리된다`(output: CapturedOutput) {
    val requestBody = """{"username": "testuser", "password": "secret123", "token": "abc-token-xyz"}"""

    wireMock.stubFor(
        post(urlPathMatching("/users"))
            .willReturn(
                ok()
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody("""{"id": 1, "accessToken": "jwt-token-value", "refreshToken": "refresh-value"}"""),
            ),
    )

    webTestClient
        .post()
        .uri("/api/users")
        .header(HttpHeaders.AUTHORIZATION, JwtTestSupport.validAuthHeader())
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(requestBody)
        .exchange()
        .expectStatus()
        .isOk

    await untilAsserted {
        val logOutput = output.toString()

        // 요청 바디 마스킹 확인
        assertThat(logOutput).contains("testuser")
        assertThat(logOutput).doesNotContain("secret123")
        assertThat(logOutput).doesNotContain("abc-token-xyz")
        assertThat(logOutput).contains("***MASKED***")

        // 응답 바디 마스킹 확인
        assertThat(logOutput).doesNotContain("jwt-token-value")
        assertThat(logOutput).doesNotContain("refresh-value")
    }
}
```

### 11.6 multipart/form-data 요청은 바디 로깅에서 제외된다
```kotlin
@Test
fun `multipart form-data 요청은 바디 로깅에서 제외된다`(output: CapturedOutput) {
    val multipartBodyBuilder = MultipartBodyBuilder()
    multipartBodyBuilder.part("file", ByteArrayResource("test file content".toByteArray()))
    multipartBodyBuilder.part("description", "Test file upload")

    webTestClient
        .post()
        .uri("/api/users/upload")
        .header(HttpHeaders.AUTHORIZATION, JwtTestSupport.validAuthHeader())
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .bodyValue(multipartBodyBuilder.build())
        .exchange()
        .expectStatus()
        .isOk

    val logOutput = output.toString()

    assertThat(logOutput).contains("\"type\":\"REQUEST\"")
    assertThat(logOutput).doesNotContain("test file content")
    assertThat(logOutput).doesNotContain("Test file upload")
}
```

### 11.7 응답 헤더가 로그에 기록된다
```kotlin
@Test
fun `응답 헤더가 로그에 기록된다`(output: CapturedOutput) {
    // gateway.logging.include-headers = true 설정
    wireMock.stubFor(
        get(urlPathMatching("/users/.*"))
            .willReturn(
                ok()
                    .withBody("""{"id": 1}""")
                    .withHeader("X-Custom-Header", "custom-value")
                    .withHeader("Content-Type", "application/json"),
            ),
    )

    webTestClient
        .get()
        .uri("/api/users/1")
        .header(HttpHeaders.AUTHORIZATION, JwtTestSupport.validAuthHeader())
        .exchange()
        .expectStatus()
        .isOk

    val logOutput = output.toString()

    assertThat(logOutput).contains("\"type\":\"RESPONSE\"")
    assertThat(logOutput).contains("\"headers\":")
    assertThat(logOutput).contains("X-Custom-Header")
    assertThat(logOutput).contains("custom-value")
}
```

## 구현 가이드

### application.yml
```yaml
gateway:
  logging:
    enabled: true
    include-body: true
    include-headers: true
    include-query-params: true
    max-body-size: 10000
    storage: NONE  # NONE, FILE, DB
    file:
      path: ./logs/gateway-requests.log
```

### LoggingProperties.kt
```kotlin
@ConfigurationProperties(prefix = "gateway.logging")
data class LoggingProperties(
    val enabled: Boolean = false,
    val storage: StorageType = StorageType.NONE,
    val includeHeaders: Boolean = false,
    val includeQueryParams: Boolean = true,
    val includeBody: Boolean = false,
    val maxBodySize: Int = 10000,
    val file: FileProperties = FileProperties(),
) {
    enum class StorageType {
        NONE,
        FILE,
        DB,
    }

    data class FileProperties(
        val path: String = "./logs/gateway-requests.log",
    )
}
```

### SensitiveFieldMasker.kt
```kotlin
object SensitiveFieldMasker {
    private val objectMapper = ObjectMapper().findAndRegisterModules()

    private val sensitiveFieldPatterns = listOf(
        Regex(".*password.*", RegexOption.IGNORE_CASE),
        Regex(".*token.*", RegexOption.IGNORE_CASE),
        Regex(".*secret.*", RegexOption.IGNORE_CASE),
        Regex(".*credential.*", RegexOption.IGNORE_CASE),
        Regex(".*apikey.*", RegexOption.IGNORE_CASE),
        Regex(".*api_key.*", RegexOption.IGNORE_CASE),
    )

    private const val MASK_VALUE = "***MASKED***"

    fun mask(body: String?): String? {
        if (body.isNullOrBlank()) return body

        return try {
            val jsonNode = objectMapper.readTree(body)
            maskNode(jsonNode)
            objectMapper.writeValueAsString(jsonNode)
        } catch (e: Exception) {
            body
        }
    }

    private fun maskNode(node: JsonNode) {
        when {
            node.isObject -> {
                val objectNode = node as ObjectNode
                val fieldNames = objectNode.fieldNames().asSequence().toList()
                for (fieldName in fieldNames) {
                    if (isSensitiveField(fieldName)) {
                        objectNode.put(fieldName, MASK_VALUE)
                    } else {
                        maskNode(objectNode.get(fieldName))
                    }
                }
            }
            node.isArray -> node.forEach { maskNode(it) }
        }
    }

    private fun isSensitiveField(fieldName: String): Boolean =
        sensitiveFieldPatterns.any { it.matches(fieldName) }
}
```

### RequestLoggingFilter.kt (바디 로깅 부분)
```kotlin
val body = if (loggingProperties.includeBody) {
    val contentType = request.headers.contentType
    val isMultipart = contentType?.includes(MediaType.MULTIPART_FORM_DATA) == true

    if (isMultipart) {
        null  // multipart 요청은 바디 로깅 제외
    } else {
        exchange.getAttribute<String>(CACHED_REQUEST_BODY_ATTR)
            ?.let { SensitiveFieldMasker.mask(it) }
            ?.truncateIfNeeded(loggingProperties.maxBodySize)
    }
} else {
    null
}
```

### ResponseLoggingFilter.kt (헤더 및 바디 로깅 부분)
```kotlin
private fun logResponse(exchange: ServerWebExchange, startTime: Instant, body: String?) {
    val truncatedBody = body
        ?.let { SensitiveFieldMasker.mask(it) }
        ?.truncateIfNeeded(loggingProperties.maxBodySize)

    val responseHeaders = if (loggingProperties.includeHeaders) {
        formatHeaders(exchange.response.headers)
    } else {
        null
    }

    val logEntry = LogEntry.response(
        timestamp = Instant.now(),
        requestId = requestId,
        statusCode = exchange.response.statusCode?.value() ?: 0,
        duration = duration,
        headers = responseHeaders,
        body = truncatedBody,
    )

    logStorage.store(logEntry)
}

private fun formatHeaders(headers: HttpHeaders): String =
    objectMapper.writeValueAsString(headers.toSingleValueMap())
```

### 바디 크기 제한 확장 함수
```kotlin
private fun String.truncateIfNeeded(maxSize: Int): String =
    if (length > maxSize) {
        substring(0, maxSize) + "...[TRUNCATED]"
    } else {
        this
    }
```

## 로그 출력 예시

### 요청 로그
```json
{
  "type": "REQUEST",
  "timestamp": "2025-12-18T05:18:18.643704Z",
  "requestId": "5442daa1-406e-4134-ba64-1af885e76a8b",
  "method": "POST",
  "path": "/api/users",
  "body": "{\"username\":\"testuser\",\"password\":\"***MASKED***\"}"
}
```

### 응답 로그
```json
{
  "type": "RESPONSE",
  "timestamp": "2025-12-18T05:18:18.690112Z",
  "requestId": "5442daa1-406e-4134-ba64-1af885e76a8b",
  "statusCode": 200,
  "duration": 31,
  "headers": "{\"Content-Type\":\"application/json\",\"X-Custom-Header\":\"custom-value\"}",
  "body": "{\"id\":1,\"accessToken\":\"***MASKED***\"}"
}
```

## 완료 조건
- [x] 요청 바디 JSON 로깅
- [x] 응답 바디 JSON 로깅
- [x] include-body 설정으로 활성화/비활성화
- [x] max-body-size 설정으로 크기 제한 및 잘라내기
- [x] 민감한 필드 마스킹 (password, token, secret, credential, apikey)
- [x] multipart/form-data 요청 바디 로깅 제외
- [x] 응답 헤더 로깅 (include-headers 설정)
