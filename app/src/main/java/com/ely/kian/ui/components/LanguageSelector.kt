package com.ely.kian.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ely.kian.R
import com.ely.kian.ui.theme.KianTheme

@Composable
fun LanguageSelector(
    currentLanguage: String,
    onLanguageChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val kianColors = KianTheme.colors

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(kianColors.panel)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LanguageOption(
            label = stringResource(R.string.english),
            isSelected = currentLanguage == "en",
            onClick = { onLanguageChange("en") }
        )
        LanguageOption(
            label = stringResource(R.string.persian),
            isSelected = currentLanguage == "fa",
            onClick = { onLanguageChange("fa") }
        )
    }
}

@Composable
private fun LanguageOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val kianColors = KianTheme.colors
    
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) kianColors.accent else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.White else kianColors.ink,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
