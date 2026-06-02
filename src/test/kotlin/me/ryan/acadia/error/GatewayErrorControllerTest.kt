package me.ryan.acadia.error

import jakarta.servlet.RequestDispatcher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import java.net.URI

class GatewayErrorControllerTest {
    private val controller = GatewayErrorController()

    @Test
    fun `에러 속성을 ProblemDetail로 매핑한다`() {
        val request =
            MockHttpServletRequest().apply {
                setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 502)
                setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/users/1")
            }

        val response = controller.handleError(request)

        assertThat(response.statusCode.value()).isEqualTo(502)
        assertThat(response.headers.contentType).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON)
        val problem = response.body!!
        assertThat(problem.status).isEqualTo(502)
        assertThat(problem.title).isEqualTo("Bad Gateway")
        assertThat(problem.instance).isEqualTo(URI.create("/api/users/1"))
    }

    @Test
    fun `상태 코드 속성이 없으면 500으로 매핑한다`() {
        val response = controller.handleError(MockHttpServletRequest())

        assertThat(response.statusCode.value()).isEqualTo(500)
        assertThat(response.body!!.status).isEqualTo(500)
    }
}
