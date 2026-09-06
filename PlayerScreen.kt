package com.localshelf.app

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun PlayerScreen(media: LocalMedia, prefs: Prefs, onBack: () -> Unit) {
    val context = LocalContext.current
    val player = remember(media.uri) {
        ExoPlayer.Builder(context).build().apply {
            val subtitleConfigurations = media.subtitles.map { sub ->
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.uri))
                    .setMimeType(sub.mimeType)
                    .setLanguage(sub.language)
                    .setLabel(sub.label)
                    .build()
            }
            val item = MediaItem.Builder()
                .setUri(media.uri)
                .setSubtitleConfigurations(subtitleConfigurations)
                .build()
            setMediaItem(item)
            prepare()
            seekTo(prefs.progress(media.uri))
            playWhenReady = true
        }
    }

    fun persistPosition() {
        val duration = when {
            player.duration > 0 -> player.duration
            media.durationMs > 0 -> media.durationMs
            else -> 0L
        }
        val position = player.currentPosition.coerceAtLeast(0L)
        if (duration > 0 && position >= duration * 0.95) {
            prefs.setWatched(media.uri, true)
        } else if (position > 0) {
            prefs.saveProgress(media.uri, position)
        }
    }

    BackHandler {
        persistPosition()
        onBack()
    }

    LaunchedEffect(player) {
        while (isActive) {
            delay(5_000)
            persistPosition()
        }
    }

    DisposableEffect(player) {
        onDispose {
            persistPosition()
            player.release()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    useController = true
                    controllerAutoShow = true
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize()
        )

        Surface(color = Color.Black.copy(alpha = 0.72f), modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
            Row(Modifier.padding(top = 4.dp, bottom = 4.dp, start = 4.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    persistPosition()
                    onBack()
                }) { Icon(Icons.Default.ArrowBack, "Back") }
                Column(Modifier.weight(1f)) {
                    Text(media.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    if (media.isEpisode) {
                        Text("${media.show} • S%02dE%02d".format(media.season, media.episode), color = Color.LightGray, fontSize = 12.sp, maxLines = 1)
                    } else if (media.subtitles.isNotEmpty()) {
                        Text("${media.subtitles.size} subtitle track${if (media.subtitles.size == 1) "" else "s"}", color = Color.LightGray, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
