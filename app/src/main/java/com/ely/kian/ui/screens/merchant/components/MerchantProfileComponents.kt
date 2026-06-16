package com.ely.kian.ui.screens.merchant.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ely.kian.R
import com.ely.kian.data.local.entities.Review
import com.ely.kian.ui.theme.KianTheme

@Composable
fun ProfileStat(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: (() -> Unit)? = null
) {
    val colors = KianTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = Color(0xFFFFB800), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = colors.ink)
        }
        Text(text = label, fontSize = 13.sp, color = colors.muted, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ReviewCard(review: Review) {
    val kianColors = KianTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(kianColors.panel, RoundedCornerShape(24.dp))
            .border(1.dp, kianColors.line, RoundedCornerShape(24.dp))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = review.authorName ?: stringResource(R.string.anonymous_user), 
                fontWeight = FontWeight.Bold, 
                color = kianColors.ink, 
                modifier = Modifier.weight(1f)
            )
            repeat(review.rating) {
                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB800), modifier = Modifier.size(16.dp))
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = review.comment ?: "", 
            fontSize = 14.sp, 
            color = kianColors.ink.copy(alpha = 0.7f), 
            lineHeight = 22.sp
        )
    }
}

@Composable
fun ReviewDialog(
    initialRating: Int,
    initialComment: String,
    onDismiss: () -> Unit,
    onConfirm: (Int, String) -> Unit
) {
    var rating by remember { mutableIntStateOf(initialRating) }
    var comment by remember { mutableStateOf(initialComment) }
    val kianColors = KianTheme.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = kianColors.panel,
        title = { Text(stringResource(R.string.rate_merchant), color = kianColors.ink) },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(5) { index ->
                        val starIndex = index + 1
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = if (starIndex <= rating) Color(0xFFFFB800) else kianColors.line,
                            modifier = Modifier
                                .size(40.dp)
                                .clickable { rating = starIndex }
                        )
                    }
                }
                
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    placeholder = { Text(stringResource(R.string.write_review_here), color = kianColors.muted) },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = kianColors.ink,
                        unfocusedTextColor = kianColors.ink,
                        focusedBorderColor = kianColors.accent,
                        unfocusedBorderColor = kianColors.line
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(rating, comment) }) {
                Text(stringResource(R.string.post_review), color = kianColors.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = kianColors.muted)
            }
        }
    )
}
