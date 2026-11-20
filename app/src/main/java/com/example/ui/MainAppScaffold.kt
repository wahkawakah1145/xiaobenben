package com.example.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.ui.components.AppBrandIconAvatar
import com.example.ui.theme.AppIcons
import com.example.ui.theme.SmartLedgerTheme
import com.example.viewmodel.DeleteUndoBanner
import com.example.viewmodel.LedgerViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

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
            val deleteUndoBanner by viewModel.deleteUndoBanner.collectAsStateWithLifecycle()

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
            var isAnalyticsCalendarOpen by remember { mutableStateOf(false) }

            LaunchedEffect(pagerState.currentPage) {
                if (pagerState.currentPage != 1 && isAnalyticsCalendarOpen) {
                    isAnalyticsCalendarOpen = false
                }
            }

            val navigateTab: (Int) -> Unit = { targetPage ->
                if (isAnalyticsCalendarOpen) {
                    isAnalyticsCalendarOpen = false
                }
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
            if (isTopLevelRoute && pagerState.currentPage != 0 && !isAnalyticsCalendarOpen) {
                if (appearance.predictiveBackEnabled) {
                    PredictiveBackHandler(enabled = true) { progress: Flow<BackEventCompat> ->
                        try {
                            progress.collect { event ->
                                pagerBackProgress = event.progress.coerceIn(0f, 1f)
                            }
                            pagerState.scrollToPage(0)
                            pagerBackProgress = 0f
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

            val previousRoute = navController.previousBackStackEntry?.destination?.route

            val currentTitle = when (currentRoute) {
                ROUTE_MAIN_TABS -> when (pagerState.currentPage) {
                    1 -> "收支分析"
                    2 -> "设置"
                    else -> appearance.homeBrandCustomName.ifBlank { stringResource(R.string.app_name) }
                }
                ROUTE_SETTINGS_LEDGER_MGMT -> "账单工具"
                ROUTE_SETTINGS_BILL_BEHAVIOR -> "记账与预算"
                ROUTE_SETTINGS_CURRENCY -> "币种与汇率"
                ROUTE_SETTINGS_DEDUP -> "智能去重"
                ROUTE_SETTINGS_STORAGE_OPT -> "数据空间与存储优化"
                ROUTE_SETTINGS_PARSER -> "通知识别与规则"
                ROUTE_SETTINGS_AI -> "AI理解"
                ROUTE_SETTINGS_DATA_HUB, ROUTE_SETTINGS_LOCAL_BACKUP -> "备份与恢复"
                ROUTE_SETTINGS_APPEARANCE -> "外观与布局"
                ROUTE_SETTINGS_PERMISSIONS -> "权限管理"
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

            var suppressNextPopTransition by remember { mutableStateOf(false) }

            LaunchedEffect(currentRoute) {
                if (suppressNextPopTransition) {
                    delay(80)
                    suppressNextPopTransition = false
                }
            }

            val popWithStyle: () -> Unit = {
                suppressNextPopTransition = false
                navController.navigateUp()
            }

            val popFromGestureImmediate: () -> Unit = {
                suppressNextPopTransition = true
                navController.navigateUp()
            }

            @Composable
            fun DestinationContent(route: String) {
                when (route) {
                    ROUTE_MAIN_TABS -> when (pagerState.currentPage) {
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
                            appearance = appearance,
                            exchangeRates = exchangeRates,
                            viewModel = viewModel,
                            isCalendarFullScreen = isAnalyticsCalendarOpen,
                            onCalendarFullScreenChange = { isAnalyticsCalendarOpen = it }
                        )
                        else -> SettingsScreen(
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
                    ROUTE_SETTINGS_LEDGER_MGMT -> BillToolsMenuScreen(
                        settings = settings,
                        transactionsState = transactionsState,
                        storageFootprint = storageFootprint,
                        onOpenBehaviorBudget = { navController.navigate(ROUTE_SETTINGS_BILL_BEHAVIOR) },
                        onOpenCurrencyRates = { navController.navigate(ROUTE_SETTINGS_CURRENCY) },
                        onOpenSmartDedup = { navController.navigate(ROUTE_SETTINGS_DEDUP) },
                        onOpenStorageOpt = { navController.navigate(ROUTE_SETTINGS_STORAGE_OPT) },
                        onRefreshFootprint = { viewModel.refreshStorageFootprint(context) }
                    )
                    ROUTE_SETTINGS_BILL_BEHAVIOR -> BookkeepingBehaviorScreen(
                        settings = settings,
                        viewModel = viewModel,
                        onShowMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                    )
                    ROUTE_SETTINGS_CURRENCY -> CurrencySettingsScreen(
                        settings = settings,
                        exchangeRates = exchangeRates,
                        viewModel = viewModel
                    )
                    ROUTE_SETTINGS_DEDUP -> SmartDeduplicationScreen(
                        transactionsState = transactionsState,
                        viewModel = viewModel
                    )
                    ROUTE_SETTINGS_STORAGE_OPT -> StorageOptimizationScreen(viewModel = viewModel)
                    ROUTE_SETTINGS_PARSER -> ParserHubScreen(
                        viewModel = viewModel,
                        isListenerEnabled = isListenerEnabled,
                        onShowMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                    )
                    ROUTE_SETTINGS_AI -> AiSettingsScreen(
                        viewModel = viewModel,
                        onShowMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                    )
                    ROUTE_SETTINGS_DATA_HUB -> DataHubScreen(
                        settings = settings,
                        viewModel = viewModel,
                        initialTab = DataHubTab.IMPORT,
                        onShowMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                    )
                    ROUTE_SETTINGS_LOCAL_BACKUP -> DataHubScreen(
                        settings = settings,
                        viewModel = viewModel,
                        initialTab = DataHubTab.LOCAL_BACKUP,
                        onShowMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                    )
                    ROUTE_SETTINGS_APPEARANCE -> AppearanceSettingsScreen(
                        appearance = appearance,
                        viewModel = viewModel
                    )
                    ROUTE_SETTINGS_PERMISSIONS -> PermissionsKeepAliveScreen(
                        settings = settings,
                        isListenerEnabled = isListenerEnabled,
                        isAccessibilityEnabled = isAccessibilityEnabled,
                        isIgnoringBattery = isIgnoringBattery,
                        viewModel = viewModel,
                        onShowMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                    )
                    ROUTE_SETTINGS_ABOUT -> AboutScreen(
                        appIconStyleId = appearance.appIconStyleId,
                        onShowMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                    )
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useNavigationRail = maxWidth >= 600.dp
                val isHomeBrandTopBar = isTopLevelRoute && pagerState.currentPage == 0

                Box(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    topBar = {
                        if (isHomeBrandTopBar) {
                            TopAppBar(
                                title = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.testTag("home_brand_top_bar")
                                    ) {
                                        if (appearance.homeBrandShowIcon) {
                                            AppBrandIconAvatar(
                                                styleId = appearance.appIconStyleId,
                                                size = appearance.homeBrandIconSizeDp.dp,
                                                cornerDp = appearance.homeBrandIconCornerDp.dp
                                            )
                                            if (appearance.homeBrandShowTitle) {
                                                Spacer(modifier = Modifier.width(appearance.homeBrandIconSpacingDp.dp))
                                            }
                                        }
                                        if (appearance.homeBrandShowTitle) {
                                            Column {
                                                Text(
                                                    text = appearance.homeBrandCustomName.ifBlank { stringResource(R.string.app_name) },
                                                    fontSize = appearance.homeBrandTitleSizeSp.sp,
                                                    fontWeight = if (appearance.homeBrandTitleBold) FontWeight.Bold else FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (appearance.homeBrandCustomSubtitle.isNotBlank()) {
                                                    Text(
                                                        text = appearance.homeBrandCustomSubtitle,
                                                        fontSize = appearance.homeBrandSubtitleSizeSp.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                        } else {
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
                        }
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

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                        NavHost(
                            navController = navController,
                            startDestination = ROUTE_MAIN_TABS,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                            enterTransition = {
                                slideInHorizontally(tween(240)) { (it * 0.25f).toInt() } + fadeIn(tween(200))
                            },
                            exitTransition = {
                                slideOutHorizontally(tween(240)) { -(it * 0.12f).toInt() } + fadeOut(tween(180))
                            },
                            popEnterTransition = {
                                if (suppressNextPopTransition || !appearance.predictiveBackEnabled) {
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
                                if (suppressNextPopTransition || !appearance.predictiveBackEnabled) {
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
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.background)
                                ) {
                                    if (pagerBackProgress > 0.001f && pagerState.currentPage != 0) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.background,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
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
                                    }
                                    Surface(
                                        color = MaterialTheme.colorScheme.background,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                alpha = (tabFadeAlpha.value * (1f - 0.16f * pagerBackProgress)).coerceIn(0f, 1f)
                                                scaleX = backScale
                                                scaleY = backScale
                                                if (appearance.predictiveBackStyle == PredictiveBackStyle.SLIDE_RIGHT) {
                                                    translationX = pagerBackProgress * size.width * 0.35f
                                                }
                                            }
                                    ) {
                                        HorizontalPager(
                                            state = pagerState,
                                            beyondViewportPageCount = 1,
                                            modifier = Modifier.fillMaxSize()
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
                                                    appearance = appearance,
                                                    exchangeRates = exchangeRates,
                                                    viewModel = viewModel,
                                                    isCalendarFullScreen = isAnalyticsCalendarOpen,
                                                    onCalendarFullScreenChange = { isAnalyticsCalendarOpen = it }
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
                                }
                            }

                            val prevComposable: (@Composable () -> Unit)? = previousRoute?.let { prev ->
                                { DestinationContent(prev) }
                            }

                            listOf(
                                ROUTE_SETTINGS_LEDGER_MGMT,
                                ROUTE_SETTINGS_BILL_BEHAVIOR,
                                ROUTE_SETTINGS_CURRENCY,
                                ROUTE_SETTINGS_DEDUP,
                                ROUTE_SETTINGS_STORAGE_OPT,
                                ROUTE_SETTINGS_PARSER,
                                ROUTE_SETTINGS_AI,
                                ROUTE_SETTINGS_DATA_HUB,
                                ROUTE_SETTINGS_LOCAL_BACKUP,
                                ROUTE_SETTINGS_APPEARANCE,
                                ROUTE_SETTINGS_PERMISSIONS,
                                ROUTE_SETTINGS_ABOUT
                            ).forEach { subRoute ->
                                composable(subRoute) {
                                    SwipeBackContainer(
                                        active = currentRoute == subRoute,
                                        predictiveBackEnabled = appearance.predictiveBackEnabled,
                                        style = appearance.predictiveBackStyle,
                                        onNavigateUp = popFromGestureImmediate,
                                        previousContent = prevComposable
                                    ) {
                                        DestinationContent(subRoute)
                                    }
                                }
                            }
                        }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = deleteUndoBanner != null,
                    enter = slideInVertically(tween(220)) { it / 2 } + fadeIn(tween(180)),
                    exit = slideOutVertically(tween(200)) { it / 2 } + fadeOut(tween(150)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = if (!useNavigationRail && isTopLevelRoute) 86.dp else 20.dp
                        )
                ) {
                    deleteUndoBanner?.let { banner ->
                        DeleteUndoFloatingBar(
                            banner = banner,
                            onUndo = { viewModel.undoLastDelete() },
                            onDismiss = { viewModel.dismissDeleteUndoBanner() }
                        )
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
 *    to return smoothly to the parent menu level, revealing a live opaque underlay instead of black.
 * 2) Android system predictive back gesture preview matching [PredictiveBackStyle].
 */
@Composable
private fun SwipeBackContainer(
    active: Boolean,
    predictiveBackEnabled: Boolean,
    style: PredictiveBackStyle,
    onNavigateUp: () -> Unit,
    previousContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val viewConfig = LocalViewConfiguration.current
    val edgeStripPx = with(density) { 48.dp.toPx() }
    val touchSlop = viewConfig.touchSlop
    val scope = rememberCoroutineScope()

    var widthPx by remember { mutableFloatStateOf(1080f) }
    val dragOffset = remember { Animatable(0f) }
    val systemBackAnim = remember { Animatable(0f) }
    var navigatingUp by remember { mutableStateOf(false) }

    if (predictiveBackEnabled && active) {
        PredictiveBackHandler(enabled = !navigatingUp) { progressFlow ->
            try {
                progressFlow.collect { event ->
                    systemBackAnim.snapTo(event.progress.coerceIn(0f, 1f))
                }
                navigatingUp = true
                // Complete predictive animation smoothly to 1f WITHOUT resetting to 0f, eliminating flash
                systemBackAnim.animateTo(1f, tween(120, easing = FastOutSlowInEasing))
                onNavigateUp()
            } catch (_: CancellationException) {
                systemBackAnim.animateTo(0f, tween(160))
            }
        }
    }

    val dragProgress = (dragOffset.value / widthPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
    val progress = maxOf(dragProgress, systemBackAnim.value)

    val cornerDp = (22f * progress).dp
    val scale = when (style) {
        PredictiveBackStyle.SYSTEM_SCALE -> 1f - 0.12f * progress
        else -> 1f
    }
    val translationXVal = when {
        dragOffset.value > 0f && style != PredictiveBackStyle.FADE ->
            if (style == PredictiveBackStyle.SYSTEM_SCALE) dragOffset.value * 0.48f else dragOffset.value
        systemBackAnim.value > 0f && style == PredictiveBackStyle.SLIDE_RIGHT ->
            systemBackAnim.value * widthPx
        systemBackAnim.value > 0f && style == PredictiveBackStyle.SYSTEM_SCALE ->
            systemBackAnim.value * widthPx * 0.14f
        else -> progress * widthPx * 0.15f
    }
    val alphaVal = when {
        navigatingUp && progress > 0.72f -> (1f - ((progress - 0.72f) / 0.28f)).coerceIn(0f, 1f)
        style == PredictiveBackStyle.FADE -> 1f - 0.75f * progress
        else -> 1f - 0.12f * progress
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
    ) {
        if (progress > 0.001f && previousContent != null) {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val underlayScale = 0.96f + 0.04f * progress
                        scaleX = underlayScale
                        scaleY = underlayScale
                        translationX = -widthPx * 0.08f * (1f - progress)
                    }
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    previousContent()
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = (0.14f * (1f - progress)).coerceIn(0f, 0.14f)))
                    )
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.background,
            shape = if (progress > 0.01f) RoundedCornerShape(cornerDp) else RectangleShape,
            shadowElevation = if (progress > 0.01f) 10.dp else 0.dp,
            modifier = Modifier
                .fillMaxSize()
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
}

@Composable
private fun DeleteUndoFloatingBar(
    banner: DeleteUndoBanner,
    onUndo: () -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember(banner.id) { Animatable(0f) }
    var barWidthPx by remember { mutableFloatStateOf(720f) }

    LaunchedEffect(banner.id) {
        delay(8500L)
        onDismiss()
    }

    val dismissProgress = (abs(offsetX.value) / barWidthPx.coerceAtLeast(1f)).coerceIn(0f, 1f)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 6.dp,
        modifier = Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .onSizeChanged { size ->
                if (size.width > 0) barWidthPx = size.width.toFloat()
            }
            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
            .graphicsLayer {
                alpha = (1f - dismissProgress * 0.75f).coerceIn(0.15f, 1f)
            }
            .pointerInput(banner.id) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            offsetX.snapTo(offsetX.value + dragAmount)
                        }
                    },
                    onDragEnd = {
                        val threshold = barWidthPx * 0.28f
                        if (abs(offsetX.value) >= threshold) {
                            val target = if (offsetX.value >= 0f) barWidthPx else -barWidthPx
                            scope.launch {
                                offsetX.animateTo(target, tween(160))
                                onDismiss()
                            }
                        } else {
                            scope.launch {
                                offsetX.animateTo(0f, tween(180))
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch { offsetX.animateTo(0f, tween(180)) }
                    }
                )
            }
            .testTag("delete_undo_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = banner.message,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "左右滑动此横栏可关闭",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.68f)
                )
            }
            TextButton(
                onClick = onUndo,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.inversePrimary
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("undo_delete_button")
            ) {
                Text("撤销", fontWeight = FontWeight.Bold)
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("dismiss_delete_undo_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "关闭提示",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
