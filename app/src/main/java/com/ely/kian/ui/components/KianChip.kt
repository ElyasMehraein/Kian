package com.ely.kian.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ely.kian.ui.theme.KianTheme

@Composable
fun KianChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color? = null,
    contentColor: Color? = null
) {
    val kianColors = KianTheme.colors
    
    val finalBgColor = backgroundColor ?: (if (selected) kianColors.ink else kianColors.canvas)
    val finalContentColor = contentColor ?: (if (selected) kianColors.canvas else kianColors.ink)

    Surface(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(50), // Rounded-full
        color = finalBgColor,
        border = if (selected || backgroundColor != null) null else BorderStroke(1.dp, kianColors.line),
        contentColor = finalContentColor
    ) {
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = text,
                fontSize = 14.sp
            )
        }
    }
}
