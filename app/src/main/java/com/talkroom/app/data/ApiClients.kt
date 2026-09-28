package com.talkroom.app.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import java.io.IOException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json

class ApiException(message: String, cause: Throwable? = null, val status: Int? = null, val code: String? = null) : Exception(message, cause)

internal val apiJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

fun createHttpClient(): HttpClient {
    return HttpClient(Android) {
        expectSuccess = true
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
        }
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
        }.body<AuthSession>().validated()
    }

    suspend fun signUp(email: String, password: String): SignUpResult = request {
        val response = http.post("$base/auth/v1/signup") {
            supabaseHeaders()
            setBody(SignUpRequest(email.trim().lowercase(), password))
        }.body<JsonObject>()
        if (!response["access_token"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()) {
            SignUpResult.SignedIn(apiJson.decodeFromJsonElement<AuthSession>(response).validated())
        } else {
            // GoTrue returns a user (not a session) when email confirmation is enabled.
            val user = (response["user"] as? JsonObject) ?: response
            if (user["id"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()) {
                throw ApiException("Kayıt yanıtı geçersiz. Lütfen daha sonra tekrar deneyin.")
            }
            SignUpResult.ConfirmationRequired
        }
    }

    suspend fun refreshSession(refreshToken: String): AuthSession = request {
        http.post("$base/auth/v1/token?grant_type=refresh_token") {
            supabaseHeaders()
            setBody(RefreshTokenRequest(refreshToken))
        }.body<AuthSession>().validated()
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
            http.post("$base/auth/v1/logout?scope=local") {
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
                username = user.email?.substringBefore('@')?.take(50)?.takeIf { it.length >= 2 } ?: "Kullanıcı",
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
        if (accessToken != null) bearerAuth(accessToken)
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

private fun AuthSession.validated(): AuthSession {
    if (accessToken.isBlank() || refreshToken.isNullOrBlank() || user.id.isBlank()) {
        throw ApiException("Sunucudan geçerli oturum alınamadı. Lütfen yeniden giriş yapın.")
    }
    val expiry = expiresAt ?: expiresIn?.takeIf { it > 0 }?.let { System.currentTimeMillis() / 1000 + it }
        ?: throw ApiException("Sunucunun oturum süresi yanıtı geçersiz.")
    return copy(expiresAt = expiry)
}

private suspend fun <T> request(block: suspend () -> T): T {
    return try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: ApiException) {
        throw error
    } catch (error: ResponseException) {
        val payload = try { apiJson.parseToJsonElement(error.response.bodyAsText()) as? JsonObject }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        fun field(name: String) = (payload?.get(name) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        val message = field("msg") ?: field("message") ?: field("error_description") ?: field("error")
        val status = error.response.status.value
        throw ApiException(message ?: when (status) {
            401 -> "Oturum doğrulanamadı. Lütfen yeniden giriş yapın."
            403 -> "Bu işlem için yetkiniz yok."
            429 -> "Çok fazla istek gönderildi. Lütfen biraz bekleyin."
            in 500..599 -> "Sunucuya şu anda ulaşılamıyor. Lütfen tekrar deneyin."
            else -> "İstek tamamlanamadı (HTTP $status)."
        }, error, status, field("error_code") ?: field("code"))
    } catch (error: SerializationException) {
        throw ApiException("Sunucu yanıtı okunamadı. Lütfen daha sonra tekrar deneyin.", error)
    } catch (error: IOException) {
        throw ApiException("Bağlantı kurulamadı. İnternet bağlantınızı kontrol edin.", error)
    } catch (error: Exception) {
        throw ApiException("İşlem tamamlanamadı. Lütfen tekrar deneyin.", error)
    }
}
