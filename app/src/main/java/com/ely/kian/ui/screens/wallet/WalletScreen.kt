package com.ely.kian.ui.screens.wallet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ely.kian.R
import com.ely.kian.KianApp
import com.ely.kian.data.repository.BalanceItem
import com.ely.kian.ui.screens.wallet.components.*
import com.ely.kian.ui.theme.KianTheme

@Composable
fun WalletScreen(onSendToken: () -> Unit, onProducerClick: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as KianApp
    val viewModel: WalletViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = WalletViewModel.provideFactory(
            app.container.tokenRepository,
            app.container.productRepository,
            app.container.keyDao
        )
    )

    val balances by viewModel.balances.collectAsState()
    val pending by viewModel.pending.collectAsState()
    val myCategories by viewModel.myCategories.collectAsState()
    val activityFilter = viewModel.activityFilter

    var editingToken by remember { mutableStateOf<BalanceItem?>(null) }
    var showEditSheet by remember { mutableStateOf(false) }

    val filteredPending = remember(pending, activityFilter) {
        if (activityFilter == "all") pending
        else pending.filter { it.status == activityFilter }
    }

    val kianColors = KianTheme.colors

    Scaffold(
        containerColor = kianColors.canvas,
        contentWindowInsets = WindowInsets(0.dp)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                WalletHeader(balancesCount = balances.size, colors = kianColors)
            }

            item {
                SectionHeader(stringResource(R.string.token_balances), Icons.Default.Token, kianColors)
            }

            if (balances.isEmpty()) {
                item { EmptyState(stringResource(R.string.no_assets_found), kianColors) }
            } else {
                items(balances) { item ->
                    BalanceRow(
                        item = item,
                        myCategories = myCategories,
                        onProducerClick = onProducerClick,
                        formatAssetRef = viewModel::formatAssetRef,
                        onToggleShowcase = { assetRef, isShowcase -> viewModel.toggleShowcase(assetRef, isShowcase) },
                        onEdit = { 
                            editingToken = it
                            showEditSheet = true
                        },
                        colors = kianColors
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(24.dp))
                SectionHeader(stringResource(R.string.transfer_activity), Icons.Default.History, kianColors)
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 16.dp, start = 20.dp, end = 20.dp)
                ) {
                    val filters = listOf(
                        "all" to R.string.all,
                        "waiting_mint" to R.string.pending,
                        "fulfilled" to R.string.completed,
                        "rejected" to R.string.failed
                    )
                    items(filters) { (id, labelId) ->
                        FilterChip(
                            label = stringResource(labelId),
                            selected = activityFilter == id,
                            onClick = { viewModel.setFilter(id) },
                            colors = kianColors
                        )
                    }
                }
            }

            if (filteredPending.isEmpty()) {
                item { EmptyState(stringResource(R.string.no_activity_filter), kianColors) }
            } else {
                items(filteredPending) { item ->
                    PendingRow(item, formatAssetRef = viewModel::formatAssetRef, colors = kianColors)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            
            item { Spacer(modifier = Modifier.height(100.dp)) }
        }
    }

    if (showEditSheet && editingToken != null) {
        TokenEditBottomSheet(
            token = editingToken!!,
            allCategories = myCategories,
            onDismiss = { showEditSheet = false },
            onSave = { name, desc, selectedCats ->
                viewModel.updateTokenDetails(editingToken!!.assetRef, name, desc, selectedCats)
                showEditSheet = false
            },
            colors = kianColors
        )
    }
}
