package com.talkroom.app

import android.app.Application
import com.talkroom.app.data.AppConfig
import com.talkroom.app.data.SupabaseApi
import com.talkroom.app.data.TalkRoomBackendApi
import com.talkroom.app.data.createHttpClient

class TalkRoomApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val config = AppConfig(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY,
            backendBaseUrl = BuildConfig.BACKEND_BASE_URL,
            agoraAppId = BuildConfig.AGORA_APP_ID
        )
        val http = createHttpClient()
        container = AppContainer(
            config = config,
            supabaseApi = SupabaseApi(config, http),
            backendApi = TalkRoomBackendApi(config, http)
        )
    }
}

data class AppContainer(
    val config: AppConfig,
    val supabaseApi: SupabaseApi,
    val backendApi: TalkRoomBackendApi
)
