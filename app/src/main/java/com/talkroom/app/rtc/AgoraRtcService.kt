package com.talkroom.app.rtc

import android.content.Context
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine

class AgoraRtcService(
    private val context: Context,
    private val appId: String
) {
    private var engine: RtcEngine? = null

    fun join(
        token: String,
        channelName: String,
        uid: Int,
        onJoined: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (appId.isBlank()) {
            onError("Agora App ID eksik.")
            return
        }
        val handler = object : IRtcEngineEventHandler() {
            override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
                onJoined()
            }

            override fun onError(err: Int) {
                onError("Agora bağlantı hatası: $err")
            }
        }
        val rtcEngine = engine ?: RtcEngine.create(context.applicationContext, appId, handler).also {
            it.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION)
            it.setClientRole(Constants.CLIENT_ROLE_BROADCASTER)
            it.enableVideo()
            engine = it
        }
        val options = ChannelMediaOptions().apply {
            channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
            clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
            publishMicrophoneTrack = true
            publishCameraTrack = true
            autoSubscribeAudio = true
            autoSubscribeVideo = true
        }
        rtcEngine.joinChannel(token, channelName, uid, options)
    }

    fun muteAudio(muted: Boolean) {
        engine?.muteLocalAudioStream(muted)
    }

    fun muteVideo(muted: Boolean) {
        engine?.muteLocalVideoStream(muted)
    }

    fun leave() {
        engine?.leaveChannel()
    }

    fun release() {
        engine?.leaveChannel()
        RtcEngine.destroy()
        engine = null
    }
}
