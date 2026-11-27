package com.bigoke.metings.ui

import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.io.File

class AudioPlayer(private val file: File) {
    private var mp: MediaPlayer? = null
    var playing by mutableStateOf(false)
        private set
    var position by mutableLongStateOf(0L)
        private set
    var duration by mutableLongStateOf(0L)
        private set
    var speed by mutableStateOf(1f)
        private set
    var available by mutableStateOf(file.exists())
        private set

    private fun ensure(): MediaPlayer? {
        if (mp != null) return mp
        if (!file.exists()) { available = false; return null }
        return try {
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener { this@AudioPlayer.playing = false }
            }.also { mp = it; duration = it.duration.toLong(); available = true }
        } catch (e: Exception) {
            available = false; null
        }
    }

    fun toggle() {
        val p = ensure() ?: return
        if (p.isPlaying) { p.pause(); playing = false } else { applySpeed(p); p.start(); playing = true }
    }

    fun seekTo(ms: Long, play: Boolean = true) {
        val p = ensure() ?: return
        p.seekTo(ms.toInt().coerceAtLeast(0))
        position = ms
        if (play && !p.isPlaying) { applySpeed(p); p.start(); playing = true }
    }

    fun skip(deltaMs: Long) = seekTo((position + deltaMs).coerceIn(0, duration), play = playing)

    fun cycleSpeed() {
        speed = when (speed) { 1f -> 1.25f; 1.25f -> 1.5f; 1.5f -> 2f; else -> 1f }
        mp?.let { if (it.isPlaying) applySpeed(it) }
    }

    private fun applySpeed(p: MediaPlayer) {
        runCatching { p.playbackParams = p.playbackParams.setSpeed(speed) }
    }

    fun tick() {
        mp?.let { if (it.isPlaying) position = it.currentPosition.toLong() }
    }

    fun release() {
        mp?.release(); mp = null; playing = false
    }
}

@Composable
fun rememberAudioPlayer(file: File): AudioPlayer {
    val player = remember(file.absolutePath, file.exists()) { AudioPlayer(file) }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(player, player.playing) {
        while (player.playing) {
            player.tick()
            delay(50)
        }
    }
    return player
}
