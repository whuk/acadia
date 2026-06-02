package me.ryan.acadia.config

import org.springframework.boot.context.properties.ConfigurationProperties

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
    }

    data class FileProperties(
        val path: String = "./logs/gateway-requests.log",
    )
}
