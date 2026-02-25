package org.example.util

import kotlinx.coroutines.sync.Mutex

data class DomainThrottle(
    val mutex: Mutex = Mutex(),
    var lastRequestTime: Long = 0L
)