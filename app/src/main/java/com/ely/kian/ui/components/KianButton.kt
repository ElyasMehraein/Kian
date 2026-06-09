package com.ely.kian.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ely.kian.ui.theme.Accent
import com.ely.kian.ui.theme.AccentSoft
import com.ely.kian.ui.theme.Canvas
import com.ely.kian.ui.theme.Ink
import com.ely.kian.ui.theme.Line

enum class ButtonType {
    Primary, Secondary, Soft
}

@Composable
fun KianButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    type: ButtonType = ButtonType.Primary,
    enabled: Boolean = true
) {
    val containerColor = when (type) {
        ButtonType.Primary -> Ink
        ButtonType.Secondary -> Canvas
        ButtonType.Soft -> AccentSoft
    }

    val contentColor = when (type) {
        ButtonType.Primary -> Canvas
        ButtonType.Secondary -> Ink
        ButtonType.Soft -> Accent
    }

    val border = if (type == ButtonType.Secondary) {
        BorderStroke(1.dp, Line)
    } else {
        null
    }

    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = Line,
            disabledContentColor = Color.Gray
        ),
        border = border,
        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 16.dp)
    ) {
        Text(text = text)
    }
}
