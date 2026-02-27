package org.example.util

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import org.example.util.interfaces.RobotsFetcher

class KtorRobotsFetcher(
    private val httpClient: HttpClient
) : RobotsFetcher {
    override suspend fun fetchRobots(domain: String): String? {
        return try {
            httpClient.get("https://$domain/robots.txt")
                .bodyAsText()
        } catch (e: Exception) {
            null
        }
    }
}