package me.ryan.acadia

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class AcadiaApplication

fun main(args: Array<String>) {
    runApplication<AcadiaApplication>(*args)
}
