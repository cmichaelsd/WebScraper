package org.example.util.interfaces

interface RobotsFetcher {
    suspend fun fetchRobots(domain: String): String?
}