package com.ely.kian.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.ely.kian.R
import com.ely.kian.ui.theme.KianTheme

@Composable
fun LogoutConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onBackup: () -> Unit
) {
    val kianColors = KianTheme.colors
    
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = kianColors.canvas,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = kianColors.danger,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.sign_out),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = kianColors.ink
                )
            }
        },
        text = {
            Text(
                text = stringResource(R.string.logout_warning),
                fontSize = 15.sp,
                color = kianColors.muted,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
        },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onBackup,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = kianColors.accent)
                ) {
                    Text(stringResource(R.string.backup_database), fontWeight = FontWeight.SemiBold)
                }
                TextButton(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = kianColors.danger)
                ) {
                    Text(stringResource(R.string.logout_wipe_data), fontWeight = FontWeight.Bold)
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.cancel), color = kianColors.ink)
                }
            }
        }
    )
}
