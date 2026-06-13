package com.ely.kian.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ely.kian.ui.theme.KianTheme

@Composable
fun KianInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    isError: Boolean = false,
    singleLine: Boolean = true
) {
    val kianColors = KianTheme.colors
    
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = label?.let { { Text(it) } },
        placeholder = { Text(text = placeholder) },
        isError = isError,
        singleLine = singleLine,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = kianColors.accent,
            unfocusedBorderColor = kianColors.line,
            focusedTextColor = kianColors.ink,
            unfocusedTextColor = kianColors.ink,
            cursorColor = kianColors.accent,
            focusedPlaceholderColor = kianColors.ink.copy(alpha = 0.5f),
            unfocusedPlaceholderColor = kianColors.ink.copy(alpha = 0.5f)
        )
    )
}
