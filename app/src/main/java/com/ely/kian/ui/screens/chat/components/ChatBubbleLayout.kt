package com.ely.kian.ui.screens.chat.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ely.kian.ui.theme.KianColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubbleLayout(
    isMine: Boolean,
    colors: KianColors,
    onLongClick: () -> Unit,
    onDoubleClick: () -> Unit = {},
    onSwipeToReply: (() -> Unit)? = null,
    reactions: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var hapticTriggered by remember { mutableStateOf(false) }
    
    val replyIconAlpha by remember { 
        derivedStateOf { (offsetX.value / 80f).coerceIn(0f, 1f) } 
    }
    val replyIconScale by remember { 
        derivedStateOf { (0.5f + (offsetX.value / 160f)).coerceIn(0.5f, 1f) } 
    }

    val alignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (isMine) colors.accent else colors.panel
    
    val shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (isMine) 18.dp else 4.dp,
        bottomEnd = if (isMine) 4.dp else 18.dp
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .pointerInput(Unit) {
                if (onSwipeToReply != null) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX.value > 80f) {
                                onSwipeToReply()
                            }
                            scope.launch {
                                offsetX.animateTo(0f)
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                offsetX.animateTo(0f)
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            scope.launch {
                                // Only allow dragging to the right for reply
                                val newOffset = (offsetX.value + dragAmount).coerceIn(0f, 120f)
                                offsetX.snapTo(newOffset)
                                
                                if (newOffset >= 80f && !hapticTriggered) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    hapticTriggered = true
                                } else if (newOffset < 80f) {
                                    hapticTriggered = false
                                }
                            }
                            if (offsetX.value > 0) {
                                change.consume()
                            }
                        }
                    )
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Reply Icon shown behind the bubble when swiping
        if (onSwipeToReply != null && offsetX.value > 0) {
            Box(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .alpha(replyIconAlpha)
                    .scale(replyIconScale)
            ) {
                Icon(
                    imageVector = Icons.Default.Reply,
                    contentDescription = null,
                    tint = colors.muted
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) },
            contentAlignment = alignment
        ) {
            Box {
                Surface(
                    color = bubbleColor,
                    shape = shape,
                    tonalElevation = if (isMine) 0.dp else 1.dp,
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { },
                            onLongClick = onLongClick,
                            onDoubleClick = onDoubleClick
                        )
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        content()
                    }
                }
                
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = (-4).dp, y = 10.dp) // Float on bottom-left, partially sticking out
                ) {
                    reactions()
                }
            }
        }
    }
}
