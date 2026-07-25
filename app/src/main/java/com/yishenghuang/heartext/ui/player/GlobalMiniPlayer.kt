package com.yishenghuang.heartext.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yishenghuang.heartext.R
import com.yishenghuang.heartext.tts.PlaybackSession
import com.yishenghuang.heartext.tts.TtsPlaybackState
import com.yishenghuang.heartext.ui.theme.HearPurple
import com.yishenghuang.heartext.ui.theme.HearTextPrimary

@Composable
fun GlobalMiniPlayer(
    session: PlaybackSession,
    onOpenPlayer: () -> Unit,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val speaking = session.playbackState == TtsPlaybackState.Speaking
    val shape = RoundedCornerShape(18.dp)
    val appName = stringResource(R.string.app_name)
    val pauseLabel = stringResource(R.string.reader_pause)
    val playLabel = stringResource(R.string.reader_play)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .shadow(6.dp, shape)
            .clip(shape)
            .background(Color.White)
            .clickable(onClick = onOpenPlayer)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        IconButton(onClick = onPlayPause, modifier = Modifier.size(44.dp)) {
            Icon(
                if (speaking) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (speaking) pauseLabel else playLabel,
                tint = HearPurple,
                modifier = Modifier.size(28.dp)
            )
        }
        Text(
            text = buildString {
                append(session.title.ifBlank { appName })
                if (session.chapterTitle.isNotBlank()) {
                    append(" · ")
                    append(session.chapterTitle)
                }
            },
            color = HearTextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
        )
        Text(
            text = session.voiceLabel.ifBlank { "TTS" },
            color = HearTextPrimary.copy(alpha = 0.55f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(end = 4.dp)
        )
        IconButton(onClick = onStop, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.reader_stop),
                tint = HearTextPrimary.copy(alpha = 0.65f)
            )
        }
    }
}
