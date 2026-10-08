package com.yishenghuang.heartext.tts

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.yishenghuang.heartext.HearTextApp
import com.yishenghuang.heartext.MainActivity
import com.yishenghuang.heartext.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

/**
 * Foreground media session so TTS shows on lock screen / shade and responds to headset buttons.
 * Skip next/previous maps to chapter navigation (not sentence).
 */
@UnstableApi
class HearTextPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var player: HearTextSessionPlayer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.playback_notification_channel)
                .build()
        )

        val app = application as HearTextApp
        val coordinator = app.container.playbackCoordinator
        val sessionPlayer = HearTextSessionPlayer(Looper.getMainLooper(), coordinator)
        player = sessionPlayer

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, sessionPlayer)
            .setId("heartext_playback")
            .setSessionActivity(sessionActivity)
            .build()

        observeJob = serviceScope.launch {
            coordinator.session.collectLatest { session ->
                sessionPlayer.invalidateFromCoordinator()
                if (!session.active) {
                    // Tear down after controllers release; stopSelf ends FGS + notification.
                    stopSelf()
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession.takeIf { controllerInfo.isTrusted }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val playing = player?.isPlaying == true
        if (!playing) {
            mediaSession?.player?.stop()
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        observeJob?.cancel()
        serviceScope.cancel()
        mediaSession?.run {
            release()
        }
        player?.release()
        mediaSession = null
        player = null
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "heartext_playback"
    }
}

@UnstableApi
private class HearTextSessionPlayer(
    looper: Looper,
    private val coordinator: PlaybackCoordinator
) : SimpleBasePlayer(looper) {
    private val handler = Handler(looper)

    fun invalidateFromCoordinator() {
        if (Looper.myLooper() == handler.looper) {
            invalidateState()
        } else {
            handler.post { invalidateState() }
        }
    }

    override fun getState(): State {
        val session = coordinator.session.value
        val playing = session.active && session.playbackState == TtsPlaybackState.Speaking
        val paused = session.active && session.playbackState == TtsPlaybackState.Paused

        val commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_STOP,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_GET_MEDIA_ITEMS_METADATA,
                Player.COMMAND_GET_TIMELINE
            )
            .build()

        val metadata = MediaMetadata.Builder()
            .setTitle(session.title.ifBlank { "HearText" })
            .setArtist(
                session.author.ifBlank {
                    session.voiceLabel.ifBlank { "HearText" }
                }
            )
            .setAlbumTitle(session.title.ifBlank { "HearText" })
            .setSubtitle(session.chapterTitle.ifBlank { " " })
            .setDisplayTitle(session.title.ifBlank { "HearText" })
            .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER)
            .setIsPlayable(true)
            .apply {
                session.coverPath?.let { path ->
                    val file = File(path)
                    if (file.exists()) {
                        setArtworkUri(Uri.fromFile(file))
                    }
                }
            }
            .build()

        val mediaId = if (session.bookId.isNotBlank()) {
            "${session.bookId}:${session.chapterIndex}"
        } else {
            "idle"
        }
        val item = MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(metadata)
            .build()

        val playlistItem = MediaItemData.Builder(mediaId)
            .setMediaItem(item)
            .setMediaMetadata(metadata)
            .setDurationUs(C.TIME_UNSET)
            .setIsSeekable(false)
            .build()

        val playbackState = when {
            !session.active -> Player.STATE_IDLE
            session.playbackState == TtsPlaybackState.Error -> Player.STATE_IDLE
            session.playbackState == TtsPlaybackState.Idle && !paused -> Player.STATE_ENDED
            else -> Player.STATE_READY
        }

        val audioAttrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()

        return State.Builder()
            .setAvailableCommands(commands)
            .setPlayWhenReady(
                playing,
                Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
            )
            .setPlaybackState(playbackState)
            .setAudioAttributes(audioAttrs)
            .setPlaylist(listOf(playlistItem))
            .setPlaylistMetadata(
                MediaMetadata.Builder()
                    .setTitle(session.title.ifBlank { "HearText" })
                    .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK)
                    .build()
            )
            .setCurrentMediaItemIndex(0)
            .setContentPositionMs(0L)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        val session = coordinator.session.value
        if (!session.active) {
            return Futures.immediateVoidFuture()
        }
        if (playWhenReady) {
            if (session.playbackState != TtsPlaybackState.Speaking) {
                coordinator.playPause()
            }
        } else if (session.playbackState == TtsPlaybackState.Speaking) {
            coordinator.pause()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        coordinator.stop()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int
    ): ListenableFuture<*> {
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> coordinator.skipChapter(+1)
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> coordinator.skipChapter(-1)
            else -> Unit
        }
        return Futures.immediateVoidFuture()
    }
}
