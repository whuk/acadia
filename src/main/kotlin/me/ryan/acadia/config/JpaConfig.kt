package me.ryan.acadia.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

/**
 * JPA configuration that is only enabled when gateway.logging.storage=db.
 * This allows the application to run without a database when storage is set to 'none' or 'file'.
 */
@Configuration
@ConditionalOnProperty(prefix = "gateway.logging", name = ["storage"], havingValue = "db")
@Import(
    DataSourceAutoConfiguration::class,
    HibernateJpaAutoConfiguration::class,
    DataJpaRepositoriesAutoConfiguration::class,
)
class JpaConfig
