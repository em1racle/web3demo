package dev.web3demo.androidapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.navigation.material.ExperimentalMaterialNavigationApi
import com.google.accompanist.navigation.material.ModalBottomSheetLayout
import com.google.accompanist.navigation.material.rememberBottomSheetNavigator
import com.reown.appkit.ui.appKitGraph
import com.reown.appkit.ui.openAppKit
import dev.web3demo.chain.AppResult
import dev.web3demo.chain.EthereumRpcClient
import dev.web3demo.chain.TokenRepository
import dev.web3demo.wallet.WalletSession
import kotlinx.coroutines.launch

// Chainlink's official Sepolia testnet LINK token — same contract verified live in
// shared/src/jvmTest/.../TokenRepositoryLiveTest.
private const val LINK_CONTRACT = "0x779877A7B0D9E8603169DdbD7836e478b4624789"

@OptIn(ExperimentalMaterialNavigationApi::class)
@Composable
fun PortfolioScreen(gateway: ReownWalletGateway) {
    // AppKit's connect modal is presented as an Accompanist bottom sheet destination, which
    // needs its own Navigator registered on the NavController plus a ModalBottomSheetLayout
    // hosting the NavHost — a plain rememberNavController() crashes with "Could not find
    // Navigator with name BottomSheetNavigator" the moment appKitGraph is added.
    val bottomSheetNavigator = rememberBottomSheetNavigator()
    val navController = rememberNavController(bottomSheetNavigator)

    ModalBottomSheetLayout(bottomSheetNavigator = bottomSheetNavigator) {
        NavHost(navController = navController, startDestination = "portfolio") {
            appKitGraph(navController)
            composable("portfolio") {
                PortfolioContent(gateway, navController)
            }
        }
    }
}

@Composable
private fun PortfolioContent(
    gateway: ReownWalletGateway,
    navController: NavHostController,
) {
    val session by gateway.session.collectAsState()
    val scope = rememberCoroutineScope()
    // One client for the screen's lifetime, not one per session transition — EthereumRpcClient's
    // default HttpClient isn't closed automatically, so recreating it on every connect/disconnect
    // leaked an HTTP engine each time. See docs/review.md #3.
    val repository = remember { TokenRepository(EthereumRpcClient()) }
    var balanceText by remember { mutableStateOf<String?>(null) }
    var balanceError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(session) {
        val connected = session as? WalletSession.Connected ?: return@LaunchedEffect
        balanceError = null
        balanceText = null
        when (val metadata = repository.fetchMetadata(LINK_CONTRACT)) {
            is AppResult.Ok -> {
                when (
                    val balance =
                        repository.fetchBalance(
                            LINK_CONTRACT,
                            connected.account,
                            metadata.value.decimals,
                        )
                ) {
                    is AppResult.Ok -> balanceText = "${balance.value.formatted} ${metadata.value.symbol}"
                    is AppResult.Err -> balanceError = "Balance read failed: ${balance.error}"
                }
            }
            is AppResult.Err -> balanceError = "Token metadata read failed: ${metadata.error}"
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Portfolio", style = MaterialTheme.typography.headlineMedium)

        when (val current = session) {
            is WalletSession.Disconnected -> {
                Text("No wallet connected.", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { navController.openAppKit() }) {
                    Text("Connect Wallet")
                }
            }
            is WalletSession.Connecting -> {
                Text("Connecting…", style = MaterialTheme.typography.bodyMedium)
            }
            is WalletSession.Connected -> {
                Text(current.account, style = MaterialTheme.typography.bodySmall)
                Text(current.chainId, style = MaterialTheme.typography.labelSmall)

                Text("LINK balance (Sepolia)", style = MaterialTheme.typography.titleSmall)
                when {
                    balanceError != null -> Text(balanceError!!, color = MaterialTheme.colorScheme.error)
                    balanceText != null -> Text(balanceText!!, style = MaterialTheme.typography.bodyLarge)
                    else -> Text("Loading…", style = MaterialTheme.typography.bodyMedium)
                }

                Button(onClick = { scope.launch { gateway.disconnect() } }) {
                    Text("Disconnect")
                }
            }
        }
    }
}
