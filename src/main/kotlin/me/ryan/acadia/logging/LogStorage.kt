package me.ryan.acadia.logging

import me.ryan.acadia.logging.entity.RequestLogEntry

interface LogStorage {
    fun store(entry: RequestLogEntry)
}
