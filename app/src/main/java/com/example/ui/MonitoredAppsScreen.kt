package com.example.ui

import android.Manifest
import android.os.Build
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.model.BudgetSettings
import com.example.service.InstalledAppEntry
import com.example.service.InstalledAppsCatalog
import com.example.service.NotificationBridge
import com.example.ui.theme.AppIcons
import com.example.ui.theme.ExpenseCoral
import com.example.ui.theme.IncomeJade
import com.example.viewmodel.LedgerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class MonitoredAppFilter(val label: String) {
    ALL("全部"),
    SELECTED("已勾选"),
    RECOMMENDED("推荐金融与短信"),
    USER("用户应用"),
    SYSTEM("系统应用")
}

@Composable
fun MonitoredAppsScreen(
    settings: BudgetSettings,
    isListenerEnabled: Boolean,
    isIgnoringBattery: Boolean,
    viewModel: LedgerViewModel,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    val iconPx = remember(density) { with(density) { 40.dp.roundToPx().coerceIn(48, 128) } }

    // Bounded small icon cache strictly scoped to this screen; evicted immediately on exit -> 0 KB background RAM.
    val iconCache = remember { LruCache<String, ImageBitmap>(48) }
    DisposableEffect(Unit) {
        onDispose { iconCache.evictAll() }
    }

    var hasPostNotifPermission by remember {
        mutableStateOf(NotificationBridge.hasPostNotificationPermission(context))
    }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(MonitoredAppFilter.ALL) }
    var appsLoading by remember { mutableStateOf(true) }
    var installedApps by remember { mutableStateOf<List<InstalledAppEntry>>(emptyList()) }
    var showAddPackageDialog by remember { mutableStateOf(false) }
    var manualPackageInput by remember { mutableStateOf("") }
    var showEnableListenerPrompt by remember { mutableStateOf(false) }

    val postNotifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPostNotifPermission = granted
        onShowMessage(if (granted) "已授予通知发送权限" else "未授予通知发送权限")
    }

    // Reload permissions and installed app list whenever user returns from system settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPostNotifPermission = NotificationBridge.hasPostNotificationPermission(context)
                refreshTrigger += 1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(refreshTrigger, settings.monitoredAppPackages) {
        appsLoading = installedApps.isEmpty()
        val loaded = withContext(Dispatchers.IO) {
            InstalledAppsCatalog.querySelectableApps(
                context = context,
                extraPackages = settings.monitoredAppPackages
            )
        }
        installedApps = loaded
        appsLoading = false
    }

    val effectiveSelectedSet = remember(settings.monitoredAppsCustomized, settings.monitoredAppPackages, installedApps) {
        if (settings.monitoredAppsCustomized) {
            settings.monitoredAppPackages.toSet()
        } else {
            installedApps.filter { it.isRecommendedFinancial }.map { it.packageName }.toSet()
        }
    }

    fun updateCustomMonitoredPackages(nextPackages: Set<String>, promptIfPermissionMissing: Boolean) {
        val sortedList = nextPackages.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
        viewModel.saveSettings(
            settings.copy(
                monitoredAppsCustomized = true,
                monitoredAppPackages = sortedList
            )
        )
        if (promptIfPermissionMissing && !isListenerEnabled) {
            showEnableListenerPrompt = true
        }
    }

    val filteredApps = remember(installedApps, searchQuery, selectedFilter, effectiveSelectedSet) {
        val q = searchQuery.trim()
        installedApps
            .filter { app ->
                val matchesFilter = when (selectedFilter) {
                    MonitoredAppFilter.ALL -> true
                    MonitoredAppFilter.SELECTED -> app.packageName in effectiveSelectedSet
                    MonitoredAppFilter.RECOMMENDED -> app.isRecommendedFinancial || app.isSmsApp
                    MonitoredAppFilter.USER -> !app.isSystemApp
                    MonitoredAppFilter.SYSTEM -> app.isSystemApp
                }
                val matchesQuery = q.isEmpty() ||
                    app.label.contains(q, ignoreCase = true) ||
                    app.packageName.contains(q, ignoreCase = true)
                matchesFilter && matchesQuery
            }
            .sortedWith(
                compareByDescending<InstalledAppEntry> { it.packageName in effectiveSelectedSet }
                    .thenByDescending { it.isRecommendedFinancial }
                    .thenByDescending { it.isSmsApp }
                    .thenBy { it.label.lowercase() }
            )
    }

    val installedCount = remember(installedApps) { installedApps.count { it.isInstalled } }
    val mayBeRestrictedByOem = !appsLoading && installedCount <= 2

    if (showEnableListenerPrompt) {
        AlertDialog(
            onDismissRequest = { showEnableListenerPrompt = false },
            icon = { Icon(AppIcons.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("需要开启通知读取权限") },
            text = {
                Text("已保存你勾选的监听应用，但系统“通知使用权”尚未开启。前往系统设置授予权限后，小本本才能实时读取这些应用的消费与动账通知。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEnableListenerPrompt = false
                        NotificationBridge.openNotificationListenerSettings(context)
                    },
                    modifier = Modifier.testTag("monitored_apps_dialog_open_listener")
                ) {
                    Text("立即去开启")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEnableListenerPrompt = false }) {
                    Text("稍后再说")
                }
            }
        )
    }

    if (showAddPackageDialog) {
        AlertDialog(
            onDismissRequest = { showAddPackageDialog = false },
            title = { Text("手动添加应用包名") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "若系统限制读取部分应用列表，可直接输入应用包名（如 com.tencent.mm 或银行 App 包名）加入监听。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = manualPackageInput,
                        onValueChange = { manualPackageInput = it },
                        label = { Text("应用包名") },
                        placeholder = { Text("例如：com.chinamworld.bocmbci") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("monitored_apps_manual_pkg_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pkg = manualPackageInput.trim()
                        if (pkg.length < 3 || !pkg.contains('.')) {
                            onShowMessage("请输入合法的应用包名（包含点号，如 com.example.app）")
                        } else {
                            val next = effectiveSelectedSet + pkg
                            updateCustomMonitoredPackages(next, promptIfPermissionMissing = true)
                            manualPackageInput = ""
                            showAddPackageDialog = false
                            onShowMessage("已添加监听包名：$pkg")
                        }
                    },
                    modifier = Modifier.testTag("monitored_apps_manual_pkg_confirm")
                ) {
                    Text("添加并勾选")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPackageDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "arrival_banner") {
                SettingsSearchArrivalBanner(
                    route = ROUTE_SETTINGS_MONITORED_APPS,
                    onDismiss = { viewModel.clearSettingsHighlight() }
                )
            }

            // 1. Permission readiness card (proactive guidance & one-tap grant)
            item(key = "permission_status_card") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isListenerEnabled) {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        } else {
                            ExpenseCoral.copy(alpha = 0.10f)
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .settingsHighlightSection(ROUTE_SETTINGS_MONITORED_APPS, "monitored_apps_card")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (isListenerEnabled) Icons.Rounded.CheckCircle else AppIcons.WarningAmber,
                                    contentDescription = null,
                                    tint = if (isListenerEnabled) IncomeJade else ExpenseCoral
                                )
                                Column {
                                    Text(
                                        text = settingsHighlightedText(
                                            if (isListenerEnabled) "通知读取权限已开启" else "尚未开启通知读取权限",
                                            ROUTE_SETTINGS_MONITORED_APPS
                                        ),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (settings.monitoredAppsCustomized) {
                                            "自定义监听模式 · 已选 ${settings.monitoredAppPackages.size} 款应用"
                                        } else {
                                            "默认智能监听 · 自动监听常见支付、银行与短信应用"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (!isListenerEnabled) {
                                Button(
                                    onClick = { NotificationBridge.openNotificationListenerSettings(context) },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("monitored_apps_open_listener_button")
                                ) {
                                    Text("去开启")
                                }
                            }
                        }

                        val needPostNotif = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPostNotifPermission
                        if (needPostNotif || !isIgnoringBattery) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (needPostNotif) {
                                    OutlinedButton(
                                        onClick = { postNotifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .minimumInteractiveComponentSize()
                                            .testTag("monitored_apps_request_post_notif")
                                    ) {
                                        Text("授权记账提醒", maxLines = 1)
                                    }
                                }
                                if (!isIgnoringBattery) {
                                    OutlinedButton(
                                        onClick = { NotificationBridge.requestIgnoreBatteryOptimizations(context) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .minimumInteractiveComponentSize()
                                            .testTag("monitored_apps_battery_button")
                                    ) {
                                        Text("加入电池白名单", maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. OEM app-list restriction hint (if device ROM hides launcher apps)
            if (mayBeRestrictedByOem) {
                item(key = "oem_restriction_hint") {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "当前系统仅返回了 $installedCount 款应用",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "部分手机系统（如 MIUI/HyperOS、OriginOS、ColorOS、HarmonyOS）默认拦截“读取应用列表”权限。请前往系统权限页开启后返回刷新，或手动输入包名添加。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val opened = NotificationBridge.openAppSystemPermissionSettings(context)
                                        if (!opened) {
                                            NotificationBridge.openAppDetailsSettings(context)
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("monitored_apps_open_oem_perm_button")
                                ) {
                                    Text("开启应用列表权限", maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = { refreshTrigger += 1 },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = "刷新列表", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            // 3. Controls & Quick Actions Card
            item(key = "controls_card") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "兜底监听其他应用的强动账通知",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "关闭后将严格仅监听已勾选应用的通知，进一步减少无关解析",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.allowOtherFinancialAppsFallback,
                                onCheckedChange = { checked ->
                                    viewModel.saveSettings(settings.copy(allowOtherFinancialAppsFallback = checked))
                                },
                                modifier = Modifier.testTag("monitored_apps_fallback_switch")
                            )
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val recommended = installedApps
                                        .filter { it.isRecommendedFinancial || it.isSmsApp }
                                        .map { it.packageName }
                                        .toSet()
                                    if (recommended.isEmpty()) {
                                        onShowMessage("当前未扫描到可勾选的金融或短信应用，可手动添加包名")
                                    } else {
                                        updateCustomMonitoredPackages(recommended, promptIfPermissionMissing = true)
                                        onShowMessage("已勾选 ${recommended.size} 款金融与短信应用")
                                    }
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("monitored_apps_select_recommended")
                            ) {
                                Text("一键勾选金融/短信")
                            }
                            OutlinedButton(
                                onClick = { showAddPackageDialog = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("monitored_apps_add_pkg_button")
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("添加包名")
                            }
                            if (settings.monitoredAppsCustomized) {
                                TextButton(
                                    onClick = {
                                        viewModel.saveSettings(
                                            settings.copy(
                                                monitoredAppsCustomized = false,
                                                monitoredAppPackages = emptyList(),
                                                allowOtherFinancialAppsFallback = true
                                            )
                                        )
                                        onShowMessage("已恢复默认监听策略")
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("monitored_apps_reset_default")
                                ) {
                                    Text("恢复默认")
                                }
                            }
                        }

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("搜索应用名称或包名（如 微信、支付宝、短信、银行）") },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "清空搜索")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("monitored_apps_search_input")
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MonitoredAppFilter.entries.forEach { filter ->
                                val label = if (filter == MonitoredAppFilter.SELECTED) {
                                    "${filter.label} (${effectiveSelectedSet.size})"
                                } else {
                                    filter.label
                                }
                                FilterChip(
                                    selected = selectedFilter == filter,
                                    onClick = { selectedFilter = filter },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("monitored_apps_filter_${filter.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            }

            if (appsLoading) {
                item(key = "loading_apps") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            Text(
                                text = "正在读取手机已安装应用…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else if (filteredApps.isEmpty()) {
                item(key = "empty_apps") {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("没有匹配的应用", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "可切换筛选标签、清空搜索词，或点击“添加包名”手动指定需要监听的应用",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(items = filteredApps, key = { it.packageName }) { app ->
                    val isChecked = app.packageName in effectiveSelectedSet
                    MonitoredAppRowItem(
                        app = app,
                        checked = isChecked,
                        iconPx = iconPx,
                        iconCache = iconCache,
                        onToggle = { nextChecked ->
                            val nextSet = if (nextChecked) {
                                effectiveSelectedSet + app.packageName
                            } else {
                                effectiveSelectedSet - app.packageName
                            }
                            updateCustomMonitoredPackages(nextSet, promptIfPermissionMissing = nextChecked)
                        }
                    )
                }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun MonitoredAppRowItem(
    app: InstalledAppEntry,
    checked: Boolean,
    iconPx: Int,
    iconCache: LruCache<String, ImageBitmap>,
    onToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val iconBitmap by produceState<ImageBitmap?>(
        initialValue = iconCache.get(app.packageName),
        key1 = app.packageName,
        key2 = app.isInstalled
    ) {
        if (!app.isInstalled) {
            value = null
            return@produceState
        }
        val cached = iconCache.get(app.packageName)
        if (cached != null) {
            value = cached
            return@produceState
        }
        val loaded = withContext(Dispatchers.IO) {
            InstalledAppsCatalog.loadCompactAppIcon(context, app.packageName, iconPx)
        }
        if (loaded != null) {
            iconCache.put(app.packageName, loaded)
        }
        value = loaded
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (checked) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .testTag("monitored_app_row_${app.packageName}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap!!,
                    contentDescription = app.label,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = app.label.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    val tagText = when {
                        !app.isInstalled -> "手动包名"
                        app.isSmsApp -> "短信"
                        app.isRecommendedFinancial -> "推荐金融"
                        app.isSystemApp -> "系统"
                        else -> null
                    }
                    if (tagText != null) {
                        Surface(
                            color = if (app.isRecommendedFinancial || app.isSmsApp) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = tagText,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (app.isRecommendedFinancial || app.isSmsApp) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Checkbox(
                checked = checked,
                onCheckedChange = onToggle,
                modifier = Modifier.testTag("monitored_app_check_${app.packageName}")
            )
        }
    }
}
