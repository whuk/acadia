package me.ryan.acadia.logging

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface RequestLogRepository : JpaRepository<RequestLogEntry, Long>
