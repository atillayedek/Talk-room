package com.talkroom.app.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause)

fun createHttpClient(): HttpClient {
    return HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            })
        }
    }
}

class SupabaseApi(
    private val config: AppConfig,
    private val http: HttpClient
) {
    private val base = config.supabaseUrl.trimEnd('/')

    suspend fun signIn(email: String, password: String): AuthSession = request {
        http.post("$base/auth/v1/token?grant_type=password") {
            supabaseHeaders()
            setBody(LoginRequest(email.trim().lowercase(), password))
        }.body()
    }

    suspend fun signUp(email: String, password: String): AuthSession = request {
        http.post("$base/auth/v1/signup") {
            supabaseHeaders()
            setBody(SignUpRequest(email.trim().lowercase(), password))
        }.body()
    }

    suspend fun recoverPassword(email: String) {
        return request {
            http.post("$base/auth/v1/recover") {
                supabaseHeaders()
                setBody(RecoverPasswordRequest(email.trim().lowercase()))
            }.let { }
        }
    }

    suspend fun signOut(accessToken: String) {
        return request {
            http.post("$base/auth/v1/logout") {
                supabaseHeaders(accessToken)
            }.let { }
        }
    }

    suspend fun getProfile(accessToken: String, user: SupabaseUser): Profile {
        val profiles: List<Profile> = request {
            http.get("$base/rest/v1/profiles?id=eq.${user.id}&select=*") {
                supabaseHeaders(accessToken)
            }.body()
        }
        return profiles.firstOrNull() ?: upsertProfile(
            accessToken = accessToken,
            profile = UpsertProfileRequest(
                id = user.id,
                email = user.email.orEmpty(),
                username = user.email?.substringBefore('@') ?: "Kullanıcı",
                bio = "",
                website = ""
            )
        )
    }

    suspend fun upsertProfile(accessToken: String, profile: UpsertProfileRequest): Profile {
        val saved: List<Profile> = request {
            http.post("$base/rest/v1/profiles?on_conflict=id&select=*") {
                supabaseHeaders(accessToken)
                header("Prefer", "resolution=merge-duplicates,return=representation")
                setBody(profile)
            }.body()
        }
        return saved.first()
    }

    private fun io.ktor.client.request.HttpRequestBuilder.supabaseHeaders(accessToken: String? = null) {
        contentType(ContentType.Application.Json)
        header("apikey", config.supabaseAnonKey)
        header(HttpHeaders.Authorization, "Bearer ${accessToken ?: config.supabaseAnonKey}")
    }
}

class TalkRoomBackendApi(
    config: AppConfig,
    private val http: HttpClient
) {
    private val base = config.backendBaseUrl.trimEnd('/')

    suspend fun rooms(accessToken: String): List<Room> = request {
        http.get("$base/rooms") {
            bearerAuth(accessToken)
        }.body()
    }

    suspend fun createRoom(accessToken: String, name: String, password: String?): Room = request {
        http.post("$base/rooms") {
            bearerAuth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(CreateRoomRequest(name, password?.takeIf { it.isNotBlank() }))
        }.body()
    }

    suspend fun joinRoom(accessToken: String, roomId: String, password: String?): Room = request {
        http.post("$base/rooms/$roomId/join") {
            bearerAuth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(JoinRoomRequest(password?.takeIf { it.isNotBlank() }))
        }.body()
    }

    suspend fun leaveRoom(accessToken: String, roomId: String) {
        return request {
            http.delete("$base/rooms/$roomId/join") {
                bearerAuth(accessToken)
            }.let { }
        }
    }

    suspend fun agoraToken(accessToken: String, roomId: String, channelName: String, uid: Int): AgoraTokenResponse = request {
        http.post("$base/agora/token") {
            bearerAuth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(AgoraTokenRequest(roomId, channelName, uid))
        }.body()
    }
}

private suspend fun <T> request(block: suspend () -> T): T {
    return try {
        block()
    } catch (error: ClientRequestException) {
        throw ApiException("İstek reddedildi: ${error.response.status.value}", error)
    } catch (error: ServerResponseException) {
        throw ApiException("Sunucu hatası: ${error.response.status.value}", error)
    } catch (error: Throwable) {
        throw ApiException(error.message ?: "Beklenmeyen bağlantı hatası.", error)
    }
}
