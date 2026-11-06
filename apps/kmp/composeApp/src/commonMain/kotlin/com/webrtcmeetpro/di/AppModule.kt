package com.webrtcmeetpro.di

import com.webrtcmeetpro.core.data.AuthRepository
import com.webrtcmeetpro.core.data.MeetingRepository
import com.webrtcmeetpro.core.data.SessionStore
import com.webrtcmeetpro.core.data.SettingsStore
import com.webrtcmeetpro.core.data.TeamRepository
import com.webrtcmeetpro.core.data.UserRepository
import com.webrtcmeetpro.core.network.LimeetApi
import com.webrtcmeetpro.core.network.TokenStorage
import com.webrtcmeetpro.core.network.createHttpClient
import com.webrtcmeetpro.feature.auth.LoginViewModel
import com.webrtcmeetpro.feature.home.HomeViewModel
import com.webrtcmeetpro.feature.meetings.MeetingsViewModel
import com.webrtcmeetpro.feature.settings.SettingsViewModel
import com.webrtcmeetpro.feature.teams.TeamsViewModel
import com.webrtcmeetpro.AppViewModel
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

/** 모든 플랫폼 공통 객체. 모두 single(싱글톤) — 앱 전체에서 하나씩만 */
val dataModule = module {
    singleOf(::SessionStore) bind TokenStorage::class
    singleOf(::SettingsStore)
    single { createHttpClient(get(), get()) }
    singleOf(::LimeetApi)
    singleOf(::AuthRepository)
    singleOf(::UserRepository)
    singleOf(::TeamRepository)
    singleOf(::MeetingRepository)
}

val viewModelModule = module {
    viewModelOf(::AppViewModel)
    viewModelOf(::LoginViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::TeamsViewModel)
    viewModelOf(::MeetingsViewModel)
    viewModelOf(::SettingsViewModel)
}

/** Android Application / Desktop main 에서 한 번 호출. platform 은 서버 주소·저장 경로 같은 플랫폼 값 */
fun initKoin(platform: Module, extra: KoinApplication.() -> Unit = {}): KoinApplication = startKoin {
    extra()
    modules(platform, dataModule, viewModelModule)
}
