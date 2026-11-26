package com.ahmed.zaghlolplayer.tv

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * ExoPlayer wrapper. HLS via Media3 (the Android equivalent of hls.js).
 * Xtream live .ts URLs are rewritten to .m3u8, same as Windows.
 */
class TvPlayer(context: Context) {

    private val dataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
        )
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(30_000)
        .setAllowCrossProtocolRedirects(true)

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context)
                .setDataSourceFactory(dataSourceFactory)
        )
        .build()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _errorAr = MutableStateFlow<String?>(null)
    val errorAr: StateFlow<String?> = _errorAr

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady

    /** One-shot callback fired the first time the current item becomes ready. */
    var onFirstReady: (() -> Unit)? = null
    private var readyFiredFor: String? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onPlaybackStateChanged(state: Int) {
                _isReady.value = state == Player.STATE_READY
                if (state == Player.STATE_READY) {
                    val tag = player.currentMediaItem?.mediaId
                    if (tag != null && tag != readyFiredFor) {
                        readyFiredFor = tag
                        onFirstReady?.invoke()
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                _errorAr.value = "تعذّر تشغيل هذا البث — قد يكون السيرفر مشغولاً أو الرابط منتهياً."
            }
        })
    }

    fun play(url: String, mediaId: String, startMs: Long = 0L) {
        _errorAr.value = null
        readyFiredFor = null
        val finalUrl = PlaylistService.streamUrlFor(url)
        val item = MediaItem.Builder()
            .setUri(finalUrl)
            .setMediaId(mediaId)
            .build()
        // HLS vs progressive is auto-detected from the URL/content by the
        // DefaultMediaSourceFactory (HlsMediaSource for .m3u8).
        player.setMediaItem(item, startMs)
        player.prepare()
        player.play()
    }

    fun playHls(url: String, mediaId: String, startMs: Long = 0L) {
        _errorAr.value = null
        readyFiredFor = null
        val item = MediaItem.Builder()
            .setUri(PlaylistService.streamUrlFor(url))
            .setMediaId(mediaId)
            .build()
        val source = HlsMediaSource.Factory(dataSourceFactory).createMediaSource(item)
        player.setMediaSource(source, startMs)
        player.prepare()
        player.play()
    }

    @Suppress("UNUSED_PARAMETER")
    fun playProgressive(url: String, mediaId: String, startMs: Long = 0L) {
        _errorAr.value = null
        readyFiredFor = null
        val item = MediaItem.Builder().setUri(url).setMediaId(mediaId).build()
        val source = ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(item)
        player.setMediaSource(source, startMs)
        player.prepare()
        player.play()
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekBy(deltaMs: Long) {
        val dur = player.duration
        val pos = player.currentPosition + deltaMs
        player.seekTo(pos.coerceIn(0L, if (dur > 0) dur else pos.coerceAtLeast(0L)))
    }

    fun seekTo(ms: Long) = player.seekTo(ms)

    fun stop() {
        player.stop()
        _isPlaying.value = false
    }

    fun release() {
        player.release()
    }
}
