package org.example.di

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import org.example.services.CrawlerService
import org.example.services.RobotsService
import org.koin.dsl.module

val appModule = module {
    single {
        HttpClient(CIO) {
            followRedirects = true
            expectSuccess = false
        }
    }

    single {
        RobotsService(get())
    }

    single {
        CrawlerService(
            robotsService = get(),
            client = get()
        )
    }
}