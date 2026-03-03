package org.webscraper.models

import java.util.UUID

data class Page(
    val id: UUID,
    val url: String,
    val depth: Int
)
