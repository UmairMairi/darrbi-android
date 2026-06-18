package com.mytm.darrbi.presentation.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mytm.darrbi.R
import com.mytm.darrbi.core.designsystem.DarrbiTheme
import com.mytm.darrbi.domain.model.ChatMessage
import com.mytm.darrbi.domain.model.ChatMessageStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Rider↔captain in-trip chat (socket-based, matching ride-android's chat events): header with the
 * captain, a scrollable message list with status ticks, quick-reply chips, and the message input bar.
 */
@Composable
fun ChatScreen(
    peerId: String,
    peerName: String,
    peerImageUrl: String?,
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(peerId) {
        viewModel.start(peerId, peerName, peerImageUrl)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarrbiTheme.colors.background)
            .statusBarsPadding(),
    ) {
        ChatHeader(
            name = state.driverName.ifBlank { peerName },
            imageUrl = state.driverImageUrl ?: peerImageUrl,
            typing = state.otherTyping,
            onBack = onBack,
        )

        val listState = rememberLazyListState()
        LaunchedEffect(state.messages.size) {
            if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.messages, key = { it.messageId.ifBlank { it.mediaIdentifier } }) { message ->
                MessageRow(message)
            }
        }

        QuickReplies(onSelect = viewModel::sendQuickReply)

        ChatInputBar(
            value = state.draft,
            onValueChange = viewModel::onDraftChange,
            onSend = viewModel::sendDraft,
            onMediaUnavailable = {
                android.widget.Toast
                    .makeText(context, context.getString(R.string.chat_media_coming_soon), android.widget.Toast.LENGTH_SHORT)
                    .show()
            },
        )
    }
}

@Composable
private fun ChatHeader(name: String, imageUrl: String?, typing: Boolean, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarrbiTheme.colors.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .border(1.dp, DarrbiTheme.colors.outline, CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
                tint = DarrbiTheme.colors.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
            placeholder = painterResource(R.drawable.user_placeholder),
            error = painterResource(R.drawable.user_placeholder),
            fallback = painterResource(R.drawable.user_placeholder),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            androidx.compose.material3.Text(
                text = name,
                style = DarrbiTheme.typography.title,
                color = DarrbiTheme.colors.onSurface,
            )
            if (typing) {
                androidx.compose.material3.Text(
                    text = stringResource(R.string.chat_typing),
                    style = DarrbiTheme.typography.caption,
                    color = DarrbiTheme.colors.primary,
                )
            }
        }
    }
}

@Composable
private fun MessageRow(message: ChatMessage) {
    val mine = message.isMine
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = if (mine) 18.dp else 4.dp,
                            bottomEnd = if (mine) 4.dp else 18.dp,
                        ),
                    )
                    .background(if (mine) DarrbiTheme.colors.onSurface else DarrbiTheme.colors.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                androidx.compose.material3.Text(
                    text = message.content,
                    style = DarrbiTheme.typography.body,
                    color = if (mine) DarrbiTheme.colors.surface else DarrbiTheme.colors.onSurface,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Text(
                    text = formatTime(message.timestampMillis),
                    style = DarrbiTheme.typography.caption,
                    color = DarrbiTheme.colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
                )
                if (mine) StatusTick(message.status)
            }
        }
    }
}

@Composable
private fun StatusTick(status: ChatMessageStatus) {
    val (icon, tint) = when (status) {
        ChatMessageStatus.Sending -> Icons.Filled.Schedule to DarrbiTheme.colors.onSurfaceVariant
        ChatMessageStatus.Sent -> Icons.Filled.Done to DarrbiTheme.colors.onSurfaceVariant
        ChatMessageStatus.Delivered -> Icons.Filled.DoneAll to DarrbiTheme.colors.onSurfaceVariant
        ChatMessageStatus.Read -> Icons.Filled.DoneAll to DarrbiTheme.colors.primary
    }
    androidx.compose.material3.Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .padding(top = 4.dp)
            .size(14.dp),
    )
}

@Composable
private fun QuickReplies(onSelect: (String) -> Unit) {
    val replies = stringArrayResource(R.array.chat_quick_replies)
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(replies) { reply ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarrbiTheme.colors.surfaceVariant)
                    .clickable { onSelect(reply) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                androidx.compose.material3.Text(
                    text = reply,
                    style = DarrbiTheme.typography.bodyMedium,
                    color = DarrbiTheme.colors.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onMediaUnavailable: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarrbiTheme.colors.surface)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircleIconButton(Icons.Filled.Add, stringResource(R.string.chat_attach), onMediaUnavailable)

        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, DarrbiTheme.colors.primary, RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    androidx.compose.material3.Text(
                        text = stringResource(R.string.chat_input_hint),
                        style = DarrbiTheme.typography.body,
                        color = DarrbiTheme.colors.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = DarrbiTheme.typography.body.copy(color = DarrbiTheme.colors.onSurface),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(DarrbiTheme.colors.primary),
                    maxLines = 4,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { onSend() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (value.isBlank()) {
            CircleIconButton(Icons.Filled.Image, stringResource(R.string.chat_send_image), onMediaUnavailable)
            CircleIconButton(Icons.Filled.Mic, stringResource(R.string.chat_record_voice), onMediaUnavailable)
        } else {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(DarrbiTheme.colors.primary)
                    .clickable(onClick = onSend),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.chat_send),
                    tint = DarrbiTheme.colors.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun CircleIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = DarrbiTheme.colors.onSurface,
            modifier = Modifier.size(24.dp),
        )
    }
}

private fun formatTime(millis: Long): String {
    if (millis <= 0L) return ""
    return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(millis))
}
