package com.ely.kian.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ely.kian.ui.theme.KianTheme

@Composable
fun MerchantCard(
    name: String,
    bio: String,
    rating: String,
    distance: String,
    pictureUrl: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val kianColors = KianTheme.colors
    
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, kianColors.line, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = kianColors.canvas
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InitialAvatar(name = name, pictureUrl = pictureUrl, size = 48.dp)
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = kianColors.ink
                )
                Text(
                    text = bio,
                    fontSize = 14.sp,
                    color = kianColors.ink.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "★ $rating",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = kianColors.ink
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "• $distance",
                        fontSize = 12.sp,
                        color = kianColors.ink.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
