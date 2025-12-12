package me.ryan.acadia.logging

import me.ryan.acadia.logging.entity.LogEntry

interface LogStorage {
    fun store(entry: LogEntry)
}
