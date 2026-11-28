package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.viewmodel.SettingsHighlightTarget
import kotlinx.coroutines.delay

const val ROUTE_SETTINGS_CHANNELS = "settings/channels"
const val ROUTE_SETTINGS_BILL_FIELDS = "settings/bill_fields"
const val ROUTE_SETTINGS_NOTIFICATION_LOGS = "settings/notification_logs"

val LocalSettingsHighlight = compositionLocalOf<SettingsHighlightTarget?> { null }

data class SettingsSearchItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val categoryLabel: String,
    val route: String,
    val sectionKey: String,
    val keywords: List<String> = emptyList()
)

object SettingsSearchCatalog {
    val items: List<SettingsSearchItem> = listOf(
        // 账单工具
        SettingsSearchItem(
            id = "manual_import",
            title = "从文档或文本导入",
            subtitle = "批量导入微信/支付宝/银行 CSV、XLSX、JSON 与剪贴板账单并识别重复",
            categoryLabel = "账单工具",
            route = ROUTE_SETTINGS_MANUAL_IMPORT,
            sectionKey = "import_file_card",
            keywords = listOf("导入", "csv", "xlsx", "excel", "剪贴板", "批量", "农业银行", "微信账单", "支付宝账单")
        ),
        SettingsSearchItem(
            id = "books_mgmt",
            title = "账本管理",
            subtitle = "创建与管理独立账本，支持自定义颜色、按账本分析与导出",
            categoryLabel = "账单工具",
            route = ROUTE_SETTINGS_BOOKS,
            sectionKey = "books_header",
            keywords = listOf("账本", "多账本", "独立账本", "旅行账本", "导出账本")
        ),
        SettingsSearchItem(
            id = "category_mgmt",
            title = "分类管理（图标、排序与归属）",
            subtitle = "父子折叠列表管理、长按拖动排序与悬停高光归属、切换账单显示子类/父类名、快捷查看关联账单且均可撤回",
            categoryLabel = "账单工具",
            route = ROUTE_SETTINGS_CATEGORIES,
            sectionKey = "category_header_card",
            keywords = listOf("分类", "图标", "自定义分类", "分类归属", "子类", "父类", "排序", "关联账单", "撤回", "餐饮", "购物")
        ),
        SettingsSearchItem(
            id = "channel_mgmt",
            title = "渠道管理（排序与归属）",
            subtitle = "父子折叠列表管理、长按排序与悬停高光归属、切换账单显示子渠道/父渠道名、快捷查看关联账单且均可撤回",
            categoryLabel = "账单工具",
            route = ROUTE_SETTINGS_CHANNELS,
            sectionKey = "channel_header_info",
            keywords = listOf("渠道", "渠道归属", "子类", "父类", "排序", "关联账单", "撤回", "花呗", "余额宝", "零钱", "零钱通", "支付宝", "微信支付")
        ),
        SettingsSearchItem(
            id = "bill_fields_mgmt",
            title = "账单信息与可选字段排序",
            subtitle = "自定义账单可选信息类别（商品/类型/状态/单号及自定义项），长按排序并配置折叠进更多信息",
            categoryLabel = "账单工具",
            route = ROUTE_SETTINGS_BILL_FIELDS,
            sectionKey = "bill_fields_header",
            keywords = listOf("账单信息", "账单字段", "可选信息", "自定义字段", "更多账单信息", "折叠", "排序", "商品", "单号")
        ),
        SettingsSearchItem(
            id = "budget_and_behavior",
            title = "月度预算与自动记账",
            subtitle = "设置每月预算上限、自动记账开关、手动确认与成单通知提醒",
            categoryLabel = "账单工具",
            route = ROUTE_SETTINGS_BILL_BEHAVIOR,
            sectionKey = "behavior_preferences_card",
            keywords = listOf("预算", "月度预算", "自动记账", "手动确认", "通知提醒", "智能语义")
        ),
        SettingsSearchItem(
            id = "currency_and_rates",
            title = "基准币种与汇率库",
            subtitle = "配置主币种、多币种启停、汇率数据源与自动更新频率",
            categoryLabel = "账单工具",
            route = ROUTE_SETTINGS_CURRENCY,
            sectionKey = "base_currency",
            keywords = listOf("币种", "汇率", "外币", "美元", "港币", "日元", "欧元", "自动更新汇率")
        ),
        SettingsSearchItem(
            id = "smart_dedup",
            title = "智能去重",
            subtitle = "按金额与短时窗口筛选重复账单，支持删除或改为不计收支的转账",
            categoryLabel = "账单工具",
            route = ROUTE_SETTINGS_DEDUP,
            sectionKey = "dedup_header",
            keywords = listOf("去重", "重复", "智能去重", "不计收支", "排除去重")
        ),
        SettingsSearchItem(
            id = "storage_opt",
            title = "存储占用与清理优化",
            subtitle = "查看数据库与缓存体积，执行 VACUUM 压缩与历史通知原文精简",
            categoryLabel = "账单工具",
            route = ROUTE_SETTINGS_STORAGE_OPT,
            sectionKey = "storage_overview",
            keywords = listOf("存储", "空间", "清理", "缓存", "数据库", "vacuum", "内存")
        ),

        // 通知识别与规则
        SettingsSearchItem(
            id = "parser_test_bench",
            title = "通知解析测试台",
            subtitle = "粘贴支付通知或银行短信测试规则匹配、AI 理解与学习新模版",
            categoryLabel = "通知识别与规则",
            route = ROUTE_SETTINGS_PARSER,
            sectionKey = "test_bench",
            keywords = listOf("测试", "通知解析", "短信测试", "识别测试")
        ),
        SettingsSearchItem(
            id = "parser_custom_rules",
            title = "自定义识别规则与忽略规则",
            subtitle = "新增、编辑、启停关键词规则、格式模版与非账单忽略规则",
            categoryLabel = "通知识别与规则",
            route = ROUTE_SETTINGS_PARSER,
            sectionKey = "custom_rules_header",
            keywords = listOf("规则", "关键词", "忽略规则", "非账单", "模版", "正则")
        ),
        SettingsSearchItem(
            id = "monitored_apps",
            title = "自定义监听应用（图形化选择）",
            subtitle = "勾选需要监听通知的微信、支付宝、短信、银行或其他已安装应用",
            categoryLabel = "通知识别与规则",
            route = ROUTE_SETTINGS_MONITORED_APPS,
            sectionKey = "monitored_apps_card",
            keywords = listOf("监听应用", "选择应用", "应用通知", "微信", "支付宝", "短信", "银行app", "包名", "通知监听")
        ),
        SettingsSearchItem(
            id = "notification_logs",
            title = "通知读取日志与保留策略",
            subtitle = "仅记录所选监听应用的通知，可选保存条数与天数，支持无限条数永久保存",
            categoryLabel = "通知识别与规则",
            route = ROUTE_SETTINGS_NOTIFICATION_LOGS,
            sectionKey = "notification_log_retention_card",
            keywords = listOf("日志", "通知日志", "读取记录", "保留时间", "永久保存", "无限条数", "通知记录")
        ),

        // AI理解
        SettingsSearchItem(
            id = "ai_provider_mode",
            title = "AI 理解模式与开源小模型",
            subtitle = "选择本地开源小模型权重下载/导入，或连接在线 OpenAI 兼容 API",
            categoryLabel = "AI理解",
            route = ROUTE_SETTINGS_AI,
            sectionKey = "ai_mode_card",
            keywords = listOf("ai", "大模型", "小模型", "本地模型", "api", "权重", "huggingface", "ollama")
        ),
        SettingsSearchItem(
            id = "ai_system_prompt",
            title = "AI 理解提示词与参考规则",
            subtitle = "查看、编辑或重置 AI 系统提示词，折叠/勾选参考识别规则",
            categoryLabel = "AI理解",
            route = ROUTE_SETTINGS_AI,
            sectionKey = "ai_prompt_card",
            keywords = listOf("提示词", "prompt", "系统提示词", "参考规则", "ai提示词")
        ),
        SettingsSearchItem(
            id = "ai_tuning_and_test",
            title = "AI 大模型调参与对话测试",
            subtitle = "调节温度、Top-P/Top-K、上下文、线程与空闲自动释放，支持多轮对话与账单解析测试",
            categoryLabel = "AI理解 · 调参与测试",
            route = ROUTE_SETTINGS_AI_TUNING,
            sectionKey = "ai_tuning_params_card",
            keywords = listOf("调参", "温度", "temperature", "top_p", "top_k", "上下文", "对话测试", "gguf", "释放内存", "模型测试")
        ),

        // 备份与恢复
        SettingsSearchItem(
            id = "local_backup_and_export",
            title = "加密备份、恢复与文件导出",
            subtitle = "AES-256-GCM 加密 ZIP 备份与兼容旧版 JSON 恢复，导出 CSV / XLSX / JSON（附隐私提示）",
            categoryLabel = "备份与恢复",
            route = ROUTE_SETTINGS_LOCAL_BACKUP,
            sectionKey = "local_export_spreadsheet_card",
            keywords = listOf("备份", "加密备份", "zip", "密码", "恢复", "导出", "csv", "xlsx", "json", "定时备份提醒")
        ),
        SettingsSearchItem(
            id = "webdav_sync",
            title = "WebDAV 云端同步",
            subtitle = "配置坚果云 / Alist / Nextcloud WebDAV 加密云备份与拉取恢复",
            categoryLabel = "备份与恢复",
            route = ROUTE_SETTINGS_DATA_HUB,
            sectionKey = "webdav_form",
            keywords = listOf("webdav", "云备份", "坚果云", "同步", "云端")
        ),

        // 个性化设置
        SettingsSearchItem(
            id = "theme_dynamic_color",
            title = "莫奈动态取色与调色盘风格",
            subtitle = "开启动态取色、自定义主题主色与 Tonal / Expressive / Vibrant 调色盘",
            categoryLabel = "个性化设置 · 主题配色",
            route = ROUTE_SETTINGS_APPEARANCE_THEME,
            sectionKey = "color_and_palette",
            keywords = listOf("个性化", "主题", "莫奈", "动态取色", "配色", "调色盘", "主题色")
        ),
        SettingsSearchItem(
            id = "dark_mode_oled",
            title = "深色模式与 OLED 纯黑背景",
            subtitle = "切换浅色/深色/跟随系统，并可在深色模式下启用 OLED 纯黑省电背景",
            categoryLabel = "个性化设置 · 主题配色",
            route = ROUTE_SETTINGS_APPEARANCE_THEME,
            sectionKey = "theme_mode_and_oled",
            keywords = listOf("个性化", "深色", "暗黑", "夜间", "oled", "纯黑", "浅色")
        ),
        SettingsSearchItem(
            id = "font_and_dpi",
            title = "数字字体、页面缩放与显示密度 (DPI)",
            subtitle = "调整数字字体、DPI 缩放比例（点击应用后生效）与卡片紧凑度",
            categoryLabel = "个性化设置 · 显示与排版",
            route = ROUTE_SETTINGS_APPEARANCE_DISPLAY,
            sectionKey = "page_dpi_scaling",
            keywords = listOf("个性化", "dpi", "缩放", "密度", "字体", "卡片密度", "角标")
        ),
        SettingsSearchItem(
            id = "bottom_tab_order",
            title = "底部导航栏排序与账本 Tab 显隐",
            subtitle = "自定义底部 Tab 顺序，支持隐藏或显示「账本」入口",
            categoryLabel = "个性化设置 · 主页与导航",
            route = ROUTE_SETTINGS_APPEARANCE_HOME_NAV,
            sectionKey = "bottom_tab_bar_customization",
            keywords = listOf("个性化", "tab", "底栏", "底部导航", "账本tab", "排序", "隐藏")
        ),
        SettingsSearchItem(
            id = "home_batch_load_settings",
            title = "首页账单单次加载条数",
            subtitle = "自定义主页初次加载及触底上划续载的条数，提升进出流畅度",
            categoryLabel = "个性化设置 · 主页与导航",
            route = ROUTE_SETTINGS_APPEARANCE_HOME_NAV,
            sectionKey = "home_batch_load_settings",
            keywords = listOf("个性化", "加载", "分批加载", "条数", "100条", "性能", "流畅", "上划加载")
        ),
        SettingsSearchItem(
            id = "feature_tips_management",
            title = "功能提示管理（一键关闭 / 重新打开）",
            subtitle = "一键关闭所有页面提示条，或逐项重新打开已关闭的功能提示",
            categoryLabel = "个性化设置 · 主页与导航",
            route = ROUTE_SETTINGS_APPEARANCE_HOME_NAV,
            sectionKey = "feature_tips_management",
            keywords = listOf("个性化", "提示", "功能提示", "关闭提示", "隐藏提示", "重新打开提示", "引导")
        ),
        SettingsSearchItem(
            id = "home_brand_tips",
            title = "主页标题、Tips 与悬浮按钮位置",
            subtitle = "自定义顶部标题与图标尺寸、财务 Tips 文案以及右下/左下悬浮按钮",
            categoryLabel = "个性化设置 · 主页与导航",
            route = ROUTE_SETTINGS_APPEARANCE_HOME_NAV,
            sectionKey = "home_brand_header_customization",
            keywords = listOf("个性化", "主页", "tips", "标题", "悬浮按钮", "fab", "置顶")
        ),
        SettingsSearchItem(
            id = "haptic_feedback",
            title = "触感反馈（跟随系统 / 强度与分场景调节）",
            subtitle = "开启或关闭触感反馈，支持跟随系统触感、自定义振动强度与各交互场景细粒度开关",
            categoryLabel = "个性化设置 · 触感与交互",
            route = ROUTE_SETTINGS_APPEARANCE_INTERACTION,
            sectionKey = "haptic_feedback_card",
            keywords = listOf("个性化", "触感", "触感反馈", "震动", "振动", "马达", "跟随系统", "强度", "haptic")
        ),
        SettingsSearchItem(
            id = "interaction_gestures",
            title = "导航动画、预测性返回与撤回横栏时长",
            subtitle = "设置 Tab 切换动画、预测性返回样式与底部提示/撤回横栏停留时长",
            categoryLabel = "个性化设置 · 触感与交互",
            route = ROUTE_SETTINGS_APPEARANCE_INTERACTION,
            sectionKey = "navigation_and_gestures",
            keywords = listOf("个性化", "手势", "预测性返回", "撤回", "横栏", "提示时长", "动画")
        ),
        SettingsSearchItem(
            id = "icon_and_shortcut",
            title = "图标风格与创建桌面快捷方式",
            subtitle = "桌面图标动态适配系统亮色/暗色模式（含莫奈徽标），支持自定义桌面快捷方式",
            categoryLabel = "个性化设置 · 图标与快捷方式",
            route = ROUTE_SETTINGS_APPEARANCE_ICON,
            sectionKey = "icon_style_settings",
            keywords = listOf("个性化", "图标", "桌面图标", "暗色图标", "深色图标", "快捷方式", "莫奈图标", "图标风格", "应用图标")
        ),

        // 权限管理 & 关于
        SettingsSearchItem(
            id = "permissions_keepalive",
            title = "通知监听、无障碍保活与电池白名单",
            subtitle = "管理自动记账核心权限、Root 保活与后台常驻状态",
            categoryLabel = "权限管理",
            route = ROUTE_SETTINGS_PERMISSIONS,
            sectionKey = "permissions_card",
            keywords = listOf("权限", "通知监听", "无障碍", "保活", "电池", "后台", "root", "白名单")
        ),
        SettingsSearchItem(
            id = "about_updates",
            title = "检查更新、下载镜像与版本信息",
            subtitle = "查看当前版本、切换 GitHub 加速镜像并检查新版本",
            categoryLabel = "关于",
            route = ROUTE_SETTINGS_ABOUT,
            sectionKey = "about_update_card",
            keywords = listOf("更新", "检查更新", "版本", "镜像", "github", "关于")
        )
    )

    fun search(rawQuery: String): List<SettingsSearchItem> {
        val tokens = rawQuery.trim()
            .split(Regex("""[\s\u3000]+"""))
            .filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()

        return items
            .mapNotNull { item ->
                val haystack = buildString {
                    append(item.title).append(' ')
                    append(item.subtitle).append(' ')
                    append(item.categoryLabel).append(' ')
                    append(item.keywords.joinToString(" "))
                }
                if (tokens.all { tok -> haystack.contains(tok, ignoreCase = true) }) {
                    val score = tokens.sumOf { tok ->
                        when {
                            item.title.contains(tok, ignoreCase = true) -> 10
                            item.keywords.any { it.equals(tok, ignoreCase = true) } -> 8
                            item.keywords.any { it.contains(tok, ignoreCase = true) } -> 5
                            item.subtitle.contains(tok, ignoreCase = true) -> 3
                            else -> 1
                        }
                    }
                    item to score
                } else {
                    null
                }
            }
            .sortedByDescending { it.second }
            .map { it.first }
    }
}

fun buildHighlightedSettingsAnnotatedString(
    text: String,
    rawQuery: String?,
    highlightColor: Color,
    highlightBackground: Color = highlightColor.copy(alpha = 0.14f)
): AnnotatedString {
    val tokens = rawQuery.orEmpty()
        .trim()
        .split(Regex("""[\s\u3000]+"""))
        .filter { it.isNotEmpty() }
        .distinct()
    if (tokens.isEmpty() || text.isEmpty()) return AnnotatedString(text)

    val matchedMask = BooleanArray(text.length)
    val lowerText = text.lowercase()
    tokens.forEach { token ->
        val lowerTok = token.lowercase()
        var startIdx = 0
        while (startIdx < lowerText.length) {
            val found = lowerText.indexOf(lowerTok, startIdx)
            if (found < 0) break
            val end = (found + lowerTok.length).coerceAtMost(text.length)
            for (i in found until end) {
                matchedMask[i] = true
            }
            startIdx = end.coerceAtLeast(found + 1)
        }
    }

    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val isHit = matchedMask[i]
            var j = i + 1
            while (j < text.length && matchedMask[j] == isHit) {
                j++
            }
            val segment = text.substring(i, j)
            if (isHit) {
                withStyle(
                    SpanStyle(
                        color = highlightColor,
                        fontWeight = FontWeight.Bold,
                        background = highlightBackground
                    )
                ) {
                    append(segment)
                }
            } else {
                append(segment)
            }
            i = j
        }
    }
}

@Composable
fun settingsHighlightedText(
    text: String,
    route: String? = null,
    explicitQuery: String? = null
): AnnotatedString {
    val activeHighlight = LocalSettingsHighlight.current
    val effectiveQuery = explicitQuery ?: activeHighlight
        ?.takeIf { route == null || it.route == route }
        ?.query
    return buildHighlightedSettingsAnnotatedString(
        text = text,
        rawQuery = effectiveQuery,
        highlightColor = MaterialTheme.colorScheme.primary
    )
}

@Composable
fun SettingsAutoScrollEffect(
    listState: LazyListState,
    route: String,
    sectionKeysInOrder: List<String>
) {
    val target = LocalSettingsHighlight.current
    LaunchedEffect(target?.token, route) {
        if (target != null && target.route == route) {
            val index = sectionKeysInOrder.indexOf(target.sectionKey)
            if (index >= 0) {
                delay(90L)
                runCatching { listState.animateScrollToItem(index) }
            }
        }
    }
}

@Composable
fun Modifier.settingsHighlightSection(
    route: String,
    sectionKey: String
): Modifier {
    val target = LocalSettingsHighlight.current
    val isHighlighted = target != null && target.route == route && target.sectionKey == sectionKey
    return if (isHighlighted) {
        this.border(
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(20.dp)
        )
    } else {
        this
    }
}

@Composable
fun SettingsSearchArrivalBanner(
    route: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val target = LocalSettingsHighlight.current
    if (target == null || target.route != route || target.query.isBlank()) return

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = buildHighlightedSettingsAnnotatedString(
                    text = "已为您定位设置关键词「${target.query}」",
                    rawQuery = target.query,
                    highlightColor = MaterialTheme.colorScheme.primary
                ),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "关闭定位高亮",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
