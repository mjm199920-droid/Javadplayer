
package com.javad.player

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class MusicService : MediaSessionService() {

    companion object {
        @Volatile
        var currentAudioSessionId: Int = 0
    }

    private var mediaSession: MediaSession? = null

    lateinit var player: ExoPlayer
        private set

    override fun onCreate() {
        super.onCreate()

        player = ExoPlayer.Builder(this).build()
        currentAudioSessionId = player.audioSessionId

        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null

        player.release()

        super.onDestroy()
    }
}
