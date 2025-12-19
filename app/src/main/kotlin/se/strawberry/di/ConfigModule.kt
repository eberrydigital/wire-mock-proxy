package se.strawberry.di

import org.koin.dsl.module
import se.strawberry.config.AppConfig
import se.strawberry.config.AppConfigLoader

val configModule = module {
    single<AppConfig> { AppConfigLoader.load() }
}