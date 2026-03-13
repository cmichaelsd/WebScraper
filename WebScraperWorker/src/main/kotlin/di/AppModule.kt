package org.example.di

import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import org.koin.dsl.module
import org.koin.dsl.onClose
import org.webscraper.db.Database
import org.webscraper.db.JobRepository
import org.webscraper.db.PageRepository
import org.webscraper.services.CrawlerService
import org.webscraper.services.JobService
import org.webscraper.services.RobotsService
import org.webscraper.util.RulesCache
import javax.sql.DataSource

val appModule =
    module {
        single {
            HttpClient(CIO) {
                followRedirects = true
                expectSuccess = false
            }
        } onClose { it?.close() }

        single<DataSource> {
            Database.dataSource
        } onClose { (it as? HikariDataSource)?.close() }

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
                crawlerService = get(),
            )
        }

        single {
            RulesCache()
        }

        single {
            RobotsService(
                client = get(),
                rulesCache = get(),
            )
        }

        single {
            CrawlerService(
                robotsService = get(),
                client = get(),
            )
        }
    }
