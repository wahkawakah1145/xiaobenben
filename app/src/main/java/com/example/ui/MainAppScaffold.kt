package com.example.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.data.LedgerRepository
import com.example.service.NotificationBridge
import com.example.ui.theme.SmartLedgerTheme
import com.example.viewmodel.LedgerViewModel
import kotlinx.coroutines.launch

const val ROUTE_LEDGER = "ledger"
const val ROUTE_ANALYTICS = "analytics"
const val ROUTE_SETTINGS = "settings"

const val ROUTE_SETTINGS_PARSER = "settings/parser"
const val ROUTE_SETTINGS_CURRENCY = "settings/currency"
const val ROUTE_SETTINGS_IMPORT = "settings/import"
const val ROUTE_SETTINGS_WEBDAV = "settings/webdav"
const val ROUTE_SETTINGS_APPEARANCE = "settings/appearance"

private data class TopDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val topDestinations = listOf(
    TopDestination(
        route = ROUTE_LEDGER,
        labelRes = R.string.nav_ledger,
        selectedIcon = Icons.Rounded.ReceiptLong,
        unselectedIcon = Icons.Outlined.ReceiptLong,
        testTag = "nav_ledger_tab"
    ),
    TopDestination(
        route = ROUTE_ANALYTICS,
        labelRes = R.string.nav_analytics,
        selectedIcon = Icons.Rounded.PieChart,
        unselectedIcon = Icons.Outlined.PieChart,
        testTag = "nav_analytics_tab"
    ),
    TopDestination(
        route = ROUTE_SETTINGS,
        labelRes = R.string.nav_settings,
        selectedIcon = Icons.Rounded.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_settings_tab"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    val viewModel: LedgerViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                LedgerViewModel(repository = LedgerRepository(app))
            }
        }
    )

    val appearance by viewModel.appearanceState.collectAsStateWithLifecycle()

    SmartLedgerTheme(appearanceSettings = appearance) {
        Surface {
            val context = LocalContext.current
            val lifecycleOwner = LocalLifecycleOwner.current
            var isListenerEnabled by remember {
                mutableStateOf(NotificationBridge.isNotificationListenerEnabled(context))
            }
            var isAccessibilityEnabled by remember {
                mutableStateOf(NotificationBridge.isAccessibilityServiceEnabled(context))
            }
            var isIgnoringBattery by remember {
                mutableStateOf(NotificationBridge.isIgnoringBatteryOptimizations(context))
            }

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        isListenerEnabled = NotificationBridge.isNotificationListenerEnabled(context)
                        isAccessibilityEnabled = NotificationBridge.isAccessibilityServiceEnabled(context)
                        isIgnoringBattery = NotificationBridge.isIgnoringBatteryOptimizations(context)
                        NotificationBridge.requestListenerRebind(context)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            val transactionsState by viewModel.transactionsState.collectAsStateWithLifecycle()
            val inquiries by viewModel.inquiriesState.collectAsStateWithLifecycle()
            val settings by viewModel.settingsState.collectAsStateWithLifecycle()
            val exchangeRates by viewModel.exchangeRateState.collectAsStateWithLifecycle()
            val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()

            LaunchedEffect(settings.rootKeepAliveEnabled) {
                viewModel.checkRootStatus()
                if (settings.rootKeepAliveEnabled) {
                    viewModel.applyRootKeepAlive(context)
                }
            }

            val snackbarHostState = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()

            LaunchedEffect(actionMessage) {
                val msg = actionMessage
                if (msg != null) {
                    snackbarHostState.showSnackbar(msg)
                    viewModel.clearActionMessage()
                }
            }

            val navController = rememberNavController()
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_LEDGER
            val isTopLevelRoute = topDestinations.any { it.route == currentRoute }

            val navigateTab: (String) -> Unit = { route ->
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }

            val currentTitle = when (currentRoute) {
                ROUTE_ANALYTICS -> "收支分析"
                ROUTE_SETTINGS -> "设置"
                ROUTE_SETTINGS_PARSER -> "通知识别与规则"
                ROUTE_SETTINGS_CURRENCY -> "币种与汇率"
                ROUTE_SETTINGS_IMPORT -> "手动导入"
                ROUTE_SETTINGS_WEBDAV -> "WebDAV 备份同步"
                ROUTE_SETTINGS_APPEARANCE -> "外观与布局"
                else -> stringResource(R.string.app_name)
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useNavigationRail = maxWidth >= 600.dp

                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Text(
                                    text = currentTitle,
                                    style = MaterialTheme.typography.titleLarge
                                )
                            },
                            navigationIcon = {
                                if (!isTopLevelRoute) {
                                    IconButton(
                                        onClick = { navController.navigateUp() },
                                        modifier = Modifier.testTag("top_bar_back_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                            contentDescription = "返回"
                                        )
                                    }
                                }
                            }
                        )
                    },
                    bottomBar = {
                        if (!useNavigationRail && isTopLevelRoute) {
                            NavigationBar {
                                topDestinations.forEach { dest ->
                                    val selected = currentRoute == dest.route
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { navigateTab(dest.route) },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                contentDescription = stringResource(dest.labelRes)
                                            )
                                        },
                                        label = { Text(stringResource(dest.labelRes)) },
                                        modifier = Modifier.testTag(dest.testTag)
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (useNavigationRail && isTopLevelRoute) {
                            NavigationRail {
                                topDestinations.forEach { dest ->
                                    val selected = currentRoute == dest.route
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = { navigateTab(dest.route) },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                contentDescription = stringResource(dest.labelRes)
                                            )
                                        },
                                        label = { Text(stringResource(dest.labelRes)) },
                                        modifier = Modifier.testTag(dest.testTag)
                                    )
                                }
                            }
                        }

                        NavHost(
                            navController = navController,
                            startDestination = ROUTE_LEDGER,
                            modifier = Modifier.weight(1f)
                        ) {
                            composable(ROUTE_LEDGER) {
                                LedgerHomeScreen(
                                    transactionsState = transactionsState,
                                    inquiries = inquiries,
                                    settings = settings,
                                    appearance = appearance,
                                    exchangeRates = exchangeRates,
                                    isListenerEnabled = isListenerEnabled,
                                    viewModel = viewModel,
                                    onNavigateToParser = { navController.navigate(ROUTE_SETTINGS_PARSER) }
                                )
                            }
                            composable(ROUTE_ANALYTICS) {
                                AnalyticsScreen(
                                    transactionsState = transactionsState,
                                    settings = settings,
                                    exchangeRates = exchangeRates,
                                    viewModel = viewModel
                                )
                            }
                            composable(ROUTE_SETTINGS) {
                                SettingsScreen(
                                    settings = settings,
                                    isListenerEnabled = isListenerEnabled,
                                    isAccessibilityEnabled = isAccessibilityEnabled,
                                    isIgnoringBattery = isIgnoringBattery,
                                    viewModel = viewModel,
                                    onShowMessage = { msg ->
                                        scope.launch { snackbarHostState.showSnackbar(msg) }
                                    },
                                    onOpenParser = { navController.navigate(ROUTE_SETTINGS_PARSER) },
                                    onOpenCurrency = { navController.navigate(ROUTE_SETTINGS_CURRENCY) },
                                    onOpenImport = { navController.navigate(ROUTE_SETTINGS_IMPORT) },
                                    onOpenWebDav = { navController.navigate(ROUTE_SETTINGS_WEBDAV) },
                                    onOpenAppearance = { navController.navigate(ROUTE_SETTINGS_APPEARANCE) }
                                )
                            }
                            composable(ROUTE_SETTINGS_PARSER) {
                                ParserHubScreen(
                                    viewModel = viewModel,
                                    isListenerEnabled = isListenerEnabled,
                                    onShowMessage = { msg ->
                                        scope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                )
                            }
                            composable(ROUTE_SETTINGS_CURRENCY) {
                                CurrencySettingsScreen(
                                    settings = settings,
                                    exchangeRates = exchangeRates,
                                    viewModel = viewModel
                                )
                            }
                            composable(ROUTE_SETTINGS_IMPORT) {
                                ManualImportScreen(
                                    viewModel = viewModel,
                                    onShowMessage = { msg ->
                                        scope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                )
                            }
                            composable(ROUTE_SETTINGS_WEBDAV) {
                                WebDavSettingsScreen(
                                    viewModel = viewModel,
                                    onShowMessage = { msg ->
                                        scope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                )
                            }
                            composable(ROUTE_SETTINGS_APPEARANCE) {
                                AppearanceSettingsScreen(
                                    appearance = appearance,
                                    viewModel = viewModel
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
