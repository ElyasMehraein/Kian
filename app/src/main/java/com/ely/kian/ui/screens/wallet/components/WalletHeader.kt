package com.ely.kian.ui.screens.wallet.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ely.kian.R
import com.ely.kian.ui.components.ScreenHeader as KianScreenHeader
import com.ely.kian.ui.theme.KianColors

@Composable
fun WalletHeader(
    balancesCount: Int,
    colors: KianColors
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            KianScreenHeader(
                title = stringResource(R.string.my_wallet),
                subtitle = stringResource(R.string.wallet_desc),
                modifier = Modifier.weight(1f)
            )
            
            Surface(
                shape = CircleShape,
                color = colors.panel,
                modifier = Modifier.padding(end = 20.dp).size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = stringResource(R.string.scan), tint = colors.ink)
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
            color = colors.accent,
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(stringResource(R.string.total_assets), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                Text(
                    text = stringResource(R.string.categories_count, balancesCount), 
                    color = Color.White, 
                    fontSize = 28.sp, 
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.utxo_secured), 
                        color = Color.White.copy(alpha = 0.8f), 
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
