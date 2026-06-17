package com.ely.kian.ui.screens.vouchers.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ely.kian.R
import com.ely.kian.data.repository.PendingItem
import com.ely.kian.ui.theme.KianColors

@Composable
fun PendingRow(item: PendingItem, formatAssetRef: (String) -> String, colors: KianColors) {
    val (containerColor, borderColor, textColor, metaColor, labelId, detailId) = when (item.status) {
        "fulfilled" -> HexColorPairIds(colors.successSoft, colors.success, colors.ink, colors.success, R.string.completed, R.string.completed_detail)
        "rejected" -> HexColorPairIds(colors.danger.copy(alpha = 0.1f), colors.danger, colors.ink, colors.danger, R.string.failed, R.string.rejected_detail)
        "offline" -> HexColorPairIds(colors.panel, colors.muted, colors.ink, colors.muted, R.string.queued_offline, R.string.queued_offline_desc)
        else -> HexColorPairIds(colors.warningSoft, colors.warning, colors.ink, colors.warning, R.string.pending, R.string.waiting_issuer_desc)
    }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = item.assetName, 
                    fontSize = 16.sp, 
                    fontWeight = FontWeight.Bold, 
                    color = textColor
                )
                Text(
                    text = stringResource(labelId).uppercase(), 
                    fontSize = 10.sp, 
                    fontWeight = FontWeight.Black, 
                    color = metaColor,
                    letterSpacing = 1.sp
                )
            }
            
            Text(
                text = stringResource(R.string.pending_row_detail, item.amount, stringResource(detailId)),
                fontSize = 14.sp,
                color = colors.muted,
                modifier = Modifier.padding(top = 4.dp)
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = borderColor.copy(alpha = 0.1f))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = null, tint = colors.muted, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.to_recipient, formatAssetRef(item.recipient)), 
                    fontSize = 12.sp, 
                    color = colors.muted
                )
            }
            
            Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Fingerprint, contentDescription = null, tint = colors.muted, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "ID: ${formatAssetRef(item.eventId)}", 
                    fontSize = 12.sp, 
                    color = colors.muted,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

private data class HexColorPairIds(
    val container: Color,
    val border: Color,
    val text: Color,
    val meta: Color,
    val labelId: Int,
    val detailId: Int
)

@Composable
fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit, colors: KianColors) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) colors.accent else colors.panel,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) colors.accent else colors.line)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else colors.muted
        )
    }
}
