package org.example.models

import java.util.UUID

data class Job(
    val id: UUID,
    val seedUrls: List<String>,
    val maxDepth: Int
)
