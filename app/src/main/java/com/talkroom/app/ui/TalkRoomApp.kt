package com.talkroom.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.talkroom.app.data.Room
import com.talkroom.app.rtc.AgoraRtcService

@Composable
fun TalkRoomApp(state: AppState, actions: AppActions) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        actions.clearMessage()
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Header(state)
            if (state.configError != null) {
                ConfigError(state.configError)
            } else if (!state.signedIn) {
                AuthScreen(state.loading, actions)
            } else if (state.selectedRoom != null && state.agoraToken != null) {
                RoomCallScreen(state, actions)
            } else {
                HomeScreen(state, actions)
            }
        }
    }
}

@Composable
private fun Header(state: AppState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("TalkRoom", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                state.profile?.username ?: state.session?.user?.email ?: "Güvenli sesli odalar",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (state.loading) CircularProgressIndicator(modifier = Modifier.width(28.dp))
    }
}

@Composable
private fun ConfigError(message: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Konfigürasyon eksik", fontWeight = FontWeight.Bold)
            Text(message)
            Text("kotlin/local.properties içine Supabase, backend ve Agora değerlerini ekleyin.")
        }
    }
}

@Composable
private fun AuthScreen(loading: Boolean, actions: AppActions) {
    var register by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !register, onClick = { register = false }, label = { Text("Giriş") })
                FilterChip(selected = register, onClick = { register = true }, label = { Text("Kayıt") })
            }
            OutlinedTextField(email, { email = it }, label = { Text("E-posta") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                password,
                { password = it },
                label = { Text("Şifre") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            if (register) {
                OutlinedTextField(
                    confirm,
                    { confirm = it },
                    label = { Text("Şifre tekrar") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Button(
                enabled = !loading,
                onClick = {
                    if (register) {
                        if (password == confirm) actions.signUp(email, password)
                    } else {
                        actions.signIn(email, password)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (register) "Hesap oluştur" else "Giriş yap")
            }
            TextButton(onClick = { actions.recoverPassword(email) }) {
                Text("Şifremi sıfırla")
            }
        }
    }
}

@Composable
private fun HomeScreen(state: AppState, actions: AppActions) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = state.activeTab == HomeTab.Rooms,
                    onClick = { actions.setTab(HomeTab.Rooms) },
                    label = { Text("Odalar") },
                    icon = {}
                )
                NavigationBarItem(
                    selected = state.activeTab == HomeTab.Profile,
                    onClick = { actions.setTab(HomeTab.Profile) },
                    label = { Text("Profil") },
                    icon = {}
                )
                NavigationBarItem(
                    selected = state.activeTab == HomeTab.Diamonds,
                    onClick = { actions.setTab(HomeTab.Diamonds) },
                    label = { Text("Elmas") },
                    icon = {}
                )
                if (state.isAdmin) {
                    NavigationBarItem(
                        selected = state.activeTab == HomeTab.Admin,
                        onClick = { actions.setTab(HomeTab.Admin) },
                        label = { Text("Admin") },
                        icon = {}
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (state.activeTab) {
                HomeTab.Rooms -> RoomsScreen(state.rooms, state.loading, actions)
                HomeTab.Profile -> ProfileScreen(state, actions)
                HomeTab.Diamonds -> DiamondsScreen()
                HomeTab.Admin -> AdminScreen(state, actions)
            }
        }
    }
}

@Composable
private fun RoomsScreen(rooms: List<Room>, loading: Boolean, actions: AppActions) {
    var filter by remember { mutableStateOf("Tümü") }
    var showCreate by remember { mutableStateOf(false) }
    val filtered = when (filter) {
        "Açık" -> rooms.filter { !it.isPrivate }
        "Şifreli" -> rooms.filter { it.isPrivate }
        else -> rooms
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Tümü", "Açık", "Şifreli").forEach {
            FilterChip(selected = filter == it, onClick = { filter = it }, label = { Text(it) })
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { showCreate = true }) { Text("Oda oluştur") }
        OutlinedButton(onClick = actions::refreshRooms, enabled = !loading) { Text("Yenile") }
    }
    if (filtered.isEmpty()) {
        Text("Gösterilecek oda yok.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filtered, key = { it.id }) { room ->
                RoomRow(room, actions)
            }
        }
    }
    if (showCreate) CreateRoomDialog(
        onDismiss = { showCreate = false },
        onCreate = { name, password ->
            showCreate = false
            actions.createRoom(name, password)
        }
    )
}

@Composable
private fun RoomRow(room: Room, actions: AppActions) {
    var passwordDialog by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(room.name, fontWeight = FontWeight.Bold)
                Text(
                    "${room.memberCount} kişi • ${if (room.isPrivate) "şifreli" else "herkese açık"}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = { if (room.isPrivate) passwordDialog = true else actions.joinRoom(room, null) }) {
                Text("Katıl")
            }
        }
    }
    if (passwordDialog) PasswordDialog(
        title = "${room.name} odasına giriş",
        onDismiss = { passwordDialog = false },
        onSubmit = {
            passwordDialog = false
            actions.joinRoom(room, it)
        }
    )
}

@Composable
private fun ProfileScreen(state: AppState, actions: AppActions) {
    var username by remember(state.profile?.username) { mutableStateOf(state.profile?.username.orEmpty()) }
    var bio by remember(state.profile?.bio) { mutableStateOf(state.profile?.bio.orEmpty()) }
    var website by remember(state.profile?.website) { mutableStateOf(state.profile?.website.orEmpty()) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(username, { username = it }, label = { Text("Kullanıcı adı") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(bio, { bio = it }, label = { Text("Bio") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(website, { website = it }, label = { Text("Website") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { actions.saveProfile(username, bio, website) }) { Text("Kaydet") }
            OutlinedButton(onClick = actions::signOut) { Text("Çıkış yap") }
        }
    }
}

@Composable
private fun DiamondsScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("50 Elmas" to "₺19.99", "150 Elmas" to "₺49.99", "500 Elmas" to "₺149.99").forEach { (label, price) ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, fontWeight = FontWeight.Bold)
                    Text(price, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Text("Ödeme sağlayıcısı bağlanana kadar satın alma butonu gösterilmez.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AdminScreen(state: AppState, actions: AppActions) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("God Mode", fontWeight = FontWeight.Bold)
            Text("Toplam oda: ${state.rooms.size}")
            Text("Aktif kullanıcı: ${state.profile?.email.orEmpty()}")
            OutlinedButton(onClick = actions::refreshRooms) { Text("Oda verisini yenile") }
        }
    }
}

@Composable
private fun RoomCallScreen(state: AppState, actions: AppActions) {
    val context = LocalContext.current
    val room = state.selectedRoom ?: return
    val token = state.agoraToken ?: return
    var connected by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var micEnabled by remember { mutableStateOf(true) }
    var videoEnabled by remember { mutableStateOf(true) }
    val service = remember(token.token) {
        AgoraRtcService(context, token.appId)
    }
    LaunchedEffect(token.token) {
        service.join(
            token = token.token,
            channelName = token.channelName,
            uid = token.uid,
            onJoined = {
                connected = true
                error = null
            },
            onError = {
                connected = false
                error = it
            }
        )
    }
    DisposableEffect(Unit) {
        onDispose { service.release() }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(room.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(if (connected) "Odadasın" else error ?: "Bağlanıyor")
            Text("Kanal: ${token.channelName}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    micEnabled = !micEnabled
                    service.muteAudio(!micEnabled)
                }) {
                    Text(if (micEnabled) "Mikrofon açık" else "Mikrofon kapalı")
                }
                Button(onClick = {
                    videoEnabled = !videoEnabled
                    service.muteVideo(!videoEnabled)
                }) {
                    Text(if (videoEnabled) "Kamera açık" else "Kamera kapalı")
                }
            }
            OutlinedButton(onClick = {
                service.leave()
                actions.leaveCurrentRoom()
            }) { Text("Odadan çık") }
        }
    }
}

@Composable
private fun CreateRoomDialog(onDismiss: () -> Unit, onCreate: (String, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Oda oluştur") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Oda adı") })
                OutlinedTextField(password, { password = it }, label = { Text("Şifre (opsiyonel)") })
            }
        },
        confirmButton = {
            Button(onClick = { onCreate(name, password) }, enabled = name.isNotBlank()) { Text("Aç") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } }
    )
}

@Composable
private fun PasswordDialog(title: String, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                password,
                { password = it },
                label = { Text("Oda şifresi") },
                visualTransformation = PasswordVisualTransformation()
            )
        },
        confirmButton = {
            Button(onClick = { onSubmit(password) }, enabled = password.isNotBlank()) { Text("Giriş") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } }
    )
}
