package com.ely.kian.ui.screens.chat.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ely.kian.ui.theme.KianColors

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubbleLayout(
    isMine: Boolean,
    colors: KianColors,
    onLongClick: () -> Unit,
    onDoubleClick: () -> Unit = {},
    reactions: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
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
            .padding(vertical = 4.dp), // Increased vertical padding for floating reactions
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
