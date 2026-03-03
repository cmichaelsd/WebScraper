package org.example.di

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import org.webscraper.db.Database
import org.webscraper.db.JobRepository
import org.webscraper.db.PageRepository
import org.webscraper.services.CrawlerService
import org.webscraper.services.JobService
import org.webscraper.services.RobotsService
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