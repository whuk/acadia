package me.ryan.acadia

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.web.reactive.DispatcherHandler
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
class NettyServerTest {

    @Autowired
    lateinit var applicationContext: ApplicationContext

    @Test
    fun `애플리케이션이 Reactive 환경으로 동작한다`() {
        // WebFlux의 DispatcherHandler가 빈으로 등록되어 있으면 Reactive 환경
        val dispatcherHandler = applicationContext.getBean(DispatcherHandler::class.java)
        assertNotNull(dispatcherHandler)
    }

    @Test
    fun `web-application-type이 reactive로 설정되어 있다`() {
        val webAppType = applicationContext.environment.getProperty("spring.main.web-application-type")
        assertTrue(webAppType == "reactive")
    }
}
