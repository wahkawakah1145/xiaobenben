package com.example.ui

import com.example.model.GitHubUpdateMirror
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
import android.os.SystemClock
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import com.example.model.DeleteUndoDuration
import com.example.model.GlobalToastDuration
import com.example.model.MainBottomTab
import com.example.model.PredictiveBackStyle
import com.example.model.ThemeMode
import com.example.model.TabClickAnimationStyle
import com.example.service.NotificationBridge
import com.example.ui.components.AppBrandIconAvatar
import com.example.ui.theme.AppIcons
import com.example.ui.theme.SmartLedgerTheme
import com.example.viewmodel.DeleteUndoBanner
import com.example.viewmodel.LedgerViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

const val ROUTE_MAIN_TABS = "main_tabs"
const val ROUTE_LEDGER = "ledger"
const val ROUTE_BOOKS = "books"
const val ROUTE_ANALYTICS = "analytics"
const val ROUTE_SETTINGS = "settings"

const val ROUTE_SETTINGS_LEDGER_MGMT = "settings/ledger_mgmt"
const val ROUTE_SETTINGS_BOOKS = "settings/books"
const val ROUTE_BOOK_ANALYTICS = "books/analytics"
const val ROUTE_SETTINGS_MANUAL_IMPORT = "settings/manual_import"
const val ROUTE_SETTINGS_CATEGORIES = "settings/categories"
const val ROUTE_SETTINGS_BILL_BEHAVIOR = "settings/bill_behavior"
const val ROUTE_SETTINGS_PARSER = "settings/parser"
const val ROUTE_SETTINGS_AI = "settings/ai"
const val ROUTE_SETTINGS_CURRENCY = "settings/currency"
const val ROUTE_SETTINGS_DATA_HUB = "settings/data_hub"
const val ROUTE_SETTINGS_LOCAL_BACKUP = "settings/local_backup"
const val ROUTE_SETTINGS_DEDUP = "settings/dedup"
const val ROUTE_SETTINGS_STORAGE_OPT = "settings/storage_opt"
const val ROUTE_SETTINGS_APPEARANCE = "settings/appearance"
const val ROUTE_SETTINGS_APPEARANCE_THEME = "settings/appearance/theme"
const val ROUTE_SETTINGS_APPEARANCE_DISPLAY = "settings/appearance/display"
const val ROUTE_SETTINGS_APPEARANCE_HOME_NAV = "settings/appearance/home_nav"
const val ROUTE_SETTINGS_APPEARANCE_INTERACTION = "settings/appearance/interaction"
const val ROUTE_SETTINGS_APPEARANCE_ICON = "settings/appearance/icon"
const val ROUTE_SETTINGS_PERMISSIONS = "settings/permissions"
const val ROUTE_SETTINGS_ABOUT = "settings/about"

private data class TopDestination(
    val tab: MainBottomTab,
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private fun destinationForTab(tab: MainBottomTab): TopDestination = when (tab) {
    MainBottomTab.LEDGER -> TopDestination(
        tab = MainBottomTab.LEDGER,
        route = ROUTE_LEDGER,
        labelRes = R.string.nav_ledger,
        selectedIcon = AppIcons.ReceiptLong,
        unselectedIcon = AppIcons.ReceiptLongOutlined,
        testTag = "nav_ledger_tab"
    )
    MainBottomTab.BOOKS -> TopDestination(
        tab = MainBottomTab.BOOKS,
        route = ROUTE_BOOKS,
        labelRes = R.string.nav_books,
        selectedIcon = AppIcons.MenuBook,
        unselectedIcon = AppIcons.MenuBookOutlined,
        testTag = "nav_books_tab"
    )
    MainBottomTab.ANALYTICS -> TopDestination(
        tab = MainBottomTab.ANALYTICS,
        route = ROUTE_ANALYTICS,
        labelRes = R.string.nav_analytics,
        selectedIcon = AppIcons.PieChart,
        unselectedIcon = AppIcons.PieChartOutlined,
        testTag = "nav_analytics_tab"
    )
    MainBottomTab.SETTINGS -> TopDestination(
        tab = MainBottomTab.SETTINGS,
        route = ROUTE_SETTINGS,
        labelRes = R.string.nav_settings,
        selectedIcon = Icons.Rounded.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_settings_tab"
    )
}

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
            val books by viewModel.booksState.collectAsStateWithLifecycle()
            val bookLinks by viewModel.bookLinksState.collectAsStateWithLifecycle()
            val selectedAnalyticsBookId by viewModel.selectedAnalyticsBookId.collectAsStateWithLifecycle()
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
            var snackbarTimerJob by remember { mutableStateOf<Job?>(null) }

            val showGlobalSnackbar: (String) -> Unit = { message ->
                snackbarTimerJob?.cancel()
                snackbarHostState.currentSnackbarData?.dismiss()
                val durationChoice = appearance.globalSnackbarDuration
                snackbarTimerJob = scope.launch {
                    val showJob = launch {
                        snackbarHostState.showSnackbar(
                            message = message,
                            actionLabel = if (durationChoice == GlobalToastDuration.MANUAL_ONLY) "关闭" else null,
                            withDismissAction = true,
                            duration = SnackbarDuration.Indefinite
                        )
                    }
                    if (durationChoice.durationMillis > 0L) {
                        delay(durationChoice.durationMillis)
                        snackbarHostState.currentSnackbarData?.dismiss()
                        showJob.cancel()
                    } else {
                        showJob.join()
                    }
                }
            }

            LaunchedEffect(actionMessage) {
                val msg = actionMessage
                if (msg != null) {
                    showGlobalSnackbar(msg)
                    viewModel.clearActionMessage()
                }
            }

            val navController = rememberNavController()
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_MAIN_TABS
            val isTopLevelRoute = currentRoute == ROUTE_MAIN_TABS

            val visibleTabs = remember(appearance.bottomTabOrder, appearance.showBooksTab) {
                appearance.visibleBottomTabs()
            }
            val visibleDestinations = remember(visibleTabs) {
                visibleTabs.map { destinationForTab(it) }
            }
            val ledgerPageIndex = remember(visibleTabs) {
                visibleTabs.indexOf(MainBottomTab.LEDGER).coerceAtLeast(0)
            }
            val pagerState = rememberPagerState(
                initialPage = ledgerPageIndex,
                pageCount = { visibleDestinations.size }
            )
            var lastTrackedTab by remember { mutableStateOf(MainBottomTab.LEDGER) }

            LaunchedEffect(visibleTabs) {
                val targetIdx = visibleTabs.indexOf(lastTrackedTab)
                    .takeIf { it >= 0 }
                    ?: visibleTabs.indexOf(MainBottomTab.LEDGER).coerceAtLeast(0)
                val safeIdx = targetIdx.coerceIn(0, (visibleTabs.size - 1).coerceAtLeast(0))
                if (pagerState.currentPage != safeIdx) {
                    pagerState.scrollToPage(safeIdx)
                }
            }

            val currentTab = visibleTabs.getOrElse(pagerState.currentPage) { MainBottomTab.LEDGER }

            LaunchedEffect(currentTab) {
                lastTrackedTab = currentTab
            }

            val tabFadeAlpha = remember { Animatable(1f) }
            var pagerBackProgress by remember { mutableFloatStateOf(0f) }
            var isAnalyticsCalendarOpen by remember { mutableStateOf(false) }

            val homeListState = rememberLazyListState()
            val booksListState = rememberLazyListState()
            val analyticsListState = rememberLazyListState()
            val settingsListState = rememberLazyListState()
            var lastTabTapPage by remember { mutableIntStateOf(-1) }
            var lastTabTapMillis by remember { mutableLongStateOf(0L) }

            LaunchedEffect(currentTab) {
                if (currentTab != MainBottomTab.ANALYTICS && isAnalyticsCalendarOpen) {
                    isAnalyticsCalendarOpen = false
                }
            }

            val navigateTab: (Int) -> Unit = { targetPage ->
                val safePage = targetPage.coerceIn(0, (visibleDestinations.size - 1).coerceAtLeast(0))
                val now = SystemClock.uptimeMillis()
                if (isAnalyticsCalendarOpen) {
                    isAnalyticsCalendarOpen = false
                }
                if (pagerState.currentPage != safePage) {
                    lastTabTapPage = safePage
                    lastTabTapMillis = now
                    scope.launch {
                        when (appearance.tabClickAnimation) {
                            TabClickAnimationStyle.SLIDE -> {
                                pagerState.animateScrollToPage(
                                    page = safePage,
                                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                                )
                            }
                            TabClickAnimationStyle.FADE -> {
                                tabFadeAlpha.animateTo(0.35f, tween(90))
                                pagerState.scrollToPage(safePage)
                                tabFadeAlpha.animateTo(1f, tween(170))
                            }
                            TabClickAnimationStyle.NONE -> {
                                pagerState.scrollToPage(safePage)
                            }
                        }
                    }
                } else {
                    val isDoubleTap = lastTabTapPage == safePage && (now - lastTabTapMillis) <= 450L
                    lastTabTapPage = safePage
                    lastTabTapMillis = now
                    if (isDoubleTap) {
                        lastTabTapMillis = 0L
                        val tappedTab = visibleTabs.getOrNull(safePage)
                        scope.launch {
                            when (tappedTab) {
                                MainBottomTab.LEDGER -> homeListState.animateScrollToItem(0)
                                MainBottomTab.BOOKS -> booksListState.animateScrollToItem(0)
                                MainBottomTab.ANALYTICS -> analyticsListState.animateScrollToItem(0)
                                MainBottomTab.SETTINGS -> settingsListState.animateScrollToItem(0)
                                null -> Unit
                            }
                        }
                    }
                }
            }

            // On non-home main tab: system back / predictive back returns to Ledger tab
            if (isTopLevelRoute && currentTab != MainBottomTab.LEDGER && !isAnalyticsCalendarOpen) {
                if (appearance.predictiveBackEnabled) {
                    PredictiveBackHandler(enabled = true) { progress: Flow<BackEventCompat> ->
                        try {
                            progress.collect { event ->
                                pagerBackProgress = event.progress.coerceIn(0f, 1f)
                            }
                            pagerState.scrollToPage(ledgerPageIndex)
                            pagerBackProgress = 0f
                        } catch (_: CancellationException) {
                            pagerBackProgress = 0f
                        }
                    }
                } else {
                    BackHandler(enabled = true) {
                        scope.launch { pagerState.scrollToPage(ledgerPageIndex) }
                    }
                }
            }

            val previousRoute = navController.previousBackStackEntry?.destination?.route

            val currentTitle = when (currentRoute) {
                ROUTE_MAIN_TABS -> when (currentTab) {
                    MainBottomTab.BOOKS -> "账本"
                    MainBottomTab.ANALYTICS -> "收支分析"
                    MainBottomTab.SETTINGS -> "设置"
                    MainBottomTab.LEDGER -> appearance.homeBrandCustomName.ifBlank { stringResource(R.string.app_name) }
                }
                ROUTE_SETTINGS_LEDGER_MGMT -> "账单工具"
                ROUTE_SETTINGS_BOOKS -> "账本管理"
                ROUTE_BOOK_ANALYTICS -> {
                    val bName = selectedAnalyticsBookId?.let { id -> books.firstOrNull { it.id == id }?.name }
                    if (bName != null) "账本分析 · $bName" else "账本分析"
                }
                ROUTE_SETTINGS_MANUAL_IMPORT -> "手动导入账单"
                ROUTE_SETTINGS_CATEGORIES -> "分类管理"
                ROUTE_SETTINGS_BILL_BEHAVIOR -> "记账与预算"
                ROUTE_SETTINGS_CURRENCY -> "币种与汇率"
                ROUTE_SETTINGS_DEDUP -> "智能去重"
                ROUTE_SETTINGS_STORAGE_OPT -> "数据空间与存储优化"
                ROUTE_SETTINGS_PARSER -> "通知识别与规则"
                ROUTE_SETTINGS_AI -> "AI理解"
                ROUTE_SETTINGS_DATA_HUB, ROUTE_SETTINGS_LOCAL_BACKUP -> "备份与恢复"
                ROUTE_SETTINGS_APPEARANCE -> "外观与布局"
                ROUTE_SETTINGS_APPEARANCE_THEME -> "主题与配色"
                ROUTE_SETTINGS_APPEARANCE_DISPLAY -> "字体、DPI 与列表样式"
                ROUTE_SETTINGS_APPEARANCE_HOME_NAV -> "主页、顶栏与导航"
                ROUTE_SETTINGS_APPEARANCE_INTERACTION -> "动画、手势与提示横栏"
                ROUTE_SETTINGS_APPEARANCE_ICON -> "图标风格与桌面快捷方式"
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
                    ROUTE_MAIN_TABS -> when (currentTab) {
                        MainBottomTab.LEDGER -> LedgerHomeScreen(
                            transactionsState = transactionsState,
                            inquiries = inquiries,
                            settings = settings,
                            appearance = appearance,
                            exchangeRates = exchangeRates,
                            isListenerEnabled = isListenerEnabled,
                            viewModel = viewModel,
                            onNavigateToParser = { navController.navigate(ROUTE_SETTINGS_PARSER) },
                            books = books,
                            bookLinks = bookLinks,
                            listState = homeListState
                        )
                        MainBottomTab.BOOKS -> LedgerBooksScreen(
                            books = books,
                            bookLinks = bookLinks,
                            transactionsState = transactionsState,
                            settings = settings,
                            exchangeRates = exchangeRates,
                            viewModel = viewModel,
                            onAnalyzeBook = { bookId ->
                                viewModel.selectAnalyticsBook(bookId)
                                val analyticsIdx = visibleTabs.indexOf(MainBottomTab.ANALYTICS)
                                if (analyticsIdx >= 0) {
                                    navigateTab(analyticsIdx)
                                } else {
                                    navController.navigate(ROUTE_BOOK_ANALYTICS)
                                }
                            },
                            listState = booksListState
                        )
                        MainBottomTab.ANALYTICS -> AnalyticsScreen(
                            transactionsState = transactionsState,
                            settings = settings,
                            appearance = appearance,
                            exchangeRates = exchangeRates,
                            viewModel = viewModel,
                            books = books,
                            bookLinks = bookLinks,
                            selectedBookId = selectedAnalyticsBookId,
                            onSelectBookId = { viewModel.selectAnalyticsBook(it) },
                            isCalendarFullScreen = isAnalyticsCalendarOpen,
                            onCalendarFullScreenChange = { isAnalyticsCalendarOpen = it },
                            listState = analyticsListState
                        )
                        MainBottomTab.SETTINGS -> SettingsScreen(
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
                            onOpenAbout = { navController.navigate(ROUTE_SETTINGS_ABOUT) },
                            listState = settingsListState
                        )
                    }
                    ROUTE_SETTINGS_LEDGER_MGMT -> BillToolsMenuScreen(
                        settings = settings,
                        transactionsState = transactionsState,
                        storageFootprint = storageFootprint,
                        booksCount = books.size,
                        onOpenBooks = { navController.navigate(ROUTE_SETTINGS_BOOKS) },
                        onOpenCategories = { navController.navigate(ROUTE_SETTINGS_CATEGORIES) },
                        onOpenManualImport = { navController.navigate(ROUTE_SETTINGS_MANUAL_IMPORT) },
                        onOpenBehaviorBudget = { navController.navigate(ROUTE_SETTINGS_BILL_BEHAVIOR) },
                        onOpenCurrencyRates = { navController.navigate(ROUTE_SETTINGS_CURRENCY) },
                        onOpenSmartDedup = { navController.navigate(ROUTE_SETTINGS_DEDUP) },
                        onOpenStorageOpt = { navController.navigate(ROUTE_SETTINGS_STORAGE_OPT) },
                        onRefreshFootprint = { viewModel.refreshStorageFootprint(context) }
                    )
                    ROUTE_SETTINGS_BOOKS -> LedgerBooksScreen(
                        books = books,
                        bookLinks = bookLinks,
                        transactionsState = transactionsState,
                        settings = settings,
                        exchangeRates = exchangeRates,
                        viewModel = viewModel,
                        onAnalyzeBook = { bookId ->
                            viewModel.selectAnalyticsBook(bookId)
                            navController.navigate(ROUTE_BOOK_ANALYTICS)
                        },
                        listState = booksListState
                    )
                    ROUTE_BOOK_ANALYTICS -> AnalyticsScreen(
                        transactionsState = transactionsState,
                        settings = settings,
                        appearance = appearance,
                        exchangeRates = exchangeRates,
                        viewModel = viewModel,
                        books = books,
                        bookLinks = bookLinks,
                        selectedBookId = selectedAnalyticsBookId,
                        onSelectBookId = { viewModel.selectAnalyticsBook(it) },
                        isCalendarFullScreen = isAnalyticsCalendarOpen,
                        onCalendarFullScreenChange = { isAnalyticsCalendarOpen = it },
                        listState = analyticsListState
                    )
                    ROUTE_SETTINGS_MANUAL_IMPORT -> ManualImportScreen(
                        viewModel = viewModel,
                        onShowMessage = { msg -> showGlobalSnackbar(msg) }
                    )
                    ROUTE_SETTINGS_CATEGORIES -> CategoryManagementScreen(
                        settings = settings,
                        transactionsState = transactionsState,
                        viewModel = viewModel
                    )
                    ROUTE_SETTINGS_BILL_BEHAVIOR -> BookkeepingBehaviorScreen(
                        settings = settings,
                        viewModel = viewModel,
                        onShowMessage = { msg -> showGlobalSnackbar(msg) }
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
                        onShowMessage = { msg -> showGlobalSnackbar(msg) }
                    )
                    ROUTE_SETTINGS_AI -> AiSettingsScreen(
                        viewModel = viewModel,
                        onShowMessage = { msg -> showGlobalSnackbar(msg) }
                    )
                    ROUTE_SETTINGS_DATA_HUB -> DataHubScreen(
                        settings = settings,
                        viewModel = viewModel,
                        initialTab = DataHubTab.LOCAL_BACKUP,
                        onShowMessage = { msg -> showGlobalSnackbar(msg) }
                    )
                    ROUTE_SETTINGS_LOCAL_BACKUP -> DataHubScreen(
                        settings = settings,
                        viewModel = viewModel,
                        initialTab = DataHubTab.LOCAL_BACKUP,
                        onShowMessage = { msg -> showGlobalSnackbar(msg) }
                    )
                    ROUTE_SETTINGS_APPEARANCE -> AppearanceSettingsScreen(
                        appearance = appearance,
                        onOpenTheme = { navController.navigate(ROUTE_SETTINGS_APPEARANCE_THEME) },
                        onOpenDisplay = { navController.navigate(ROUTE_SETTINGS_APPEARANCE_DISPLAY) },
                        onOpenHomeNav = { navController.navigate(ROUTE_SETTINGS_APPEARANCE_HOME_NAV) },
                        onOpenInteraction = { navController.navigate(ROUTE_SETTINGS_APPEARANCE_INTERACTION) },
                        onOpenIconShortcut = { navController.navigate(ROUTE_SETTINGS_APPEARANCE_ICON) }
                    )
                    ROUTE_SETTINGS_APPEARANCE_THEME -> AppearanceThemeSettingsScreen(
                        appearance = appearance,
                        viewModel = viewModel
                    )
                    ROUTE_SETTINGS_APPEARANCE_DISPLAY -> AppearanceDisplaySettingsScreen(
                        appearance = appearance,
                        viewModel = viewModel
                    )
                    ROUTE_SETTINGS_APPEARANCE_HOME_NAV -> AppearanceHomeNavSettingsScreen(
                        appearance = appearance,
                        viewModel = viewModel
                    )
                    ROUTE_SETTINGS_APPEARANCE_INTERACTION -> AppearanceInteractionSettingsScreen(
                        appearance = appearance,
                        viewModel = viewModel,
                        onShowMessage = { msg -> showGlobalSnackbar(msg) }
                    )
                    ROUTE_SETTINGS_APPEARANCE_ICON -> AppearanceIconShortcutSettingsScreen(
                        appearance = appearance,
                        viewModel = viewModel
                    )
                    ROUTE_SETTINGS_PERMISSIONS -> PermissionsKeepAliveScreen(
                        settings = settings,
                        isListenerEnabled = isListenerEnabled,
                        isAccessibilityEnabled = isAccessibilityEnabled,
                        isIgnoringBattery = isIgnoringBattery,
                        viewModel = viewModel,
                        onShowMessage = { msg -> showGlobalSnackbar(msg) }
                    )
                    ROUTE_SETTINGS_ABOUT -> AboutScreen(
                        appIconStyleId = appearance.appIconStyleId,
                        selectedMirror = GitHubUpdateMirror.fromRaw(settings.githubUpdateMirror),
                        onSelectMirror = { mirror ->
                            viewModel.saveSettings(settings.copy(githubUpdateMirror = mirror.name))
                        },
                        onShowMessage = { msg -> showGlobalSnackbar(msg) }
                    )
                }
            }

            val isSystemDark = isSystemInDarkTheme()
            val isDarkEffective = when (appearance.themeMode) {
                ThemeMode.SYSTEM -> isSystemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            val isOledBlackActive = isDarkEffective && appearance.oledPureBlack
            val topBarColors = if (isOledBlackActive) {
                TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    scrolledContainerColor = Color.Black
                )
            } else {
                TopAppBarDefaults.topAppBarColors()
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useNavigationRail = maxWidth >= 600.dp
                val isHomeBrandTopBar = isTopLevelRoute && currentTab == MainBottomTab.LEDGER

                Box(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    topBar = {
                        if (isHomeBrandTopBar) {
                            TopAppBar(
                                colors = topBarColors,
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
                                colors = if (isOledBlackActive) TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Black, scrolledContainerColor = Color.Black) else TopAppBarDefaults.centerAlignedTopAppBarColors(),
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
                            NavigationBar(
                                containerColor = if (isOledBlackActive) Color.Black else MaterialTheme.colorScheme.surfaceContainer
                            ) {
                                visibleDestinations.forEachIndexed { index, dest ->
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
                            NavigationRail(
                                containerColor = if (isOledBlackActive) Color.Black else MaterialTheme.colorScheme.surface
                            ) {
                                visibleDestinations.forEachIndexed { index, dest ->
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
                                    if (pagerBackProgress > 0.001f && currentTab != MainBottomTab.LEDGER) {
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
                                                onNavigateToParser = { navController.navigate(ROUTE_SETTINGS_PARSER) },
                                                books = books,
                                                bookLinks = bookLinks,
                                                listState = homeListState
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
                                            userScrollEnabled = !isAnalyticsCalendarOpen,
                                            beyondViewportPageCount = 0,
                                            modifier = Modifier.fillMaxSize()
                                        ) { page ->
                                            when (visibleTabs.getOrNull(page)) {
                                                MainBottomTab.LEDGER -> LedgerHomeScreen(
                                                    transactionsState = transactionsState,
                                                    inquiries = inquiries,
                                                    settings = settings,
                                                    appearance = appearance,
                                                    exchangeRates = exchangeRates,
                                                    isListenerEnabled = isListenerEnabled,
                                                    viewModel = viewModel,
                                                    onNavigateToParser = { navController.navigate(ROUTE_SETTINGS_PARSER) },
                                                    books = books,
                                                    bookLinks = bookLinks,
                                                    listState = homeListState
                                                )
                                                MainBottomTab.BOOKS -> LedgerBooksScreen(
                                                    books = books,
                                                    bookLinks = bookLinks,
                                                    transactionsState = transactionsState,
                                                    settings = settings,
                                                    exchangeRates = exchangeRates,
                                                    viewModel = viewModel,
                                                    onAnalyzeBook = { bookId ->
                                                        viewModel.selectAnalyticsBook(bookId)
                                                        val analyticsIdx = visibleTabs.indexOf(MainBottomTab.ANALYTICS)
                                                        if (analyticsIdx >= 0) {
                                                            navigateTab(analyticsIdx)
                                                        } else {
                                                            navController.navigate(ROUTE_BOOK_ANALYTICS)
                                                        }
                                                    },
                                                    listState = booksListState
                                                )
                                                MainBottomTab.ANALYTICS -> AnalyticsScreen(
                                                    transactionsState = transactionsState,
                                                    settings = settings,
                                                    appearance = appearance,
                                                    exchangeRates = exchangeRates,
                                                    viewModel = viewModel,
                                                    books = books,
                                                    bookLinks = bookLinks,
                                                    selectedBookId = selectedAnalyticsBookId,
                                                    onSelectBookId = { viewModel.selectAnalyticsBook(it) },
                                                    isCalendarFullScreen = isAnalyticsCalendarOpen,
                                                    onCalendarFullScreenChange = { isAnalyticsCalendarOpen = it },
                                                    listState = analyticsListState
                                                )
                                                MainBottomTab.SETTINGS -> SettingsScreen(
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
                                                    onOpenAbout = { navController.navigate(ROUTE_SETTINGS_ABOUT) },
                                                    listState = settingsListState
                                                )
                                                null -> Unit
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
                                ROUTE_SETTINGS_BOOKS,
                                ROUTE_SETTINGS_CATEGORIES,
                                ROUTE_SETTINGS_MANUAL_IMPORT,
                                ROUTE_BOOK_ANALYTICS,
                                ROUTE_SETTINGS_BILL_BEHAVIOR,
                                ROUTE_SETTINGS_CURRENCY,
                                ROUTE_SETTINGS_DEDUP,
                                ROUTE_SETTINGS_STORAGE_OPT,
                                ROUTE_SETTINGS_PARSER,
                                ROUTE_SETTINGS_AI,
                                ROUTE_SETTINGS_DATA_HUB,
                                ROUTE_SETTINGS_LOCAL_BACKUP,
                                ROUTE_SETTINGS_APPEARANCE,
                                ROUTE_SETTINGS_APPEARANCE_THEME,
                                ROUTE_SETTINGS_APPEARANCE_DISPLAY,
                                ROUTE_SETTINGS_APPEARANCE_HOME_NAV,
                                ROUTE_SETTINGS_APPEARANCE_INTERACTION,
                                ROUTE_SETTINGS_APPEARANCE_ICON,
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
                            manualOnly = appearance.deleteUndoDuration == DeleteUndoDuration.MANUAL_ONLY,
                            globalDuration = appearance.globalSnackbarDuration,
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

    LaunchedEffect(active) {
        if (active) {
            navigatingUp = false
            dragOffset.snapTo(0f)
            systemBackAnim.snapTo(0f)
        }
    }

    // Keep PredictiveBackHandler enabled while `active` so toggling `navigatingUp`
    // does NOT cancel the completion animation coroutine before `onNavigateUp()` executes.
    if (predictiveBackEnabled && active) {
        PredictiveBackHandler(enabled = true) { progressFlow ->
            try {
                progressFlow.collect { event ->
                    if (!navigatingUp) {
                        systemBackAnim.snapTo(event.progress.coerceIn(0f, 1f))
                    }
                }
                if (!navigatingUp) {
                    navigatingUp = true
                    scope.launch {
                        systemBackAnim.animateTo(1f, tween(110, easing = FastOutSlowInEasing))
                        onNavigateUp()
                    }
                }
            } catch (_: CancellationException) {
                if (!navigatingUp) {
                    systemBackAnim.animateTo(0f, tween(160))
                }
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
    manualOnly: Boolean,
    globalDuration: GlobalToastDuration,
    onUndo: () -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember(banner.id) { Animatable(0f) }
    var barWidthPx by remember { mutableFloatStateOf(720f) }
    val effectiveMillis = if (manualOnly) -1L else globalDuration.durationMillis

    LaunchedEffect(banner.id, effectiveMillis) {
        if (effectiveMillis > 0L) {
            delay(effectiveMillis)
            onDismiss()
        }
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
                    text = if (manualOnly || globalDuration == GlobalToastDuration.MANUAL_ONLY) {
                        "仅手动关闭 · 左右滑动或点击关闭"
                    } else {
                        "跟随全局（${globalDuration.label}）· 左右滑动可关闭"
                    },
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
