package org.example.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

object Database {
    private val jdbcUrlValue = System.getenv("JDBC_URL") ?: error("JDBC_URL not set")
    private val usernameValue = System.getenv("DB_USER") ?: error("DB_USER not set")
    private val passwordValue = System.getenv("DB_PASSWORD") ?: error("DB_PASSWORD not set")
    private val hikariConfig = HikariConfig().apply {
        jdbcUrl = jdbcUrlValue
        username = usernameValue
        password = passwordValue
        maximumPoolSize = 5
    }

    val dataSource = HikariDataSource(hikariConfig)
}