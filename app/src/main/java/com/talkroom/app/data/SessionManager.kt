package com.talkroom.app.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class SessionManager(private val api: SupabaseApi, private val store: SessionStore) {
    private val mutex = Mutex()
    private var current: AuthSession? = null

    suspend fun restore(): AuthSession? = mutex.withLock {
        if (current == null) current = store.read()
        current
    }

    suspend fun accept(session: AuthSession) = mutex.withLock {
        withContext(NonCancellable) {
            store.write(session)
            current = session
        }
    }

    suspend fun clear() = mutex.withLock {
        current = null
        store.write(null)
    }

    suspend fun snapshot(): AuthSession? = mutex.withLock { current }

    // Serialize refresh rotation, and retry only rejected credentials, never arbitrary writes.
    suspend fun <T> authorized(block: suspend (AuthSession) -> T): T = mutex.withLock {
        val session = validSession()
        try {
            block(session)
        } catch (error: ApiException) {
            if (error.status != 401) throw error
            val renewed = refresh(session)
            try { block(renewed) } catch (retry: ApiException) {
                if (retry.status == 401) expired()
                throw retry
            }
        }
    }

    private suspend fun validSession(): AuthSession {
        val session = current ?: throw ApiException("Lütfen yeniden giriş yapın.", code = "session_expired")
        return if ((session.expiresAt ?: 0) <= System.currentTimeMillis() / 1000 + 60) refresh(session) else session
    }

    private suspend fun refresh(session: AuthSession): AuthSession = withContext(NonCancellable) {
        val token = session.refreshToken?.takeIf { it.isNotBlank() }
        if (token == null) expired()
        val renewed = try { api.refreshSession(token) } catch (error: ApiException) {
            if (error.code in setOf("refresh_token_not_found", "refresh_token_already_used", "session_not_found", "session_expired", "user_not_found", "user_banned", "invalid_grant") ||
                (error.status == 400 && error.code == null)) expired()
            throw error
        }
        current = renewed
        store.write(renewed)
        renewed
    }

    private suspend fun expired(): Nothing {
        current = null
        store.write(null)
        throw ApiException("Oturumunuz sona erdi. Lütfen yeniden giriş yapın.", code = "session_expired")
    }
}
