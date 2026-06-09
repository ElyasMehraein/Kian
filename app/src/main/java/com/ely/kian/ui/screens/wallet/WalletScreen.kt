package com.ely.kian.ui.screens.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ely.kian.ui.theme.Accent
import com.ely.kian.ui.theme.Ink
import com.ely.kian.ui.theme.Line
import com.ely.kian.ui.theme.Panel

@Composable
fun WalletScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Text(
            text = "Assets",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Ink,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                SectionHeader("Token Batches (UTXOs)")
                BalanceCard(balance = "1250", symbol = "KG_SUGAR")
            }

            item {
                SectionHeader("Spendable Entries")
            }
            items(listOf("Batch #123", "Batch #124")) { entry ->
                EntryItem(name = entry, amount = "50")
            }

            item {
                SectionHeader("Recent Transfers")
            }
            items(listOf("Transferred to Alice", "Received from Bob", "Minted New Batch")) { activity ->
                ActivityItem(title = activity, date = "Oct 24, 2023", amount = "10")
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        color = Ink,
        modifier = Modifier.padding(16.dp)
    )
}

@Composable
fun BalanceCard(balance: String, symbol: String) {
    Surface(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        color = Accent,
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(text = "Total Holding", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(text = balance, color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = symbol, color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(bottom = 6.dp))
            }
        }
    }
}

@Composable
fun EntryItem(name: String, amount: String) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = name, fontWeight = FontWeight.Medium, color = Ink)
        Text(text = "$amount Units", color = Ink, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ActivityItem(title: String, date: String, amount: String) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = title, fontWeight = FontWeight.Medium, color = Ink)
                Text(text = date, fontSize = 12.sp, color = Color.Gray)
            }
            Text(text = amount, fontWeight = FontWeight.Bold, color = Ink)
        }
        HorizontalDivider(color = Line)
    }
}
