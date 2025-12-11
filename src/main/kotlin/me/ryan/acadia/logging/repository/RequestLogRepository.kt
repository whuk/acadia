package me.ryan.acadia.logging.repository

import me.ryan.acadia.logging.entity.RequestLogEntry
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface RequestLogRepository : JpaRepository<RequestLogEntry, Long>
