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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ely.kian.R
import com.ely.kian.services.GitHubUpdateManager
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
    currentLanguage: String,
    updateResult: GitHubUpdateManager.UpdateResult?,
    updateError: String?,
    isCheckingUpdate: Boolean,
    onAccountModeChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onClearUpdateResult: () -> Unit,
    onOpenUrl: (String) -> Unit,
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
                        text = stringResource(R.string.app_menu),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = kianColors.ink,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    MenuItem(
                        icon = Icons.Default.Wifi,
                        label = stringResource(R.string.relay_management),
                        onClick = { onNavigate("relays"); onDismiss() }
                    )
                    MenuItem(
                        icon = Icons.Default.History,
                        label = stringResource(R.string.pending_events),
                        onClick = { onNavigate("pending"); onDismiss() }
                    )
                    MenuItem(
                        icon = Icons.Default.VpnKey,
                        label = stringResource(R.string.private_key_management),
                        onClick = { onNavigate("private-key"); onDismiss() }
                    )
                    MenuItem(
                        icon = Icons.Default.Backup,
                        label = stringResource(R.string.backups),
                        onClick = { onNavigate("backups"); onDismiss() }
                    )

                    MenuItem(
                        icon = Icons.Default.SystemUpdate,
                        label = stringResource(R.string.check_for_updates),
                        onClick = { onCheckUpdate() }
                    )

                    MenuItem(
                        icon = Icons.Default.Code,
                        label = stringResource(R.string.github_project),
                        onClick = { 
                            onOpenUrl("https://github.com/ElyasMehraein/Kian")
                            onDismiss()
                        }
                    )
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = kianColors.line)

                    Text(
                        text = stringResource(R.string.language),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = kianColors.ink.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LanguageSelector(
                        currentLanguage = currentLanguage,
                        onLanguageChange = { onLanguageChange(it); onDismiss() },
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = kianColors.line)
                    
                    Text(
                        text = stringResource(R.string.account_mode),
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
                            label = stringResource(R.string.business),
                            selected = accountMode == "business",
                            modifier = Modifier.weight(1f),
                            onClick = { onAccountModeChange("business") }
                        )
                        AccountModeButton(
                            label = stringResource(R.string.merchant),
                            selected = accountMode == "merchant",
                            modifier = Modifier.weight(1f),
                            onClick = { onAccountModeChange("merchant") }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    MenuItem(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        label = stringResource(R.string.logout),
                        destructive = true,
                        onClick = { onNavigate("logout"); onDismiss() }
                    )
                }
            }
        }

        if (isCheckingUpdate) {
            AlertDialog(
                onDismissRequest = { },
                confirmButton = { },
                title = { Text(stringResource(R.string.check_for_updates)) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(stringResource(R.string.checking_updates))
                    }
                }
            )
        }

        updateError?.let { error ->
            AlertDialog(
                onDismissRequest = { onClearUpdateResult() },
                title = { Text(stringResource(R.string.check_for_updates)) },
                text = { Text(stringResource(R.string.update_failed) + "\n" + error) },
                confirmButton = {
                    TextButton(onClick = { onClearUpdateResult() }) {
                        Text(stringResource(R.string.confirm))
                    }
                }
            )
        }

        updateResult?.let { result ->
            AlertDialog(
                onDismissRequest = { onClearUpdateResult() },
                title = { Text(stringResource(R.string.check_for_updates)) },
                text = {
                    Text(
                        if (result.isUpdateAvailable) 
                            stringResource(R.string.update_available, result.latestVersion ?: "") 
                        else 
                            stringResource(R.string.update_not_available)
                    )
                },
                confirmButton = {
                    if (result.isUpdateAvailable) {
                        Button(onClick = { onDownloadUpdate(); onClearUpdateResult() }) {
                            Text(stringResource(R.string.download_update))
                        }
                    } else {
                        TextButton(onClick = { onClearUpdateResult() }) {
                            Text(stringResource(R.string.confirm))
                        }
                    }
                },
                dismissButton = {
                    if (result.isUpdateAvailable) {
                        TextButton(onClick = { onClearUpdateResult() }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                }
            )
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
