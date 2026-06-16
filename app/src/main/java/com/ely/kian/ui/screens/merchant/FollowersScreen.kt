package com.ely.kian.ui.screens.merchant

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ely.kian.R
import com.ely.kian.ui.components.InitialAvatar
import com.ely.kian.ui.components.ScreenHeader
import com.ely.kian.ui.theme.KianTheme

@Composable
fun FollowersScreen(
    viewModel: FollowersViewModel,
    onBack: () -> Unit,
    onProfileClick: (String) -> Unit
) {
    val followers by viewModel.followers.collectAsState(initial = emptyList())
    val kianColors = KianTheme.colors

    Surface(modifier = Modifier.fillMaxSize(), color = kianColors.canvas) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(
                title = stringResource(R.string.followers),
                onBack = onBack
            )

            if (followers.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_followers_yet),
                        color = kianColors.muted
                    )
                }
            } else {
                LazyColumn {
                    items(followers) { profile ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onProfileClick(profile.pubkey) }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            InitialAvatar(
                                name = profile.displayName ?: profile.name ?: "",
                                pictureUrl = profile.picture,
                                size = 48.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = profile.displayName ?: profile.name ?: stringResource(R.string.unknown),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = kianColors.ink
                                )
                                profile.about?.let {
                                    Text(
                                        text = it,
                                        fontSize = 14.sp,
                                        color = kianColors.muted,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = kianColors.line.copy(alpha = 0.5f),
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }
    }
}
