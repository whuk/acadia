package me.ryan.acadia.config

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.web.servlet.DispatcherServlet
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
class ServletServerTest {
    @Autowired
    lateinit var applicationContext: ApplicationContext

    @Test
    fun `애플리케이션이 Servlet 환경으로 동작한다`() {
        // Spring MVC의 DispatcherServlet이 빈으로 등록되어 있으면 Servlet 환경
        val dispatcherServlet = applicationContext.getBean(DispatcherServlet::class.java)
        assertNotNull(dispatcherServlet)
    }

    @Test
    fun `web-application-type이 servlet으로 설정되어 있다`() {
        val webAppType = applicationContext.environment.getProperty("spring.main.web-application-type")
        assertTrue(webAppType == "servlet")
    }
}
