package com.ely.kian.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ely.kian.ui.theme.KianTheme

@Composable
fun AppMenuButton(
    modifier: Modifier = Modifier,
    onOpenMenu: () -> Unit
) {
    val kianColors = KianTheme.colors
    
    Box(
        modifier = modifier
            .size(48.dp)
            .shadow(8.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(kianColors.ink)
            .clickable { onOpenMenu() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "≡",
            color = kianColors.canvas,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.offset(y = (-2).dp)
        )
    }
}

@Composable
fun AppMenuDialog(
    isOpen: Boolean,
    accountMode: String,
    onAccountModeChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val kianColors = KianTheme.colors
    
    if (isOpen) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onDismiss() }
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.TopEnd
            ) {
                Column(
                    modifier = Modifier
                        .padding(top = 80.dp, end = 16.dp, start = 16.dp)
                        .widthIn(max = 320.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(kianColors.canvas)
                        .clickable(enabled = false) { }
                        .padding(20.dp)
                ) {
                    Text(
                        text = "Application menu",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = kianColors.ink,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    MenuItem(
                        icon = Icons.Default.Wifi,
                        label = "Relay Management",
                        onClick = { onNavigate("relays"); onDismiss() }
                    )
                    MenuItem(
                        icon = Icons.Default.History,
                        label = "Pending Events",
                        onClick = { onNavigate("pending"); onDismiss() }
                    )
                    MenuItem(
                        icon = Icons.Default.VpnKey,
                        label = "Private Key Management",
                        onClick = { onNavigate("private-key"); onDismiss() }
                    )
                    MenuItem(
                        icon = Icons.Default.Backup,
                        label = "Backups",
                        onClick = { onNavigate("backups"); onDismiss() }
                    )
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = kianColors.line)
                    
                    Text(
                        text = "Account Mode",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = kianColors.ink.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(kianColors.panel)
                            .padding(4.dp)
                    ) {
                        AccountModeButton(
                            label = "Business",
                            selected = accountMode == "business",
                            modifier = Modifier.weight(1f),
                            onClick = { onAccountModeChange("business") }
                        )
                        AccountModeButton(
                            label = "Merchant",
                            selected = accountMode == "merchant",
                            modifier = Modifier.weight(1f),
                            onClick = { onAccountModeChange("merchant") }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    MenuItem(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        label = "Logout",
                        destructive = true,
                        onClick = { onNavigate("logout"); onDismiss() }
                    )
                }
            }
        }
    }
}

@Composable
fun MenuItem(
    icon: ImageVector,
    label: String,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val kianColors = KianTheme.colors
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (destructive) Color.Red else kianColors.ink,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            fontSize = 16.sp,
            color = if (destructive) Color.Red else kianColors.ink,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun AccountModeButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val kianColors = KianTheme.colors
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) kianColors.canvas else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) kianColors.ink else kianColors.ink.copy(alpha = 0.5f)
        )
    }
}
