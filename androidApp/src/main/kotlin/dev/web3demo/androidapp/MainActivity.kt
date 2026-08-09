package dev.web3demo.androidapp

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.reown.appkit.client.AppKit
import dev.web3demo.realtimefeed.ConnectionState
import dev.web3demo.realtimefeed.PersistedPriceCache
import dev.web3demo.realtimefeed.PriceFeedClient
import dev.web3demo.realtimefeed.PriceTick

// BiometricPrompt needs a FragmentActivity (it hosts an invisible fragment internally to receive
// the auth callback pre-API 28) — plain ComponentActivity isn't enough for the Wallet tab.
class MainActivity : FragmentActivity() {
    private val walletGateway = ReownWalletGateway()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppKit.register(this)
        setContent {
            MaterialTheme {
                AppRoot(activity = this, walletGateway = walletGateway)
            }
        }
    }

    override fun onDestroy() {
        AppKit.unregister()
        super.onDestroy()
    }
}

@Composable
fun AppRoot(
    activity: MainActivity,
    walletGateway: ReownWalletGateway,
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {},
                    label = { Text("Prices") },
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {},
                    label = { Text("Wallet") },
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {},
                    label = { Text("Portfolio") },
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> PriceListScreen(activity)
                1 -> WalletScreen(activity)
                else -> PortfolioScreen(walletGateway)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceListScreen(context: android.content.Context) {
    val cache = remember { PersistedPriceCache(AndroidKeyValueStore(context)) }
    val cachedSnapshot = remember { cache.load() }
    val client = remember { PriceFeedClient(listOf("btcusdt", "ethusdt", "solusdt")) }
    val state by client.state.collectAsState()
    val liveSnapshots by client.snapshots.collectAsState()

    LaunchedEffect(Unit) { client.start() }
    DisposableEffect(Unit) { onDispose { client.stop() } }

    // Persist every live update so a relaunch has something to show before the first message
    // arrives again — see PersistedPriceCache in :shared for the (shared) cache policy itself.
    LaunchedEffect(liveSnapshots) {
        if (liveSnapshots.isNotEmpty()) cache.save(liveSnapshots)
    }

    val rows =
        (if (liveSnapshots.isNotEmpty()) liveSnapshots else cachedSnapshot)
            .values.sortedBy { it.symbol }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Prices") },
                actions = { ConnectionBadge(state) },
            )
        },
    ) { padding ->
        // `key = { it.symbol }` gives each row a stable identity, so recomposition only touches
        // rows whose PriceTick actually changed — the Compose analog of SwiftUI's `.equatable()`.
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(rows, key = { it.symbol }) { tick ->
                PriceRow(tick)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun PriceRow(tick: PriceTick) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(tick.symbol, style = MaterialTheme.typography.titleMedium)
        Text("%.2f".format(tick.price), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ConnectionBadge(state: ConnectionState) {
    val (color, label) =
        when (state) {
            is ConnectionState.Connected -> Color(0xFF34C759) to "Live"
            is ConnectionState.Connecting -> Color(0xFFFFCC00) to "Connecting…"
            is ConnectionState.Reconnecting -> Color(0xFFFF9500) to "Reconnecting #${state.attempt}"
            else -> Color(0xFFFF3B30) to "Offline"
        }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 16.dp)) {
        Box(modifier = Modifier.size(8.dp).background(color, shape = CircleShape))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
