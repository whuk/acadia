package me.ryan.acadia.logging

interface LogStorage {
    fun store(entry: RequestLogEntry)
}
