package com.talkroom.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val AdminEmail = "ozgursametaydogan@gmail.com"

fun normalizeChannelName(value: String): String {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return "talkroom-room"
    return trimmed.lowercase().replace(Regex("\\s+"), "_")
}

@Serializable
data class AuthSession(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    val user: SupabaseUser
)

@Serializable
data class SupabaseUser(
    val id: String,
    val email: String? = null
)

@Serializable
data class Profile(
    val id: String,
    val email: String,
    val username: String,
    val bio: String = "",
    val website: String = "",
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class Room(
    val id: String,
    val name: String,
    @SerialName("is_private") val isPrivate: Boolean,
    @SerialName("member_count") val memberCount: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("owner_id") val ownerId: String
)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class SignUpRequest(val email: String, val password: String)

@Serializable
data class RecoverPasswordRequest(val email: String)

@Serializable
data class UpsertProfileRequest(
    val id: String,
    val email: String,
    val username: String,
    val bio: String,
    val website: String,
    @SerialName("avatar_url") val avatarUrl: String? = null
)

@Serializable
data class CreateRoomRequest(val name: String, val password: String? = null)

@Serializable
data class JoinRoomRequest(val password: String? = null)

@Serializable
data class AgoraTokenRequest(
    @SerialName("room_id") val roomId: String,
    @SerialName("channel_name") val channelName: String,
    val uid: Int
)

@Serializable
data class AgoraTokenResponse(
    val token: String,
    @SerialName("app_id") val appId: String,
    @SerialName("channel_name") val channelName: String,
    val uid: Int,
    @SerialName("expires_at_epoch_seconds") val expiresAtEpochSeconds: Long
)

data class AppConfig(
    val supabaseUrl: String,
    val supabaseAnonKey: String,
    val backendBaseUrl: String,
    val agoraAppId: String
) {
    fun validate() {
        require(supabaseUrl.startsWith("https://")) { "SUPABASE_URL eksik veya geçersiz." }
        require(supabaseAnonKey.length > 20) { "SUPABASE_ANON_KEY eksik." }
        require(backendBaseUrl.startsWith("http")) { "BACKEND_BASE_URL eksik veya geçersiz." }
    }
}
