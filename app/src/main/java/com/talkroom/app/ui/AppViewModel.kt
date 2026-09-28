package com.talkroom.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.talkroom.app.AppContainer
import com.talkroom.app.data.AdminEmail
import com.talkroom.app.data.AgoraTokenResponse
import com.talkroom.app.data.AppConfig
import com.talkroom.app.data.AuthSession
import com.talkroom.app.data.Profile
import com.talkroom.app.data.Room
import com.talkroom.app.data.UpsertProfileRequest
import com.talkroom.app.data.normalizeChannelName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

data class AppState(
    val configError: String? = null,
    val loading: Boolean = false,
    val session: AuthSession? = null,
    val profile: Profile? = null,
    val rooms: List<Room> = emptyList(),
    val selectedRoom: Room? = null,
    val agoraToken: AgoraTokenResponse? = null,
    val message: String? = null,
    val activeTab: HomeTab = HomeTab.Rooms
) {
    val signedIn: Boolean get() = session != null
    val isAdmin: Boolean get() = session?.user?.email?.lowercase() == AdminEmail
}

enum class HomeTab { Rooms, Profile, Diamonds, Admin }

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

    init {
        try {
            container.config.validate()
        } catch (error: IllegalArgumentException) {
            _state.update { it.copy(configError = error.message) }
        }
    }

    override fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    override fun signIn(email: String, password: String) = launchAuth {
        val session = container.supabaseApi.signIn(email, password)
        signedIn(session)
    }

    override fun signUp(email: String, password: String) = launchAuth {
        val session = container.supabaseApi.signUp(email, password)
        signedIn(session)
    }

    override fun recoverPassword(email: String) {
        viewModelScope.launch {
            runCatching {
                _state.update { it.copy(loading = true, message = null) }
                container.supabaseApi.recoverPassword(email)
            }.onSuccess {
                _state.update { it.copy(loading = false, message = "Şifre sıfırlama e-postası gönderildi.") }
            }.onFailure(::fail)
        }
    }

    override fun signOut() {
        val token = _state.value.session?.accessToken
        viewModelScope.launch {
            if (token != null) runCatching { container.supabaseApi.signOut(token) }
            _state.value = AppState()
            try {
                container.config.validate()
            } catch (error: IllegalArgumentException) {
                _state.update { it.copy(configError = error.message) }
            }
        }
    }

    override fun setTab(tab: HomeTab) {
        _state.update { it.copy(activeTab = tab) }
    }

    override fun refreshRooms() {
        val accessToken = _state.value.session?.accessToken ?: return
        viewModelScope.launch {
            runCatching {
                _state.update { it.copy(loading = true, message = null) }
                container.backendApi.rooms(accessToken)
            }.onSuccess { rooms ->
                _state.update { it.copy(loading = false, rooms = rooms) }
            }.onFailure(::fail)
        }
    }

    override fun saveProfile(username: String, bio: String, website: String) {
        val state = _state.value
        val session = state.session ?: return
        viewModelScope.launch {
            runCatching {
                _state.update { it.copy(loading = true, message = null) }
                container.supabaseApi.upsertProfile(
                    session.accessToken,
                    UpsertProfileRequest(
                        id = session.user.id,
                        email = session.user.email.orEmpty(),
                        username = username.trim(),
                        bio = bio.trim(),
                        website = website.trim()
                    )
                )
            }.onSuccess { profile ->
                _state.update { it.copy(loading = false, profile = profile, message = "Profil güncellendi.") }
            }.onFailure(::fail)
        }
    }

    override fun createRoom(name: String, password: String?) {
        val accessToken = _state.value.session?.accessToken ?: return
        viewModelScope.launch {
            runCatching {
                _state.update { it.copy(loading = true, message = null) }
                container.backendApi.createRoom(accessToken, name.trim(), password)
            }.onSuccess {
                refreshRooms()
            }.onFailure(::fail)
        }
    }

    override fun joinRoom(room: Room, password: String?) {
        val session = _state.value.session ?: return
        viewModelScope.launch {
            runCatching {
                _state.update { it.copy(loading = true, message = null) }
                val joined = container.backendApi.joinRoom(session.accessToken, room.id, password)
                val uid = session.user.id.hashCode().absoluteValue.coerceAtLeast(1)
                val channelName = normalizeChannelName(joined.name)
                val token = container.backendApi.agoraToken(session.accessToken, joined.id, channelName, uid)
                joined to token
            }.onSuccess { (joined, token) ->
                _state.update {
                    it.copy(
                        loading = false,
                        selectedRoom = joined,
                        agoraToken = token,
                        message = null
                    )
                }
                refreshRooms()
            }.onFailure(::fail)
        }
    }

    override fun leaveCurrentRoom() {
        val state = _state.value
        val accessToken = state.session?.accessToken
        val roomId = state.selectedRoom?.id
        _state.update { it.copy(selectedRoom = null, agoraToken = null) }
        if (accessToken != null && roomId != null) {
            viewModelScope.launch {
                runCatching { container.backendApi.leaveRoom(accessToken, roomId) }
                refreshRooms()
            }
        }
    }

    private fun launchAuth(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching {
                _state.update { it.copy(loading = true, message = null) }
                block()
            }.onFailure(::fail)
        }
    }

    private suspend fun signedIn(session: AuthSession) {
        val profile = container.supabaseApi.getProfile(session.accessToken, session.user)
        val rooms = container.backendApi.rooms(session.accessToken)
        _state.update {
            it.copy(
                loading = false,
                session = session,
                profile = profile,
                rooms = rooms,
                message = null
            )
        }
    }

    private fun fail(error: Throwable) {
        _state.update {
            it.copy(
                loading = false,
                message = error.message ?: "İşlem tamamlanamadı."
            )
        }
    }
}

class AppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AppViewModel(container) as T
    }
}
