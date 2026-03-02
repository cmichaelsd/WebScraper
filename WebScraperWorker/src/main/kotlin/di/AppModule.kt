package org.example.di

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import org.example.db.Database
import org.example.db.JobRepository
import org.example.db.PageRepository
import org.example.services.CrawlerService
import org.example.services.JobService
import org.example.services.RobotsService
import org.koin.dsl.module
import javax.sql.DataSource

val appModule = module {
    single {
        HttpClient(CIO) {
            followRedirects = true
            expectSuccess = false
        }
    }

    single<DataSource> {
        Database.dataSource
    }

    single {
        JobRepository(get())
    }

    single {
        PageRepository(get())
    }

    single {
        JobService(
            jobRepository = get(),
            pageRepository = get(),
            crawlerService = get()
        )
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