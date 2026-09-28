package com.talkroom.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.talkroom.app.AppContainer
import com.talkroom.app.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

data class AppState(
    val configError: String? = null,
    val loading: Boolean = false,
    val restoringSession: Boolean = true,
    val session: AuthSession? = null,
    val profile: Profile? = null,
    val rooms: List<Room> = emptyList(),
    val selectedRoom: Room? = null,
    val agoraToken: AgoraTokenResponse? = null,
    val message: String? = null,
    val confirmationEmail: String? = null,
    val activeTab: HomeTab = HomeTab.Call
) {
    val signedIn: Boolean get() = session != null
    val isAdmin: Boolean get() = session?.user?.email?.lowercase() == AdminEmail
}

enum class HomeTab { Call, Rooms, Profile, Diamonds, Admin }

interface AppActions {
    fun clearMessage()
    fun signIn(email: String, password: String)
    fun signUp(email: String, password: String)
    fun recoverPassword(email: String)
    fun signOut()
    fun setTab(tab: HomeTab)
    fun refreshRooms()
    fun saveProfile(username: String, bio: String, website: String)
    fun createRoom(name: String, password: String?)
    fun joinRoom(room: Room, password: String?)
    fun leaveCurrentRoom()
}

class AppViewModel(private val container: AppContainer) : ViewModel(), AppActions {
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state
    private var operation: Job? = null

    init {
        try {
            container.config.validate()
            operation = viewModelScope.launch {
                _state.update { it.copy(loading = true) }
                try {
                    container.sessions.restore()?.let { saved ->
                        _state.update { it.copy(session = saved) }
                        loadAccount()
                    }
                } catch (error: Exception) { fail(error) }
                finally { _state.update { it.copy(loading = false, restoringSession = false) } }
            }
        } catch (error: IllegalArgumentException) {
            _state.update { it.copy(configError = error.message, restoringSession = false) }
        }
    }

    override fun clearMessage() { _state.update { it.copy(message = null) } }

    override fun signIn(email: String, password: String) = launchOperation {
        validateCredentials(email, password)
        signedIn(container.supabaseApi.signIn(email, password))
    }

    override fun signUp(email: String, password: String) = launchOperation {
        validateCredentials(email, password)
        when (val result = container.supabaseApi.signUp(email, password)) {
            is SignUpResult.SignedIn -> signedIn(result.session)
            SignUpResult.ConfirmationRequired -> _state.update {
                it.copy(confirmationEmail = email.trim(), message = "E-postanızdaki onay bağlantısını açın, ardından giriş yapın.")
            }
        }
    }

    override fun recoverPassword(email: String) = launchOperation {
        requireEmail(email)
        container.supabaseApi.recoverPassword(email)
        _state.update { it.copy(message = "Bu adresle bir hesap varsa şifre sıfırlama e-postası gönderilecek.") }
    }

    override fun signOut() {
        val previous = operation
        previous?.cancel()
        operation = viewModelScope.launch {
            previous?.join()
            val old = _state.value
            _state.update { it.copy(loading = true) }
            try {
                try {
                    if (old.selectedRoom != null) authorized { container.backendApi.leaveRoom(it.accessToken, old.selectedRoom.id) }
                } catch (error: CancellationException) { throw error } catch (_: Exception) { }
                try {
                    authorized { container.supabaseApi.signOut(it.accessToken) }
                } catch (error: CancellationException) { throw error } catch (_: Exception) {
                    _state.update { it.copy(message = "Cihazdan çıkış yapıldı; sunucudaki oturum kapatılamadı.") }
                }
            } finally {
                withContext(NonCancellable) {
                    try { container.sessions.clear() }
                    catch (_: Exception) { _state.update { it.copy(message = "Kayıtlı oturum silinemedi. Uygulama verilerini cihaz ayarlarından temizleyin.") } }
                    _state.value = AppState(restoringSession = false, message = _state.value.message)
                }
            }
        }
    }

    override fun setTab(tab: HomeTab) { _state.update { it.copy(activeTab = tab) } }

    override fun refreshRooms() = launchOperation {
        // Also retries profile loading after a transient failure during login.
        loadAccount()
    }

    override fun saveProfile(username: String, bio: String, website: String) = launchOperation {
        if (username.trim().length !in 2..50) throw ApiException("Kullanıcı adı 2–50 karakter olmalı.")
        val profile = authorized { session ->
            container.supabaseApi.upsertProfile(session.accessToken, UpsertProfileRequest(
                id = session.user.id, email = session.user.email.orEmpty(),
                username = username.trim(), bio = bio.trim(), website = website.trim()
            ))
        }
        _state.update { it.copy(profile = profile, message = "Profil güncellendi.") }
    }

    override fun createRoom(name: String, password: String?) = launchOperation {
        authorized { container.backendApi.createRoom(it.accessToken, name.trim(), password) }
        loadRooms()
    }

    override fun joinRoom(room: Room, password: String?) = launchOperation {
        val joined = authorized { container.backendApi.joinRoom(it.accessToken, room.id, password) }
        val token = try {
            authorized { session -> container.backendApi.agoraToken(session.accessToken, joined.id,
                normalizeChannelName(joined.name), session.user.id.hashCode().absoluteValue.coerceAtLeast(1)) }
        } catch (error: Exception) {
            withContext(NonCancellable) {
                try { authorized { container.backendApi.leaveRoom(it.accessToken, joined.id) } }
                catch (_: Exception) { }
            }
            throw error
        }
        _state.update { it.copy(selectedRoom = joined, agoraToken = token) }
    }

    override fun leaveCurrentRoom() {
        val room = _state.value.selectedRoom ?: return
        if (operation?.isActive == true) return
        _state.update { it.copy(selectedRoom = null, agoraToken = null) }
        launchOperation {
            authorized { container.backendApi.leaveRoom(it.accessToken, room.id) }
            loadRooms()
        }
    }

    private fun launchOperation(block: suspend () -> Unit) {
        if (operation?.isActive == true || _state.value.configError != null) return
        operation = viewModelScope.launch {
            _state.update { it.copy(loading = true, message = null) }
            try { block() }
            catch (error: Exception) { fail(error) }
            finally { _state.update { it.copy(loading = false) } }
        }
    }

    private suspend fun signedIn(session: AuthSession) {
        container.sessions.accept(session)
        // Authentication succeeded even if profile or room loading later fails.
        _state.update { it.copy(session = session, confirmationEmail = null) }
        loadAccount()
    }

    private suspend fun loadAccount() {
        var profileError: ApiException? = null
        try {
            val profile = authorized { container.supabaseApi.getProfile(it.accessToken, it.user) }
            _state.update { it.copy(profile = profile) }
        } catch (error: ApiException) {
            if (error.code == "session_expired") throw error
            profileError = error
        }
        loadRooms()
        profileError?.let { throw it }
    }

    private suspend fun loadRooms() {
        val rooms = authorized { container.backendApi.rooms(it.accessToken) }
        _state.update { it.copy(rooms = rooms) }
    }

    private suspend fun <T> authorized(block: suspend (AuthSession) -> T): T {
        val result = container.sessions.authorized(block)
        val session = container.sessions.snapshot()
        _state.update { it.copy(session = session) }
        return result
    }

    private fun fail(error: Exception) {
        if (error is CancellationException) throw error
        if (error is ApiException && error.code == "session_expired") {
            _state.value = AppState(restoringSession = false, message = error.message)
        } else _state.update { it.copy(message = if (error is ApiException) error.message else "İşlem tamamlanamadı. Lütfen tekrar deneyin.") }
    }

    private fun requireEmail(email: String) {
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) throw ApiException("Geçerli bir e-posta adresi girin.")
    }

    private fun validateCredentials(email: String, password: String) {
        requireEmail(email)
        if (password.isEmpty()) throw ApiException("Şifrenizi girin.")
    }
}

class AppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(container) as T
}
