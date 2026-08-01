package com.ely.kian.ui.screens.chat.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ely.kian.ui.theme.KianColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubbleLayout(
    isMine: Boolean,
    colors: KianColors,
    onLongClick: () -> Unit,
    onDoubleClick: () -> Boolean = { false },
    onSwipeToReply: (() -> Unit)? = null,
    reactions: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var hapticTriggered by remember { mutableStateOf(false) }
    
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var pressOffset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> pressOffset = interaction.pressPosition
            }
        }
    }
    
    // Bubble scale for click/press
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bubble_scale"
    )

    // Ripple/Gradient animation state
    val rippleProgress = remember { Animatable(0f) }
    val clickRippleProgress = remember { Animatable(0f) }
    
    LaunchedEffect(isPressed) {
        if (isPressed) {
            rippleProgress.snapTo(0f)
            rippleProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 600, easing = LinearOutSlowInEasing)
            )
        } else {
            rippleProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 200)
            )
        }
    }

    val heartAlpha = remember { Animatable(0f) }
    val heartScale = remember { Animatable(0f) }

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
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    color = bubbleColor,
                    shape = shape,
                    tonalElevation = if (isMine) 0.dp else 1.dp,
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .scale(scale)
                        .combinedClickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { 
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                scope.launch {
                                    clickRippleProgress.snapTo(0f)
                                    clickRippleProgress.animateTo(
                                        targetValue = 1f,
                                        animationSpec = tween(400, easing = LinearOutSlowInEasing)
                                    )
                                }
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onLongClick()
                            },
                            onDoubleClick = {
                                val shouldAnimate = onDoubleClick()
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                
                                if (shouldAnimate) {
                                    scope.launch {
                                        heartAlpha.snapTo(0f)
                                        heartScale.snapTo(0f)
                                        
                                        launch {
                                            heartAlpha.animateTo(
                                                1f, 
                                                animationSpec = tween(150, easing = LinearOutSlowInEasing)
                                            )
                                            delay(400)
                                            heartAlpha.animateTo(
                                                0f, 
                                                animationSpec = tween(200, easing = FastOutLinearInEasing)
                                            )
                                        }
                                        
                                        launch {
                                            heartScale.animateTo(
                                                1.8f, 
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                                    stiffness = Spring.StiffnessLow
                                                )
                                            )
                                            heartScale.animateTo(
                                                1.4f,
                                                animationSpec = tween(250)
                                            )
                                        }
                                    }
                                }
                            }
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .drawBehind {
                                // Long press ripple
                                if (rippleProgress.value > 0f) {
                                    val maxRadius = size.maxDimension * 1.5f
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            0.0f to Color.White.copy(alpha = 0.45f),
                                            1.0f to Color.Transparent,
                                            center = pressOffset,
                                            radius = maxRadius * rippleProgress.value
                                        ),
                                        radius = maxRadius * rippleProgress.value,
                                        center = pressOffset,
                                        alpha = (1f - rippleProgress.value * 0.5f)
                                    )
                                }
                                // Quick click pulse
                                if (clickRippleProgress.value > 0f && clickRippleProgress.value < 1f) {
                                    drawCircle(
                                        color = Color.White.copy(alpha = 0.25f * (1f - clickRippleProgress.value)),
                                        center = pressOffset,
                                        radius = size.maxDimension * clickRippleProgress.value
                                    )
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        content()
                    }
                }

                // Animated Heart Overlay
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = Color(0xFFFF3B30),
                    modifier = Modifier
                        .size(48.dp)
                        .scale(heartScale.value)
                        .alpha(heartAlpha.value)
                )
                
                Box(
                    modifier = Modifier
                        .matchParentSize()
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .offset(x = (-4).dp, y = 10.dp)
                    ) {
                        reactions()
                    }
                }
            }
        }
    }
}
