package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AiUnderstandingManager
import com.example.model.AiConfig
import com.example.model.AiProviderMode
import com.example.ui.components.formatCurrency
import com.example.ui.theme.AppIcons
import com.example.ui.theme.ExpenseCoral
import com.example.ui.theme.IncomeJade
import com.example.viewmodel.AiTestState
import com.example.viewmodel.LedgerViewModel
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiTuningAndTestScreen(
    viewModel: LedgerViewModel,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val aiConfig by viewModel.aiConfigState.collectAsStateWithLifecycle()
    val runtimeStatus by viewModel.localLlmRuntimeStatus.collectAsStateWithLifecycle()
    val isPreloading by viewModel.localLlmPreloading.collectAsStateWithLifecycle()
    val chatMessages by viewModel.aiChatMessages.collectAsStateWithLifecycle()
    val isChatSending by viewModel.aiChatSending.collectAsStateWithLifecycle()
    val aiTestState by viewModel.aiTestState.collectAsStateWithLifecycle()
    val aiManager = remember { AiUnderstandingManager() }

    var temperature by remember(aiConfig.temperature) { mutableFloatStateOf(aiConfig.temperature) }
    var topP by remember(aiConfig.topP) { mutableFloatStateOf(aiConfig.topP) }
    var topK by remember(aiConfig.topK) { mutableIntStateOf(aiConfig.topK) }
    var maxTokens by remember(aiConfig.maxTokens) { mutableIntStateOf(aiConfig.maxTokens) }
    var contextSize by remember(aiConfig.contextSize) { mutableIntStateOf(aiConfig.contextSize) }
    var threads by remember(aiConfig.threads) { mutableIntStateOf(aiConfig.threads) }
    var seedInput by remember(aiConfig.seed) { mutableStateOf(aiConfig.seed.toString()) }
    var autoUnloadSeconds by remember(aiConfig.localAutoUnloadSeconds) {
        mutableIntStateOf(aiConfig.localAutoUnloadSeconds)
    }

    var activeTestTab by rememberSaveable { mutableIntStateOf(0) }
    var useLedgerPromptInChat by rememberSaveable { mutableStateOf(false) }
    var chatInput by rememberSaveable { mutableStateOf("") }
    var parseSampleInput by rememberSaveable { mutableStateOf("") }

    fun buildTunedConfig(): AiConfig = aiConfig.copy(
        temperature = (temperature * 20f).roundToInt() / 20f,
        topP = (topP * 20f).roundToInt() / 20f,
        topK = topK.coerceIn(1, 100),
        maxTokens = maxTokens.coerceIn(64, 2048),
        contextSize = contextSize.coerceIn(512, 4096),
        threads = threads.coerceIn(1, 8),
        seed = seedInput.trim().toIntOrNull() ?: -1,
        localAutoUnloadSeconds = autoUnloadSeconds.coerceIn(-1, 600)
    )

    val currentModelName = remember(aiConfig.providerMode, aiConfig.localModelId, aiConfig.apiModelName) {
        when (aiConfig.providerMode) {
            AiProviderMode.LOCAL_SMALL_MODEL -> aiManager.resolveModelDisplayName(context, aiConfig.localModelId)
            AiProviderMode.ONLINE_API -> aiConfig.apiModelName.ifBlank { "未填写在线模型名" }
            AiProviderMode.DISABLED -> "AI 理解当前已关闭"
        }
    }

    val listState = rememberLazyListState()

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .testTag("ai_tuning_test_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Engine Status & Background Memory Control Card
            item(key = "ai_runtime_memory_card") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_runtime_memory_card")
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("当前推理引擎", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "${aiConfig.providerMode.label} · $currentModelName",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (aiConfig.providerMode == AiProviderMode.LOCAL_SMALL_MODEL) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (runtimeStatus.isLoaded) {
                                        IncomeJade.copy(alpha = 0.14f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    }
                                ) {
                                    Text(
                                        text = when {
                                            runtimeStatus.isBusy -> "推理/加载中"
                                            runtimeStatus.isLoaded -> "已载入内存"
                                            else -> "未载入(0占用)"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (runtimeStatus.isLoaded) IncomeJade else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        if (aiConfig.providerMode == AiProviderMode.LOCAL_SMALL_MODEL) {
                            val telemetryText = buildString {
                                if (runtimeStatus.isLoaded) {
                                    append("上下文 ${runtimeStatus.contextSize} · ${runtimeStatus.threads} 线程")
                                    if (runtimeStatus.lastTokensPerSecond > 0f) {
                                        append(String.format(Locale.US, " · %.1f tok/s", runtimeStatus.lastTokensPerSecond))
                                    }
                                } else {
                                    append("端侧 GGUF 采用按需加载策略，空闲或退至后台后自动释放权重，不影响后台监听保活。")
                                }
                            }
                            Text(
                                text = telemetryText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            runtimeStatus.lastError?.takeIf { it.isNotBlank() }?.let { err ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = ExpenseCoral.copy(alpha = 0.12f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = err,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ExpenseCoral,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    enabled = !isPreloading && !runtimeStatus.isBusy,
                                    onClick = {
                                        viewModel.preloadLocalGgufModel(context, buildTunedConfig())
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("ai_tuning_preload_button")
                                ) {
                                    if (isPreloading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("加载校验中...")
                                    } else {
                                        Icon(AppIcons.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("加载模型校验")
                                    }
                                }
                                OutlinedButton(
                                    enabled = runtimeStatus.isLoaded && !runtimeStatus.isBusy,
                                    onClick = { viewModel.unloadLocalGgufModel() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("ai_tuning_unload_button")
                                ) {
                                    Icon(AppIcons.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("释放内存")
                                }
                            }

                            HorizontalDivider()
                            Text("后台空闲自动释放策略（控内存）", style = MaterialTheme.typography.labelLarge)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    0 to "推理后立即释放",
                                    30 to "30 秒",
                                    45 to "45 秒(推荐)",
                                    120 to "2 分钟",
                                    -1 to "仅内存紧张时"
                                ).forEach { (sec, label) ->
                                    FilterChip(
                                        selected = autoUnloadSeconds == sec,
                                        onClick = {
                                            autoUnloadSeconds = sec
                                            viewModel.saveAiConfig(buildTunedConfig().copy(localAutoUnloadSeconds = sec))
                                        },
                                        label = { Text(label) },
                                        modifier = Modifier.testTag("ai_auto_unload_$sec")
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. LLM Parameter Tuning Card
            item(key = "ai_tuning_params_card") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_tuning_params_card")
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("大模型推理参数", style = MaterialTheme.typography.titleMedium)
                            TextButton(
                                onClick = {
                                    temperature = 0.2f
                                    topP = 0.9f
                                    topK = 40
                                    maxTokens = 384
                                    contextSize = 1024
                                    threads = 4
                                    seedInput = "-1"
                                    autoUnloadSeconds = 45
                                    viewModel.saveAiConfig(
                                        aiConfig.copy(
                                            temperature = 0.2f,
                                            topP = 0.9f,
                                            topK = 40,
                                            maxTokens = 384,
                                            contextSize = 1024,
                                            threads = 4,
                                            seed = -1,
                                            localAutoUnloadSeconds = 45
                                        )
                                    )
                                    onShowMessage("已恢复默认推理参数")
                                },
                                modifier = Modifier.testTag("ai_tuning_reset_defaults")
                            ) {
                                Icon(AppIcons.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("恢复默认")
                            }
                        }

                        // Temperature
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            val tempLabel = String.format(Locale.US, "%.2f", (temperature * 20f).roundToInt() / 20f)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Temperature（温度）", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    text = "$tempLabel ${if (temperature <= 0.35f) "· 稳健精准" else if (temperature <= 0.8f) "· 均衡" else "· 发散"}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = temperature,
                                onValueChange = { temperature = it },
                                valueRange = 0.0f..1.5f,
                                steps = 29,
                                modifier = Modifier.testTag("ai_slider_temperature")
                            )
                        }

                        // Top-P
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            val topPLabel = String.format(Locale.US, "%.2f", (topP * 20f).roundToInt() / 20f)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Top-P（核采样）", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    text = topPLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = topP,
                                onValueChange = { topP = it },
                                valueRange = 0.1f..1.0f,
                                steps = 17,
                                modifier = Modifier.testTag("ai_slider_top_p")
                            )
                        }

                        // Max Tokens
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("最大输出 Token", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    text = "$maxTokens tokens",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = maxTokens.toFloat(),
                                onValueChange = { maxTokens = (it / 32f).roundToInt() * 32 },
                                valueRange = 64f..2048f,
                                modifier = Modifier.testTag("ai_slider_max_tokens")
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(256, 384, 512, 1024).forEach { presetTokens ->
                                    FilterChip(
                                        selected = maxTokens == presetTokens,
                                        onClick = { maxTokens = presetTokens },
                                        label = { Text("$presetTokens") }
                                    )
                                }
                            }
                        }

                        if (aiConfig.providerMode != AiProviderMode.ONLINE_API) {
                            HorizontalDivider()
                            Text("端侧 GGUF 专属参数", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

                            // Top-K
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Top-K", style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        text = "$topK",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Slider(
                                    value = topK.toFloat(),
                                    onValueChange = { topK = it.roundToInt().coerceIn(1, 100) },
                                    valueRange = 1f..100f,
                                    modifier = Modifier.testTag("ai_slider_top_k")
                                )
                            }

                            // Context Size
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("上下文长度 (Context Size)", style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        text = "$contextSize",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(
                                        512 to "512 (省内存)",
                                        1024 to "1024 (推荐)",
                                        2048 to "2048",
                                        4096 to "4096"
                                    ).forEach { (ctx, label) ->
                                        FilterChip(
                                            selected = contextSize == ctx,
                                            onClick = { contextSize = ctx },
                                            label = { Text(label) },
                                            modifier = Modifier.testTag("ai_ctx_chip_$ctx")
                                        )
                                    }
                                }
                            }

                            // CPU Threads
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("CPU 推理线程数", style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        text = "$threads 线程",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Slider(
                                    value = threads.toFloat(),
                                    onValueChange = { threads = it.roundToInt().coerceIn(1, 8) },
                                    valueRange = 1f..8f,
                                    steps = 6,
                                    modifier = Modifier.testTag("ai_slider_threads")
                                )
                            }

                            // Seed
                            OutlinedTextField(
                                value = seedInput,
                                onValueChange = { raw ->
                                    if (raw.isEmpty() || raw == "-" || raw.toIntOrNull() != null) {
                                        seedInput = raw.take(10)
                                    }
                                },
                                label = { Text("随机种子 Seed（-1 为随机）") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("ai_seed_input")
                            )
                        }

                        Button(
                            onClick = {
                                viewModel.saveAiConfig(buildTunedConfig())
                                onShowMessage("已保存大模型推理参数")
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("ai_tuning_save_button")
                        ) {
                            Icon(AppIcons.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("保存参数")
                        }
                    }
                }
            }

            // 3. Interactive Test Card (Chat Test / Bill Parse Test)
            item(key = "ai_interactive_test_card") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_interactive_test_card")
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            listOf("对话测试", "账单解析测试").forEachIndexed { idx, label ->
                                SegmentedButton(
                                    selected = activeTestTab == idx,
                                    onClick = { activeTestTab = idx },
                                    shape = SegmentedButtonDefaults.itemShape(index = idx, count = 2),
                                    modifier = Modifier.testTag("ai_test_tab_$idx")
                                ) {
                                    Text(label, maxLines = 1)
                                }
                            }
                        }

                        if (activeTestTab == 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("挂载记账提示词", style = MaterialTheme.typography.labelLarge)
                                    Switch(
                                        checked = useLedgerPromptInChat,
                                        onCheckedChange = { useLedgerPromptInChat = it },
                                        modifier = Modifier.testTag("ai_chat_ledger_prompt_switch")
                                    )
                                }
                                if (chatMessages.isNotEmpty()) {
                                    TextButton(
                                        onClick = { viewModel.clearAiChatTestHistory() },
                                        modifier = Modifier.testTag("ai_chat_clear_button")
                                    ) {
                                        Text("清空对话")
                                    }
                                }
                            }

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "你好，请简单自我介绍" to "打招呼测试",
                                    "【招商银行】尾号8812储蓄卡在星巴克消费人民币42.50元" to "测试消费解析",
                                    "发起人民币元 2,000.00汇款" to "测试汇款解析"
                                ).forEach { (prompt, chipLabel) ->
                                    AssistChip(
                                        onClick = { chatInput = prompt },
                                        label = { Text(chipLabel) }
                                    )
                                }
                            }

                            if (chatMessages.isEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "发送任意消息以实测当前端侧 GGUF 模型或在线 API 的响应速度与输出质量。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(14.dp)
                                    )
                                }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    chatMessages.forEach { msg ->
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalAlignment = if (msg.isUser) Alignment.End else Alignment.Start
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(14.dp),
                                                color = when {
                                                    msg.isError -> ExpenseCoral.copy(alpha = 0.14f)
                                                    msg.isUser -> MaterialTheme.colorScheme.primaryContainer
                                                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                                                },
                                                border = if (msg.isError) {
                                                    BorderStroke(1.dp, ExpenseCoral.copy(alpha = 0.35f))
                                                } else {
                                                    null
                                                },
                                                modifier = Modifier.widthIn(max = 520.dp)
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        text = msg.content,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = when {
                                                            msg.isError -> ExpenseCoral
                                                            msg.isUser -> MaterialTheme.colorScheme.onPrimaryContainer
                                                            else -> MaterialTheme.colorScheme.onSurface
                                                        }
                                                    )
                                                    if (msg.meta.isNotBlank()) {
                                                        Text(
                                                            text = msg.meta,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = if (msg.isError) {
                                                                ExpenseCoral
                                                            } else {
                                                                MaterialTheme.colorScheme.onSurfaceVariant
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                OutlinedTextField(
                                    value = chatInput,
                                    onValueChange = { chatInput = it },
                                    placeholder = { Text("输入对话测试内容...") },
                                    minLines = 1,
                                    maxLines = 4,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("ai_chat_test_input")
                                )
                                Button(
                                    enabled = !isChatSending && chatInput.isNotBlank(),
                                    onClick = {
                                        val msg = chatInput.trim()
                                        chatInput = ""
                                        viewModel.sendAiChatTestMessage(
                                            context = context,
                                            config = buildTunedConfig(),
                                            userInput = msg,
                                            useLedgerSystemPrompt = useLedgerPromptInChat
                                        )
                                    },
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("ai_chat_test_send_button")
                                ) {
                                    if (isChatSending) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.Send,
                                            contentDescription = "发送测试消息",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            AssistChip(
                                onClick = {
                                    parseSampleInput = "【招商银行】您尾号8812储蓄卡于11月19日在星巴克（静安嘉里店）快捷支付人民币42.50元。"
                                },
                                label = { Text("填入示例通知文本") }
                            )

                            OutlinedTextField(
                                value = parseSampleInput,
                                onValueChange = { parseSampleInput = it },
                                label = { Text("待解析通知文本") },
                                placeholder = { Text("输入任意银行短信、支付通知或消费文本") },
                                minLines = 2,
                                maxLines = 4,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("ai_test_input")
                            )

                            Button(
                                enabled = aiTestState !is AiTestState.Loading,
                                onClick = {
                                    if (parseSampleInput.isBlank()) {
                                        onShowMessage("请先输入或填入测试通知文本")
                                    } else {
                                        viewModel.testAiUnderstanding(
                                            context = context,
                                            config = buildTunedConfig(),
                                            sampleTitle = "AI理解测试",
                                            sampleContent = parseSampleInput
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("ai_run_test_button")
                            ) {
                                if (aiTestState is AiTestState.Loading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("正在理解解析...")
                                } else {
                                    Icon(AppIcons.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("运行账单解析测试")
                                }
                            }

                            when (val state = aiTestState) {
                                is AiTestState.Idle, is AiTestState.Loading -> Unit
                                is AiTestState.Error -> {
                                    Surface(
                                        color = ExpenseCoral.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = state.message,
                                            color = ExpenseCoral,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }
                                is AiTestState.Success -> {
                                    val p = state.result
                                    Surface(
                                        color = IncomeJade.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "识别成功：${p.merchant} · ${formatCurrency(p.amountCents, p.currencyCode, showSign = true, type = p.type)}",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = IncomeJade,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "渠道：${p.channel} · 分类：${p.category} · 方式：${p.reasoning}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "ai_tuning_bottom_spacer") {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
