package com.talkroom.server

import at.favre.lib.crypto.bcrypt.BCrypt
import io.agora.media.RtcTokenBuilder2
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        module()
    }.start(wait = true)
}

fun Application.module() {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    val config = ServerConfig.fromEnvironment()
    val client = HttpClient(Java) {
        install(ClientContentNegotiation) { json(json) }
    }
    val supabase = SupabaseService(config, client)
    val agora = AgoraTokenService(config)

    install(ContentNegotiation) { json(json) }

    routing {
        get("/health") {
            call.respond(mapOf("ok" to true, "service" to "talkroom-kotlin"))
        }

        get("/rooms") {
            val user = call.requireUser(supabase) ?: return@get
            call.respond(supabase.roomsFor(user.id))
        }

        post("/rooms") {
            val user = call.requireUser(supabase) ?: return@post
            val request = call.receive<CreateRoomRequest>()
            val name = request.name.trim()
            if (name.length !in 2..80) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Oda adı 2-80 karakter olmalı."))
                return@post
            }
            val passwordHash = request.password?.trim()?.takeIf { it.isNotEmpty() }?.let {
                if (it.length < 4) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Oda şifresi en az 4 karakter olmalı."))
                    return@post
                }
                BCrypt.withDefaults().hashToString(12, it.toCharArray())
            }
            call.respond(supabase.createRoom(user.id, name, passwordHash))
        }

        post("/rooms/{id}/join") {
            val user = call.requireUser(supabase) ?: return@post
            val roomId = call.parameters["id"].orEmpty()
            val request = call.receive<JoinRoomRequest>()
            val room = supabase.roomById(roomId)
                ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("Oda bulunamadı."))

            if (room.passwordHash != null) {
                val password = request.password?.trim().orEmpty()
                val verified = BCrypt.verifyer().verify(password.toCharArray(), room.passwordHash).verified
                if (!verified) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("Oda şifresi hatalı."))
                    return@post
                }
            }

            supabase.joinRoom(user.id, roomId)
            call.respond(supabase.publicRoom(room))
        }

        delete("/rooms/{id}/join") {
            val user = call.requireUser(supabase) ?: return@delete
            val roomId = call.parameters["id"].orEmpty()
            supabase.leaveRoom(user.id, roomId)
            call.respond(HttpStatusCode.NoContent)
        }

        post("/agora/token") {
            val user = call.requireUser(supabase) ?: return@post
            val request = call.receive<AgoraTokenRequest>()
            if (!supabase.isMember(user.id, request.roomId)) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("Önce odaya katılmalısınız."))
                return@post
            }
            call.respond(agora.createToken(request))
        }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.requireUser(
    supabase: SupabaseService
): SupabaseUser? {
    val token = request.headers[HttpHeaders.Authorization]
        ?.removePrefix("Bearer")
        ?.trim()
        .orEmpty()
    if (token.isBlank()) {
        respond(HttpStatusCode.Unauthorized, ErrorResponse("Oturum gerekli."))
        return null
    }
    return supabase.verifyUser(token) ?: run {
        respond(HttpStatusCode.Unauthorized, ErrorResponse("Oturum doğrulanamadı."))
        null
    }
}

data class ServerConfig(
    val supabaseUrl: String,
    val supabaseAnonKey: String,
    val supabaseServiceRoleKey: String,
    val agoraAppId: String,
    val agoraAppCertificate: String,
    val agoraTokenTtlSeconds: Int
) {
    companion object {
        fun fromEnvironment(): ServerConfig {
            fun required(name: String) = System.getenv(name)?.trim()?.takeIf { it.isNotBlank() }
                ?: error("$name environment variable is required")
            return ServerConfig(
                supabaseUrl = required("SUPABASE_URL").trimEnd('/'),
                supabaseAnonKey = required("SUPABASE_ANON_KEY"),
                supabaseServiceRoleKey = required("SUPABASE_SERVICE_ROLE_KEY"),
                agoraAppId = required("AGORA_APP_ID"),
                agoraAppCertificate = required("AGORA_APP_CERTIFICATE"),
                agoraTokenTtlSeconds = System.getenv("AGORA_TOKEN_TTL_SECONDS")?.toIntOrNull() ?: 3600
            )
        }
    }
}

class SupabaseService(
    private val config: ServerConfig,
    private val client: HttpClient
) {
    suspend fun verifyUser(accessToken: String): SupabaseUser? {
        return runCatching {
            client.get("${config.supabaseUrl}/auth/v1/user") {
                header("apikey", config.supabaseAnonKey)
                bearerAuth(accessToken)
            }.body<SupabaseUser>()
        }.getOrNull()
    }

    suspend fun roomsFor(userId: String): List<RoomResponse> {
        val rooms: List<RoomRecord> = client.get("${config.supabaseUrl}/rest/v1/rooms?select=*&order=created_at.desc") {
            serviceHeaders()
        }.body()
        val memberCounts = memberCounts()
        return rooms.map { publicRoom(it, memberCounts[it.id] ?: 0) }
    }

    suspend fun createRoom(ownerId: String, name: String, passwordHash: String?): RoomResponse {
        val created: List<RoomRecord> = client.post("${config.supabaseUrl}/rest/v1/rooms?select=*") {
            serviceHeaders()
            header("Prefer", "return=representation")
            setBody(
                CreateRoomRecord(
                    ownerId = ownerId,
                    name = name,
                    passwordHash = passwordHash,
                    isPrivate = passwordHash != null
                )
            )
        }.body()
        val room = created.first()
        joinRoom(ownerId, room.id)
        return publicRoom(room, 1)
    }

    suspend fun roomById(roomId: String): RoomRecord? {
        val rooms: List<RoomRecord> = client.get("${config.supabaseUrl}/rest/v1/rooms?id=eq.$roomId&select=*") {
            serviceHeaders()
        }.body()
        return rooms.firstOrNull()
    }

    suspend fun joinRoom(userId: String, roomId: String) {
        client.post("${config.supabaseUrl}/rest/v1/room_members?on_conflict=room_id,user_id") {
            serviceHeaders()
            header("Prefer", "resolution=merge-duplicates")
            setBody(RoomMemberRecord(roomId, userId))
        }
    }

    suspend fun leaveRoom(userId: String, roomId: String) {
        client.delete("${config.supabaseUrl}/rest/v1/room_members?room_id=eq.$roomId&user_id=eq.$userId") {
            serviceHeaders()
        }
    }

    suspend fun isMember(userId: String, roomId: String): Boolean {
        val rows: List<RoomMemberRecord> = client.get("${config.supabaseUrl}/rest/v1/room_members?room_id=eq.$roomId&user_id=eq.$userId&select=*") {
            serviceHeaders()
        }.body()
        return rows.isNotEmpty()
    }

    fun publicRoom(room: RoomRecord, memberCount: Int = 0): RoomResponse {
        return RoomResponse(
            id = room.id,
            name = room.name,
            isPrivate = room.isPrivate,
            memberCount = memberCount,
            createdAt = room.createdAt,
            ownerId = room.ownerId
        )
    }

    private suspend fun memberCounts(): Map<String, Int> {
        val members: List<RoomMemberRecord> = client.get("${config.supabaseUrl}/rest/v1/room_members?select=room_id,user_id") {
            serviceHeaders()
        }.body()
        return members.groupingBy { it.roomId }.eachCount()
    }

    private fun io.ktor.client.request.HttpRequestBuilder.serviceHeaders() {
        contentType(ContentType.Application.Json)
        header("apikey", config.supabaseServiceRoleKey)
        bearerAuth(config.supabaseServiceRoleKey)
    }
}

class AgoraTokenService(private val config: ServerConfig) {
    fun createToken(request: AgoraTokenRequest): AgoraTokenResponse {
        val ttl = config.agoraTokenTtlSeconds
        val token = RtcTokenBuilder2().buildTokenWithUid(
            config.agoraAppId,
            config.agoraAppCertificate,
            request.channelName,
            request.uid,
            RtcTokenBuilder2.Role.ROLE_PUBLISHER,
            ttl,
            ttl
        )
        return AgoraTokenResponse(
            token = token,
            appId = config.agoraAppId,
            channelName = request.channelName,
            uid = request.uid,
            expiresAtEpochSeconds = Instant.now().epochSecond + ttl
        )
    }
}

@Serializable
data class SupabaseUser(val id: String, val email: String? = null)

@Serializable
data class ErrorResponse(val error: String)

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

@Serializable
data class RoomResponse(
    val id: String,
    val name: String,
    @SerialName("is_private") val isPrivate: Boolean,
    @SerialName("member_count") val memberCount: Int,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("owner_id") val ownerId: String
)

@Serializable
data class RoomRecord(
    val id: String,
    val name: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("password_hash") val passwordHash: String? = null,
    @SerialName("is_private") val isPrivate: Boolean,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class CreateRoomRecord(
    @SerialName("owner_id") val ownerId: String,
    val name: String,
    @SerialName("password_hash") val passwordHash: String? = null,
    @SerialName("is_private") val isPrivate: Boolean
)

@Serializable
data class RoomMemberRecord(
    @SerialName("room_id") val roomId: String,
    @SerialName("user_id") val userId: String
)
