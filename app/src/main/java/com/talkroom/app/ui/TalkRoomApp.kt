package com.talkroom.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkroom.app.data.Room
import com.talkroom.app.rtc.AgoraRtcService
import kotlinx.coroutines.delay

@Composable
fun TalkRoomApp(state: AppState, actions: AppActions) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); actions.clearMessage() }
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
        if (state.signedIn && state.selectedRoom == null) {
            NavigationBar(containerColor = Emerald.copy(alpha = .035f), tonalElevation = 0.dp) {
                listOf(Triple(HomeTab.Profile, "Profil", Icons.Outlined.AccountCircle),
                    Triple(HomeTab.Call, "Ara", Icons.Outlined.PhoneInTalk),
                    Triple(HomeTab.Rooms, "Odalar", Icons.Outlined.GridView)).forEach { (tab, label, icon) ->
                    NavigationBarItem(selected = state.activeTab == tab, onClick = { actions.setTab(tab) },
                        icon = { Icon(icon, label, modifier = Modifier.size(24.dp)) },
                        alwaysShowLabel = false, label = { Text(label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Emerald.copy(alpha = .13f), selectedIconColor = Emerald, unselectedIconColor = White))
                }
                if (state.isAdmin) NavigationBarItem(selected = state.activeTab == HomeTab.Admin,
                    onClick = { actions.setTab(HomeTab.Admin) }, icon = { Icon(Icons.Outlined.Shield, "Yönetim") }, label = { Text("Yönetim") })
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("talk ", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("room", color = Emerald, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (state.loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            }
            when {
                state.configError != null -> Text("Bağlantı ayarları kullanılamıyor.", Modifier.padding(24.dp))
                !state.signedIn -> AuthScreen(state.loading, actions)
                state.selectedRoom != null && state.agoraToken != null -> RoomCallScreen(state, actions)
                state.activeTab == HomeTab.Profile -> ProfileScreen(state, actions)
                state.activeTab == HomeTab.Admin && state.isAdmin -> Column(Modifier.padding(24.dp)) {
                    Title("Yönetim", "${state.rooms.size} oda")
                    OutlinedButton(onClick = actions::refreshRooms, enabled = !state.loading) { Text("Odaları yenile") }
                }
                else -> RoomsScreen(state, actions)
            }
        }
    }
}

@Composable
private fun Title(title: String, subtitle: String) {
    Text(title, fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    Text(subtitle, color = White.copy(alpha = .6f), fontSize = 14.sp, lineHeight = 22.sp)
}

@Composable
private fun Wave(modifier: Modifier = Modifier, active: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "voice")
    val pulse by transition.animateFloat(.65f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "pulse")
    Canvas(modifier) {
        val bars = listOf(.04f, .12f, .32f, .18f, .58f, .83f, .45f, 1f, .74f, .92f, .49f, .66f, .39f, .22f, .31f, .1f, .04f)
        bars.forEachIndexed { i, h ->
            val x = size.width * (i + 1) / (bars.size + 1)
            val height = size.height * h * if (active) pulse else 1f
            drawLine(White, Offset(x, (size.height - height) / 2), Offset(x, (size.height + height) / 2), size.width / 65, StrokeCap.Round)
        }
    }
}

@Composable
private fun CallCircle(enabled: Boolean, label: String, active: Boolean = false, showWave: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.size(248.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            for (i in 14 downTo 1) drawCircle(Emerald.copy(alpha = .018f), radius = size.minDimension * .375f, style = Stroke((6 + i * 3).dp.toPx()))
            drawCircle(Emerald.copy(alpha = .17f), radius = size.minDimension * .49f, style = Stroke(1.dp.toPx()))
            drawCircle(Emerald.copy(alpha = .45f), radius = size.minDimension * .41f, style = Stroke(1.dp.toPx()))
        }
        Surface(onClick = onClick, enabled = enabled, shape = CircleShape,
            color = Forest, border = androidx.compose.foundation.BorderStroke(6.dp, Emerald), modifier = Modifier.size(186.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (showWave) {
                    Wave(Modifier.size(66.dp, 46.dp), active)
                    Spacer(Modifier.height(20.dp))
                }
                Text(label, color = White, fontWeight = FontWeight.Bold, fontSize = if (showWave) 16.sp else 24.sp,
                    lineHeight = 27.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
private fun RoomsScreen(state: AppState, actions: AppActions) {
    var filter by rememberSaveable { mutableStateOf("Tümü") }
    var create by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    var pendingRoom by remember { mutableStateOf<Room?>(null) }
    var pendingPassword by remember { mutableStateOf<String?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pendingRoom?.let { actions.joinRoom(it, pendingPassword) }
        permissionDenied = !granted
        pendingRoom = null
    }
    val join: (Room, String?) -> Unit = { room, password ->
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) actions.joinRoom(room, password)
        else { pendingRoom = room; pendingPassword = password; permission.launch(Manifest.permission.RECORD_AUDIO) }
    }
    val rooms = state.rooms.filter { filter == "Tümü" || (filter == "Açık" && !it.isPrivate) || (filter == "Şifreli" && it.isPrivate) }
    if (state.activeTab == HomeTab.Call) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val contentHeight = maxHeight.coerceAtLeast(480.dp)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Column(Modifier.fillMaxWidth().height(contentHeight).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.weight(.45f))
                    Wave(Modifier.size(154.dp, 68.dp), state.loading)
                    Spacer(Modifier.weight(.8f))
                    CallCircle(!state.loading, if (state.loading) "BEKLE…" else "ARAMAYI\nBAŞLAT", state.loading, showWave = false) {
                        state.rooms.firstOrNull { !it.isPrivate }?.let { join(it, null) } ?: run { create = true }
                    }
                    Spacer(Modifier.weight(.6f))
                    Text("Yeni bir sesle tanış.", color = White, fontSize = 14.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(5.dp))
                    Text(if (state.rooms.any { !it.isPrivate }) "Açık bir odada sohbete katıl." else "İlk sohbet odasını sen aç.", color = White.copy(alpha = .65f), fontSize = 12.sp, textAlign = TextAlign.Center)
                    if (permissionDenied) Text("Mikrofon izni gerekli.", modifier = Modifier.padding(top = 12.dp), textAlign = TextAlign.Center)
                    Spacer(Modifier.weight(.45f))
                }
            }
        }
    } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (permissionDenied) Text("Görüşmeye katılmak için mikrofon izni gerekli.", modifier = Modifier.padding(top = 12.dp))
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Sohbet odaları", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = actions::refreshRooms, enabled = !state.loading) { Icon(Icons.Outlined.Refresh, "Odaları yenile") }
            IconButton(onClick = { create = true }, enabled = !state.loading) { Icon(Icons.Outlined.Add, "Oda oluştur", tint = Emerald) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Tümü", "Açık", "Şifreli").forEach { label -> FilterChip(selected = label == filter, onClick = { filter = label }, label = { Text(label) }) }
        }
        if (rooms.isEmpty()) {
            Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Forum, null, tint = Emerald)
                Spacer(Modifier.width(14.dp))
                Column { Text("Henüz oda yok", fontWeight = FontWeight.Medium); Text("Yeni bir sohbet için yer var.", color = White.copy(alpha = .6f), fontSize = 13.sp) }
            }
        }
        rooms.forEach { room -> key(room.id) { RoomRow(room, !state.loading, join) } }
        Spacer(Modifier.height(20.dp))
    }
    if (create) CreateRoomDialog({ create = false }) { name, password -> create = false; actions.createRoom(name, password) }
}

@Composable
private fun RoomRow(room: Room, enabled: Boolean, join: (Room, String?) -> Unit) {
    var password by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(46.dp).background(Emerald.copy(alpha = .08f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Icon(if (room.isPrivate) Icons.Outlined.Lock else Icons.Outlined.GraphicEq, null, tint = Emerald)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(room.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${room.memberCount} kişi · ${if (room.isPrivate) "Şifreli" else "Açık oda"}", fontSize = 12.sp, color = White.copy(alpha = .6f))
        }
        IconButton(onClick = { if (room.isPrivate) password = true else join(room, null) }, enabled = enabled) { Icon(Icons.Outlined.ArrowForward, "${room.name} odasına katıl", tint = Emerald) }
    }
    HorizontalDivider(color = White.copy(alpha = .08f))
    if (password) PasswordDialog(room.name, { password = false }) { password = false; join(room, it) }
}

@Composable
private fun AuthScreen(loading: Boolean, actions: AppActions) {
    var register by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(12.dp))
        Wave(Modifier.size(64.dp, 48.dp))
        Title(if (register) "Sohbete katıl." else "Yeniden merhaba.", "Sesinle başlayan bağlantılar.")
        Row { FilterChip(!register, { register = false }, { Text("Giriş yap") }); Spacer(Modifier.width(12.dp)); FilterChip(register, { register = true }, { Text("Hesap oluştur") }) }
        OutlinedTextField(email, { email = it }, label = { Text("E-posta") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text("Şifre") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        if (register) OutlinedTextField(confirm, { confirm = it }, label = { Text("Şifre tekrar") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), isError = confirm.isNotEmpty() && confirm != password, modifier = Modifier.fillMaxWidth())
        Button(onClick = { if (register) actions.signUp(email.trim(), password) else actions.signIn(email.trim(), password) },
            enabled = !loading && email.isNotBlank() && password.isNotEmpty() && (!register || (password == confirm && password.length >= 6)),
            shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().height(54.dp)) { Text(if (loading) "Lütfen bekle…" else if (register) "Hesap oluştur" else "Giriş yap") }
        TextButton(onClick = { actions.recoverPassword(email.trim()) }, enabled = !loading && email.isNotBlank()) { Text("Şifremi unuttum") }
    }
}

@Composable
private fun ProfileScreen(state: AppState, actions: AppActions) {
    var name by remember(state.profile?.username) { mutableStateOf(state.profile?.username.orEmpty()) }
    var bio by remember(state.profile?.bio) { mutableStateOf(state.profile?.bio.orEmpty()) }
    var website by remember(state.profile?.website) { mutableStateOf(state.profile?.website.orEmpty()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Box(Modifier.size(72.dp).border(1.dp, Emerald, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Person, null, tint = Emerald, modifier = Modifier.size(32.dp)) }
        Title("Senin alanın", state.profile?.username ?: "Profil")
        OutlinedTextField(name, { name = it }, label = { Text("Kullanıcı adı") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(bio, { bio = it }, label = { Text("Hakkımda") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(website, { website = it }, label = { Text("Web sitesi") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = { actions.saveProfile(name, bio, website) }, enabled = !state.loading && name.isNotBlank(), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Değişiklikleri kaydet") }
        TextButton(onClick = actions::signOut, enabled = !state.loading) { Icon(Icons.Outlined.Logout, null); Spacer(Modifier.width(8.dp)); Text("Çıkış yap") }
    }
}

@Composable
private fun RoomCallScreen(state: AppState, actions: AppActions) {
    val context = LocalContext.current
    val token = state.agoraToken ?: return
    val room = state.selectedRoom ?: return
    var connected by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var mic by remember { mutableStateOf(true) }
    var speaker by remember { mutableStateOf(true) }
    var seconds by remember { mutableIntStateOf(0) }
    val service = remember(token.token) { AgoraRtcService(context, token.appId) }
    LaunchedEffect(token.token) {
        runCatching { service.join(token.token, token.channelName, token.uid, { connected = true; error = null }, { connected = false; error = it }) }.onFailure { error = "Bağlantı kurulamadı." }
    }
    LaunchedEffect(connected) { if (connected) while (true) { delay(1000); seconds++ } }
    DisposableEffect(service) { onDispose { service.release() } }
    BackHandler { actions.leaveCurrentRoom() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(26.dp))
        Text(if (connected) "GÖRÜŞMEDE" else "BAĞLANTI", color = Emerald, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Text(room.name, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(36.dp))
        CallCircle(false, if (connected) "%02d:%02d".format(seconds / 60, seconds % 60) else "Bağlanıyor…", connected) {}
        Spacer(Modifier.height(18.dp))
        Text(error ?: if (connected) "Sesli odadasın" else "Odaya bağlanılıyor", color = White.copy(alpha = .6f), textAlign = TextAlign.Center)
        Spacer(Modifier.height(42.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            CallControl(if (mic) Icons.Outlined.Mic else Icons.Outlined.MicOff, if (mic) "Mikrofon" else "Sessiz", !mic) { mic = !mic; service.muteAudio(!mic) }
            CallControl(Icons.Outlined.VolumeUp, "Hoparlör", speaker) { speaker = !speaker; service.setSpeaker(speaker) }
            CallControl(Icons.Outlined.CallEnd, "Ayrıl", true) { actions.leaveCurrentRoom() }
        }
    }
}

@Composable
private fun CallControl(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(onClick, modifier = Modifier.size(60.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = if (selected) Emerald else White.copy(alpha = .08f), contentColor = if (selected) Forest else White)) { Icon(icon, label) }
        Spacer(Modifier.height(10.dp)); Text(label, fontSize = 12.sp, color = White.copy(alpha = .7f))
    }
}

@Composable
private fun CreateRoomDialog(onDismiss: () -> Unit, onCreate: (String, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Yeni sohbet odası") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Oda adı") }, singleLine = true)
            OutlinedTextField(password, { password = it }, label = { Text("Şifre (isteğe bağlı)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
        }
    }, confirmButton = { TextButton(onClick = { onCreate(name.trim(), password.takeIf { it.isNotBlank() }) }, enabled = name.isNotBlank()) { Text("Oluştur") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } })
}

@Composable
private fun PasswordDialog(title: String, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { OutlinedTextField(password, { password = it }, label = { Text("Oda şifresi") }, visualTransformation = PasswordVisualTransformation(), singleLine = true) }, confirmButton = { TextButton(onClick = { onSubmit(password) }, enabled = password.isNotBlank()) { Text("Katıl") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } })
}
