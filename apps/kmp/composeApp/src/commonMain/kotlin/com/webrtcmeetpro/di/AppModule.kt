package com.webrtcmeetpro.di

import com.webrtcmeetpro.feature.auth.VerifyEmailViewModel
import com.webrtcmeetpro.feature.main.PlanExpiryViewModel
import com.webrtcmeetpro.AppViewModel
import com.webrtcmeetpro.core.data.AuthRepository
import com.webrtcmeetpro.core.data.BadgeRepository
import com.webrtcmeetpro.core.data.BillingRepository
import com.webrtcmeetpro.core.data.ChatRepository
import com.webrtcmeetpro.core.data.MeetingRepository
import com.webrtcmeetpro.core.data.NotificationRepository
import com.webrtcmeetpro.core.data.RealtimeRepository
import com.webrtcmeetpro.core.data.SessionStore
import com.webrtcmeetpro.core.data.SettingsStore
import com.webrtcmeetpro.core.data.TeamRepository
import com.webrtcmeetpro.core.data.UserRepository
import com.webrtcmeetpro.core.network.LimeetApi
import com.webrtcmeetpro.core.network.StompClient
import com.webrtcmeetpro.core.network.TokenStorage
import com.webrtcmeetpro.core.network.createHttpClient
import com.webrtcmeetpro.feature.auth.LoginViewModel
import com.webrtcmeetpro.feature.auth.SignupViewModel
import com.webrtcmeetpro.feature.chat.ChatListViewModel
import com.webrtcmeetpro.feature.chat.ChatPresence
import com.webrtcmeetpro.feature.chat.ChatRoomViewModel
import com.webrtcmeetpro.feature.chat.ThreadViewModel
import com.webrtcmeetpro.feature.settings.PaymentsViewModel
import com.webrtcmeetpro.feature.chat.NewChatViewModel
import com.webrtcmeetpro.feature.home.HomeViewModel
import com.webrtcmeetpro.feature.meetings.MeetingDetailViewModel
import com.webrtcmeetpro.feature.meetings.MeetingsViewModel
import com.webrtcmeetpro.feature.meetings.ScheduleMeetingViewModel
import com.webrtcmeetpro.feature.notifications.NotificationsViewModel
import com.webrtcmeetpro.feature.settings.SettingsViewModel
import com.webrtcmeetpro.feature.teams.TeamDetailViewModel
import com.webrtcmeetpro.feature.teams.TeamsViewModel
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
    singleOf(::StompClient)
    singleOf(::RealtimeRepository)
    singleOf(::BadgeRepository)
    singleOf(::UserRepository)
    singleOf(::AuthRepository)
    singleOf(::TeamRepository)
    singleOf(::ChatRepository)
    singleOf(::MeetingRepository)
    singleOf(::NotificationRepository)
    singleOf(::BillingRepository)
    singleOf(::ChatPresence)
}

/** 화면별 ViewModel. id 를 받는 화면은 koinViewModel { parametersOf(id) } 로 넘긴다 */
val viewModelModule = module {
    viewModelOf(::AppViewModel)
    viewModelOf(::PlanExpiryViewModel)
    viewModelOf(::LoginViewModel)
    viewModelOf(::SignupViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::ChatListViewModel)
    viewModelOf(::NewChatViewModel)
    viewModelOf(::ChatRoomViewModel)
    viewModelOf(::ThreadViewModel)
    viewModelOf(::MeetingsViewModel)
    viewModelOf(::MeetingDetailViewModel)
    viewModelOf(::ScheduleMeetingViewModel)
    viewModelOf(::TeamsViewModel)
    viewModelOf(::TeamDetailViewModel)
    viewModelOf(::NotificationsViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::PaymentsViewModel)
    viewModelOf(::VerifyEmailViewModel)
}

/** Android Application / Desktop main 에서 한 번 호출. platform 은 서버 주소·저장 경로·알림 같은 플랫폼 객체 */
fun initKoin(platform: Module, extra: KoinApplication.() -> Unit = {}): KoinApplication = startKoin {
    extra()
    modules(platform, dataModule, viewModelModule)
}
