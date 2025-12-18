package me.ryan.acadia.logging

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode

object SensitiveFieldMasker {
    private val objectMapper = ObjectMapper().findAndRegisterModules()

    private val sensitiveFieldPatterns =
        listOf(
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
            // If not valid JSON, return as-is
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
            node.isArray -> {
                node.forEach { maskNode(it) }
            }
        }
    }

    private fun isSensitiveField(fieldName: String): Boolean = sensitiveFieldPatterns.any { it.matches(fieldName) }
}
