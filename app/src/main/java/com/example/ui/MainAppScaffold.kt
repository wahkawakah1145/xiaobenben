package com.example.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.data.LedgerRepository
import com.example.model.PredictiveBackStyle
import com.example.model.TabClickAnimationStyle
import com.example.service.NotificationBridge
import com.example.ui.theme.AppIcons
import com.example.ui.theme.SmartLedgerTheme
import com.example.viewmodel.LedgerViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.abs

const val ROUTE_MAIN_TABS = "main_tabs"
const val ROUTE_LEDGER = "ledger"
const val ROUTE_ANALYTICS = "analytics"
const val ROUTE_SETTINGS = "settings"

const val ROUTE_SETTINGS_LEDGER_MGMT = "settings/ledger_mgmt"
const val ROUTE_SETTINGS_BILL_BEHAVIOR = "settings/bill_behavior"
const val ROUTE_SETTINGS_PARSER = "settings/parser"
const val ROUTE_SETTINGS_AI = "settings/ai"
const val ROUTE_SETTINGS_CURRENCY = "settings/currency"
const val ROUTE_SETTINGS_DATA_HUB = "settings/data_hub"
const val ROUTE_SETTINGS_LOCAL_BACKUP = "settings/local_backup"
const val ROUTE_SETTINGS_DEDUP = "settings/dedup"
const val ROUTE_SETTINGS_STORAGE_OPT = "settings/storage_opt"
const val ROUTE_SETTINGS_APPEARANCE = "settings/appearance"
const val ROUTE_SETTINGS_PERMISSIONS = "settings/permissions"
const val ROUTE_SETTINGS_ABOUT = "settings/about"

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
        selectedIcon = AppIcons.ReceiptLong,
        unselectedIcon = AppIcons.ReceiptLongOutlined,
        testTag = "nav_ledger_tab"
    ),
    TopDestination(
        route = ROUTE_ANALYTICS,
        labelRes = R.string.nav_analytics,
        selectedIcon = AppIcons.PieChart,
        unselectedIcon = AppIcons.PieChartOutlined,
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
fun AppNavigation(
    openLocalBackupRequest: Boolean = false,
    onConsumedLocalBackupRequest: () -> Unit = {}
) {
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
                        viewModel.checkBackupReminderDue(context)
                        viewModel.checkExchangeRateAutoUpdateIfDue()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            val transactionsState by viewModel.transactionsState.collectAsStateWithLifecycle()
            val inquiries by viewModel.inquiriesState.collectAsStateWithLifecycle()
            val settings by viewModel.settingsState.collectAsStateWithLifecycle()
            val exchangeRates by viewModel.exchangeRateState.collectAsStateWithLifecycle()
            val aiConfig by viewModel.aiConfigState.collectAsStateWithLifecycle()
            val storageFootprint by viewModel.storageFootprintState.collectAsStateWithLifecycle()
            val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()

            LaunchedEffect(settings.exchangeRateUpdateFrequency) {
                viewModel.checkExchangeRateAutoUpdateIfDue()
            }

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
            val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_MAIN_TABS
            val isTopLevelRoute = currentRoute == ROUTE_MAIN_TABS

            val pagerState = rememberPagerState(initialPage = 0, pageCount = { topDestinations.size })
            val tabFadeAlpha = remember { Animatable(1f) }
            var pagerBackProgress by remember { mutableFloatStateOf(0f) }

            val navigateTab: (Int) -> Unit = { targetPage ->
                if (pagerState.currentPage != targetPage) {
                    scope.launch {
                        when (appearance.tabClickAnimation) {
                            TabClickAnimationStyle.SLIDE -> {
                                pagerState.animateScrollToPage(
                                    page = targetPage,
                                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                                )
                            }
                            TabClickAnimationStyle.FADE -> {
                                tabFadeAlpha.animateTo(0.35f, tween(90))
                                pagerState.scrollToPage(targetPage)
                                tabFadeAlpha.animateTo(1f, tween(170))
                            }
                            TabClickAnimationStyle.NONE -> {
                                pagerState.scrollToPage(targetPage)
                            }
                        }
                    }
                }
            }

            // On non-home main tab: system back / predictive back returns to tab 0 (Ledger)
            if (isTopLevelRoute && pagerState.currentPage != 0) {
                if (appearance.predictiveBackEnabled) {
                    PredictiveBackHandler(enabled = true) { progress: Flow<BackEventCompat> ->
                        try {
                            progress.collect { event ->
                                pagerBackProgress = event.progress.coerceIn(0f, 1f)
                            }
                            pagerBackProgress = 0f
                            pagerState.animateScrollToPage(0)
                        } catch (_: CancellationException) {
                            pagerBackProgress = 0f
                        }
                    }
                } else {
                    BackHandler(enabled = true) {
                        scope.launch { pagerState.scrollToPage(0) }
                    }
                }
            }

            val currentTitle = when (currentRoute) {
                ROUTE_MAIN_TABS -> when (pagerState.currentPage) {
                    1 -> "收支分析"
                    2 -> "设置"
                    else -> stringResource(R.string.app_name)
                }
                ROUTE_SETTINGS_LEDGER_MGMT -> "账单工具"
                ROUTE_SETTINGS_BILL_BEHAVIOR -> "记账与预算"
                ROUTE_SETTINGS_CURRENCY -> "币种与汇率"
                ROUTE_SETTINGS_DEDUP -> "智能去重"
                ROUTE_SETTINGS_STORAGE_OPT -> "数据空间与存储优化"
                ROUTE_SETTINGS_PARSER -> "通知识别与规则"
                ROUTE_SETTINGS_AI -> "AI理解"
                ROUTE_SETTINGS_DATA_HUB, ROUTE_SETTINGS_LOCAL_BACKUP -> "导入与备份中心"
                ROUTE_SETTINGS_APPEARANCE -> "外观与布局"
                ROUTE_SETTINGS_PERMISSIONS -> "系统权限与进程保活"
                ROUTE_SETTINGS_ABOUT -> "关于"
                else -> stringResource(R.string.app_name)
            }

            LaunchedEffect(openLocalBackupRequest) {
                if (openLocalBackupRequest) {
                    navController.navigate(ROUTE_SETTINGS_LOCAL_BACKUP) {
                        launchSingleTop = true
                    }
                    onConsumedLocalBackupRequest()
                }
            }

            val popWithStyle: () -> Unit = {
                navController.navigateUp()
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
                                        onClick = popWithStyle,
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
                                topDestinations.forEachIndexed { index, dest ->
                                    val selected = pagerState.currentPage == index
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { navigateTab(index) },
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
                                topDestinations.forEachIndexed { index, dest ->
                                    val selected = pagerState.currentPage == index
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = { navigateTab(index) },
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
                            startDestination = ROUTE_MAIN_TABS,
                            modifier = Modifier.weight(1f),
                            enterTransition = {
                                slideInHorizontally(tween(240)) { (it * 0.25f).toInt() } + fadeIn(tween(200))
                            },
                            exitTransition = {
                                slideOutHorizontally(tween(240)) { -(it * 0.12f).toInt() } + fadeOut(tween(180))
                            },
                            popEnterTransition = {
                                if (!appearance.predictiveBackEnabled) {
                                    EnterTransition.None
                                } else {
                                    when (appearance.predictiveBackStyle) {
                                        PredictiveBackStyle.SYSTEM_SCALE ->
                                            scaleIn(tween(220), initialScale = 0.94f) + fadeIn(tween(200))
                                        PredictiveBackStyle.SLIDE_RIGHT ->
                                            slideInHorizontally(tween(240)) { -(it * 0.2f).toInt() } + fadeIn(tween(200))
                                        PredictiveBackStyle.FADE ->
                                            fadeIn(tween(200))
                                    }
                                }
                            },
                            popExitTransition = {
                                if (!appearance.predictiveBackEnabled) {
                                    ExitTransition.None
                                } else {
                                    when (appearance.predictiveBackStyle) {
                                        PredictiveBackStyle.SYSTEM_SCALE ->
                                            scaleOut(tween(220), targetScale = 0.90f) + fadeOut(tween(180))
                                        PredictiveBackStyle.SLIDE_RIGHT ->
                                            slideOutHorizontally(tween(240)) { it } + fadeOut(tween(200))
                                        PredictiveBackStyle.FADE ->
                                            fadeOut(tween(180))
                                    }
                                }
                            }
                        ) {
                            composable(ROUTE_MAIN_TABS) {
                                val backScale = when (appearance.predictiveBackStyle) {
                                    PredictiveBackStyle.SYSTEM_SCALE -> 1f - 0.08f * pagerBackProgress
                                    else -> 1f
                                }
                                HorizontalPager(
                                    state = pagerState,
                                    beyondViewportPageCount = 1,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            alpha = (tabFadeAlpha.value * (1f - 0.2f * pagerBackProgress)).coerceIn(0f, 1f)
                                            scaleX = backScale
                                            scaleY = backScale
                                            if (appearance.predictiveBackStyle == PredictiveBackStyle.SLIDE_RIGHT) {
                                                translationX = pagerBackProgress * size.width * 0.25f
                                            }
                                        }
                                ) { page ->
                                    when (page) {
                                        0 -> LedgerHomeScreen(
                                            transactionsState = transactionsState,
                                            inquiries = inquiries,
                                            settings = settings,
                                            appearance = appearance,
                                            exchangeRates = exchangeRates,
                                            isListenerEnabled = isListenerEnabled,
                                            viewModel = viewModel,
                                            onNavigateToParser = { navController.navigate(ROUTE_SETTINGS_PARSER) }
                                        )
                                        1 -> AnalyticsScreen(
                                            transactionsState = transactionsState,
                                            settings = settings,
                                            exchangeRates = exchangeRates,
                                            viewModel = viewModel
                                        )
                                        2 -> SettingsScreen(
                                            settings = settings,
                                            aiConfig = aiConfig,
                                            isListenerEnabled = isListenerEnabled,
                                            isAccessibilityEnabled = isAccessibilityEnabled,
                                            isIgnoringBattery = isIgnoringBattery,
                                            onOpenLedgerManagement = { navController.navigate(ROUTE_SETTINGS_LEDGER_MGMT) },
                                            onOpenParser = { navController.navigate(ROUTE_SETTINGS_PARSER) },
                                            onOpenAiSettings = { navController.navigate(ROUTE_SETTINGS_AI) },
                                            onOpenDataHub = { navController.navigate(ROUTE_SETTINGS_DATA_HUB) },
                                            onOpenAppearance = { navController.navigate(ROUTE_SETTINGS_APPEARANCE) },
                                            onOpenPermissions = { navController.navigate(ROUTE_SETTINGS_PERMISSIONS) },
                                            onOpenAbout = { navController.navigate(ROUTE_SETTINGS_ABOUT) }
                                        )
                                    }
                                }
                            }

                            composable(ROUTE_SETTINGS_LEDGER_MGMT) {
                                SwipeBackContainer(
                                    active = currentRoute == ROUTE_SETTINGS_LEDGER_MGMT,
                                    predictiveBackEnabled = appearance.predictiveBackEnabled,
                                    style = appearance.predictiveBackStyle,
                                    onNavigateUp = popWithStyle
                                ) {
                                    BillToolsMenuScreen(
                                        settings = settings,
                                        transactionsState = transactionsState,
                                        storageFootprint = storageFootprint,
                                        onOpenBehaviorBudget = { navController.navigate(ROUTE_SETTINGS_BILL_BEHAVIOR) },
                                        onOpenCurrencyRates = { navController.navigate(ROUTE_SETTINGS_CURRENCY) },
                                        onOpenSmartDedup = { navController.navigate(ROUTE_SETTINGS_DEDUP) },
                                        onOpenStorageOpt = { navController.navigate(ROUTE_SETTINGS_STORAGE_OPT) },
                                        onRefreshFootprint = { viewModel.refreshStorageFootprint(context) }
                                    )
                                }
                            }

                            composable(ROUTE_SETTINGS_BILL_BEHAVIOR) {
                                SwipeBackContainer(
                                    active = currentRoute == ROUTE_SETTINGS_BILL_BEHAVIOR,
                                    predictiveBackEnabled = appearance.predictiveBackEnabled,
                                    style = appearance.predictiveBackStyle,
                                    onNavigateUp = popWithStyle
                                ) {
                                    BookkeepingBehaviorScreen(
                                        settings = settings,
                                        viewModel = viewModel,
                                        onShowMessage = { msg ->
                                            scope.launch { snackbarHostState.showSnackbar(msg) }
                                        }
                                    )
                                }
                            }

                            composable(ROUTE_SETTINGS_CURRENCY) {
                                SwipeBackContainer(
                                    active = currentRoute == ROUTE_SETTINGS_CURRENCY,
                                    predictiveBackEnabled = appearance.predictiveBackEnabled,
                                    style = appearance.predictiveBackStyle,
                                    onNavigateUp = popWithStyle
                                ) {
                                    CurrencySettingsScreen(
                                        settings = settings,
                                        exchangeRates = exchangeRates,
                                        viewModel = viewModel
                                    )
                                }
                            }

                            composable(ROUTE_SETTINGS_DEDUP) {
                                SwipeBackContainer(
                                    active = currentRoute == ROUTE_SETTINGS_DEDUP,
                                    predictiveBackEnabled = appearance.predictiveBackEnabled,
                                    style = appearance.predictiveBackStyle,
                                    onNavigateUp = popWithStyle
                                ) {
                                    SmartDeduplicationScreen(
                                        transactionsState = transactionsState,
                                        viewModel = viewModel
                                    )
                                }
                            }

                            composable(ROUTE_SETTINGS_STORAGE_OPT) {
                                SwipeBackContainer(
                                    active = currentRoute == ROUTE_SETTINGS_STORAGE_OPT,
                                    predictiveBackEnabled = appearance.predictiveBackEnabled,
                                    style = appearance.predictiveBackStyle,
                                    onNavigateUp = popWithStyle
                                ) {
                                    StorageOptimizationScreen(viewModel = viewModel)
                                }
                            }

                            composable(ROUTE_SETTINGS_PARSER) {
                                SwipeBackContainer(
                                    active = currentRoute == ROUTE_SETTINGS_PARSER,
                                    predictiveBackEnabled = appearance.predictiveBackEnabled,
                                    style = appearance.predictiveBackStyle,
                                    onNavigateUp = popWithStyle
                                ) {
                                    ParserHubScreen(
                                        viewModel = viewModel,
                                        isListenerEnabled = isListenerEnabled,
                                        onShowMessage = { msg ->
                                            scope.launch { snackbarHostState.showSnackbar(msg) }
                                        }
                                    )
                                }
                            }

                            composable(ROUTE_SETTINGS_AI) {
                                SwipeBackContainer(
                                    active = currentRoute == ROUTE_SETTINGS_AI,
                                    predictiveBackEnabled = appearance.predictiveBackEnabled,
                                    style = appearance.predictiveBackStyle,
                                    onNavigateUp = popWithStyle
                                ) {
                                    AiSettingsScreen(
                                        viewModel = viewModel,
                                        onShowMessage = { msg ->
                                            scope.launch { snackbarHostState.showSnackbar(msg) }
                                        }
                                    )
                                }
                            }

                            composable(ROUTE_SETTINGS_DATA_HUB) {
                                SwipeBackContainer(
                                    active = currentRoute == ROUTE_SETTINGS_DATA_HUB,
                                    predictiveBackEnabled = appearance.predictiveBackEnabled,
                                    style = appearance.predictiveBackStyle,
                                    onNavigateUp = popWithStyle
                                ) {
                                    DataHubScreen(
                                        settings = settings,
                                        viewModel = viewModel,
                                        initialTab = DataHubTab.IMPORT,
                                        onShowMessage = { msg ->
                                            scope.launch { snackbarHostState.showSnackbar(msg) }
                                        }
                                    )
                                }
                            }

                            composable(ROUTE_SETTINGS_LOCAL_BACKUP) {
                                SwipeBackContainer(
                                    active = currentRoute == ROUTE_SETTINGS_LOCAL_BACKUP,
                                    predictiveBackEnabled = appearance.predictiveBackEnabled,
                                    style = appearance.predictiveBackStyle,
                                    onNavigateUp = popWithStyle
                                ) {
                                    DataHubScreen(
                                        settings = settings,
                                        viewModel = viewModel,
                                        initialTab = DataHubTab.LOCAL_BACKUP,
                                        onShowMessage = { msg ->
                                            scope.launch { snackbarHostState.showSnackbar(msg) }
                                        }
                                    )
                                }
                            }

                            composable(ROUTE_SETTINGS_APPEARANCE) {
                                SwipeBackContainer(
                                    active = currentRoute == ROUTE_SETTINGS_APPEARANCE,
                                    predictiveBackEnabled = appearance.predictiveBackEnabled,
                                    style = appearance.predictiveBackStyle,
                                    onNavigateUp = popWithStyle
                                ) {
                                    AppearanceSettingsScreen(
                                        appearance = appearance,
                                        viewModel = viewModel
                                    )
                                }
                            }

                            composable(ROUTE_SETTINGS_PERMISSIONS) {
                                SwipeBackContainer(
                                    active = currentRoute == ROUTE_SETTINGS_PERMISSIONS,
                                    predictiveBackEnabled = appearance.predictiveBackEnabled,
                                    style = appearance.predictiveBackStyle,
                                    onNavigateUp = popWithStyle
                                ) {
                                    PermissionsKeepAliveScreen(
                                        settings = settings,
                                        isListenerEnabled = isListenerEnabled,
                                        isAccessibilityEnabled = isAccessibilityEnabled,
                                        isIgnoringBattery = isIgnoringBattery,
                                        viewModel = viewModel,
                                        onShowMessage = { msg ->
                                            scope.launch { snackbarHostState.showSnackbar(msg) }
                                        }
                                    )
                                }
                            }

                            composable(ROUTE_SETTINGS_ABOUT) {
                                SwipeBackContainer(
                                    active = currentRoute == ROUTE_SETTINGS_ABOUT,
                                    predictiveBackEnabled = appearance.predictiveBackEnabled,
                                    style = appearance.predictiveBackStyle,
                                    onNavigateUp = popWithStyle
                                ) {
                                    AboutScreen(
                                        onShowMessage = { msg ->
                                            scope.launch { snackbarHostState.showSnackbar(msg) }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Multi-level sub-screen wrapper supporting:
 * 1) Horizontal rightward swipe gesture anywhere inside the screen (respecting child horizontal scrollers)
 *    to return smoothly to the parent menu level.
 * 2) Android system predictive back gesture preview matching [PredictiveBackStyle].
 */
@Composable
private fun SwipeBackContainer(
    active: Boolean,
    predictiveBackEnabled: Boolean,
    style: PredictiveBackStyle,
    onNavigateUp: () -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val viewConfig = LocalViewConfiguration.current
    val edgeStripPx = with(density) { 48.dp.toPx() }
    val touchSlop = viewConfig.touchSlop
    val scope = rememberCoroutineScope()

    var widthPx by remember { mutableFloatStateOf(1080f) }
    val dragOffset = remember { Animatable(0f) }
    var systemBackProgress by remember { mutableFloatStateOf(0f) }
    var navigatingUp by remember { mutableStateOf(false) }

    if (predictiveBackEnabled && active) {
        PredictiveBackHandler(enabled = !navigatingUp) { progressFlow ->
            try {
                progressFlow.collect { event ->
                    systemBackProgress = event.progress.coerceIn(0f, 1f)
                }
                systemBackProgress = 0f
                navigatingUp = true
                onNavigateUp()
            } catch (_: CancellationException) {
                systemBackProgress = 0f
            }
        }
    }

    val dragProgress = (dragOffset.value / widthPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
    val progress = maxOf(dragProgress, systemBackProgress)

    val cornerDp = (22f * progress).dp
    val scale = when (style) {
        PredictiveBackStyle.SYSTEM_SCALE -> 1f - 0.10f * progress
        else -> 1f
    }
    val translationXVal = when {
        dragOffset.value > 0f && style != PredictiveBackStyle.FADE ->
            if (style == PredictiveBackStyle.SYSTEM_SCALE) dragOffset.value * 0.45f else dragOffset.value
        systemBackProgress > 0f && style == PredictiveBackStyle.SLIDE_RIGHT ->
            systemBackProgress * widthPx * 0.45f
        systemBackProgress > 0f && style == PredictiveBackStyle.SYSTEM_SCALE ->
            systemBackProgress * widthPx * 0.12f
        else -> progress * widthPx * 0.15f
    }
    val alphaVal = when (style) {
        PredictiveBackStyle.FADE -> 1f - 0.50f * progress
        else -> 1f - 0.16f * progress
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                if (size.width > 0) widthPx = size.width.toFloat()
            }
            .pointerInput(active, predictiveBackEnabled) {
                if (!active) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startedNearLeftEdge = down.position.x <= edgeStripPx
                    var totalDx = 0f
                    var totalDy = 0f
                    var locked = false
                    var lastVelocityX = 0f
                    var lastTime = down.uptimeMillis

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (locked && !navigatingUp) {
                                val threshold = widthPx * 0.24f
                                if (dragOffset.value >= threshold || lastVelocityX >= 0.7f) {
                                    navigatingUp = true
                                    scope.launch {
                                        dragOffset.animateTo(widthPx, tween(160))
                                        onNavigateUp()
                                    }
                                } else {
                                    scope.launch { dragOffset.animateTo(0f, tween(180)) }
                                }
                            }
                            break
                        }

                        if (!locked && !startedNearLeftEdge && change.isConsumed) {
                            break
                        }

                        val delta = change.positionChange()
                        totalDx += delta.x
                        totalDy += delta.y

                        if (!locked) {
                            if (abs(totalDy) > touchSlop && abs(totalDy) > abs(totalDx)) {
                                break
                            }
                            val slopeFactor = if (startedNearLeftEdge) 1.15f else 1.45f
                            if (totalDx > touchSlop && totalDx > abs(totalDy) * slopeFactor) {
                                locked = true
                            }
                        }

                        if (locked) {
                            change.consume()
                            val dt = (change.uptimeMillis - lastTime).coerceAtLeast(1L)
                            lastVelocityX = delta.x / dt.toFloat()
                            lastTime = change.uptimeMillis
                            val target = (dragOffset.value + delta.x).coerceAtLeast(0f)
                            scope.launch { dragOffset.snapTo(target) }
                        }
                    }
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = translationXVal
                alpha = alphaVal.coerceIn(0f, 1f)
            }
            .then(
                if (progress > 0.01f) Modifier.clip(RoundedCornerShape(cornerDp)) else Modifier
            )
    ) {
        content()
    }
}
