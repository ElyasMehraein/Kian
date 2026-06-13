package com.ely.kian.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ely.kian.ui.theme.KianTheme

@Composable
fun InitialAvatar(
    name: String,
    modifier: Modifier = Modifier,
    pictureUrl: String? = null,
    size: Dp = 40.dp
) {
    val kianColors = KianTheme.colors
    val initials = name.ifBlank { "?" }.take(1).uppercase()
    
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(kianColors.accentSoft),
        contentAlignment = Alignment.Center
    ) {
        if (!pictureUrl.isNullOrBlank()) {
            AsyncImage(
                model = pictureUrl,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = initials,
                color = kianColors.accent,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.4).sp
            )
        }
    }
}
