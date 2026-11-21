package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import com.example.ui.theme.AppIcons
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AiUnderstandingManager
import com.example.data.LocalModelCatalog
import com.example.model.AiConfig
import com.example.model.AiProviderMode
import com.example.ui.components.formatCurrency
import com.example.ui.theme.ExpenseCoral
import com.example.ui.theme.IncomeJade
import com.example.viewmodel.AiModelDownloadState
import com.example.viewmodel.AiTestState
import com.example.viewmodel.LedgerViewModel
import com.example.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiSettingsScreen(
    viewModel: LedgerViewModel,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val aiConfig by viewModel.aiConfigState.collectAsStateWithLifecycle()
    val downloadState by viewModel.aiModelDownloadState.collectAsStateWithLifecycle()
    val customModels by viewModel.customModelsState.collectAsStateWithLifecycle()
    val aiTestState by viewModel.aiTestState.collectAsStateWithLifecycle()
    val rulesState by viewModel.rulesState.collectAsStateWithLifecycle()
    val aiManager = remember { AiUnderstandingManager() }
    val allRules = (rulesState as? UiState.Success)?.data ?: emptyList()

    LaunchedEffect(Unit) {
        viewModel.refreshCustomModels(context)
    }

    val importWeightLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importLocalAiWeight(context, uri)
        }
    }

    var selectedMode by remember(aiConfig.providerMode) { mutableStateOf(aiConfig.providerMode) }
    var selectedLocalModelId by remember(aiConfig.localModelId) { mutableStateOf(aiConfig.localModelId) }
    var selectedMirror by remember(aiConfig.localModelMirror) { mutableStateOf(aiConfig.localModelMirror) }
    var apiUrlInput by remember(aiConfig.apiBaseUrl) { mutableStateOf(aiConfig.apiBaseUrl) }
    var apiKeyInput by remember(aiConfig.apiKey) { mutableStateOf(aiConfig.apiKey) }
    var apiModelInput by remember(aiConfig.apiModelName) { mutableStateOf(aiConfig.apiModelName) }
    var fallbackOnly by remember(aiConfig.useAiAsFallbackOnly) { mutableStateOf(aiConfig.useAiAsFallbackOnly) }
    val sanitizedStoredPrompt = remember(aiConfig.customSystemPrompt) {
        AiUnderstandingManager.sanitizeStoredCustomPrompt(aiConfig.customSystemPrompt)
    }
    var promptEditorInput by remember(sanitizedStoredPrompt) {
        mutableStateOf(sanitizedStoredPrompt.ifBlank { AiUnderstandingManager.DEFAULT_SYSTEM_PROMPT })
    }
    var selectedPromptRuleIds by remember(aiConfig.selectedPromptRuleIds) {
        mutableStateOf(aiConfig.selectedPromptRuleIds)
    }
    var showPromptPreview by remember { mutableStateOf(false) }
    var showApiKey by remember { mutableStateOf(false) }

    var testSampleInput by remember { mutableStateOf("") }

    fun normalizedCustomPromptValue(raw: String): String =
        AiUnderstandingManager.sanitizeStoredCustomPrompt(raw)

    fun currentEditedConfig(): AiConfig = AiConfig(
        providerMode = selectedMode,
        localModelId = selectedLocalModelId,
        localModelMirror = selectedMirror,
        apiBaseUrl = apiUrlInput.trim(),
        apiKey = apiKeyInput.trim(),
        apiModelName = apiModelInput.trim(),
        useAiAsFallbackOnly = fallbackOnly,
        customSystemPrompt = normalizedCustomPromptValue(promptEditorInput),
        selectedPromptRuleIds = selectedPromptRuleIds
    )

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Mode Switcher Card
            item(key = "ai_mode_card") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(AppIcons.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("AI理解模式", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            text = selectedMode.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            AiProviderMode.entries.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = selectedMode == mode,
                                    onClick = {
                                        selectedMode = mode
                                        viewModel.saveAiConfig(currentEditedConfig().copy(providerMode = mode))
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = AiProviderMode.entries.size
                                    ),
                                    modifier = Modifier.testTag("ai_mode_${mode.name}")
                                ) {
                                    Text(mode.label, maxLines = 1)
                                }
                            }
                        }

                        if (selectedMode != AiProviderMode.DISABLED) {
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("仅在规则无法确定时触发 AI（推荐）", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = "关闭后将优先调用 AI 对所有动账通知进行深度语义与分类提取",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = fallbackOnly,
                                    onCheckedChange = { checked ->
                                        fallbackOnly = checked
                                        viewModel.saveAiConfig(currentEditedConfig().copy(useAiAsFallbackOnly = checked))
                                    },
                                    modifier = Modifier.testTag("ai_fallback_switch")
                                )
                            }
                        }
                    }
                }
            }

            // 2. Local Small Model On-Demand Download Card
            if (selectedMode == AiProviderMode.LOCAL_SMALL_MODEL) {
                item(key = "local_model_card") {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("本地开源小模型（按需下载）", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "选择适合的开源中文模型词表与配置包按需下载，下载后纯离线运行（零额外可执行代码加载，合规安全）。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Text("模型下载源", style = MaterialTheme.typography.labelLarge)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    "https://hf-mirror.com" to "HF-Mirror 国内加速",
                                    "https://huggingface.co" to "HuggingFace 官方源"
                                ).forEach { (url, label) ->
                                    FilterChip(
                                        selected = selectedMirror == url,
                                        onClick = {
                                            selectedMirror = url
                                            viewModel.saveAiConfig(currentEditedConfig().copy(localModelMirror = url))
                                        },
                                        label = { Text(label) }
                                    )
                                }
                            }

                            when (val dState = downloadState) {
                                is AiModelDownloadState.Downloading -> {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(dState.statusText, style = MaterialTheme.typography.bodySmall)
                                            LinearProgressIndicator(
                                                progress = { dState.progress },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                                is AiModelDownloadState.Success -> {
                                    Text(dState.message, color = IncomeJade, style = MaterialTheme.typography.bodySmall)
                                }
                                is AiModelDownloadState.Error -> {
                                    Text(dState.message, color = ExpenseCoral, style = MaterialTheme.typography.bodySmall)
                                }
                                is AiModelDownloadState.Idle -> Unit
                            }

                            fun openUrlInBrowser(url: String) {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                runCatching {
                                    context.startActivity(intent)
                                }.onFailure { err ->
                                    onShowMessage("无法打开浏览器：${err.message ?: "请检查系统浏览器"}")
                                }
                            }

                            OutlinedButton(
                                enabled = downloadState !is AiModelDownloadState.Downloading,
                                onClick = { importWeightLauncher.launch(arrayOf("*/*")) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("import_custom_model_weight_button")
                            ) {
                                Icon(AppIcons.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("从本地导入模型权重 / 词表 (.gguf / .onnx / .tflite / .json)")
                            }

                            OutlinedButton(
                                onClick = { openUrlInBrowser(LocalModelCatalog.weightsBrowserPortalUrl(selectedMirror)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("open_weights_website_button")
                            ) {
                                Icon(AppIcons.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("打开浏览器查找/下载模型权重 (${if (selectedMirror.contains("hf-mirror")) "HF-Mirror" else "HuggingFace"})")
                            }

                            if (customModels.isNotEmpty()) {
                                HorizontalDivider()
                                Text("已导入的自定义本地权重 (${customModels.size})", style = MaterialTheme.typography.labelLarge)
                                customModels.forEach { custom ->
                                    val isSelected = selectedLocalModelId == custom.id
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f)
                                        },
                                        border = BorderStroke(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(custom.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                                    val vocabText = if (custom.vocabTokenCount > 0) " · 词元 ${custom.vocabTokenCount}" else ""
                                                    Text(
                                                        text = "${custom.formatLabel} · ${custom.fileName} (${custom.sizeBytes / 1024} KB$vocabText)",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Icon(
                                                    imageVector = Icons.Rounded.CheckCircle,
                                                    contentDescription = "已导入",
                                                    tint = IncomeJade
                                                )
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Button(
                                                    onClick = {
                                                        selectedLocalModelId = custom.id
                                                        viewModel.saveAiConfig(
                                                            currentEditedConfig().copy(
                                                                providerMode = AiProviderMode.LOCAL_SMALL_MODEL,
                                                                localModelId = custom.id
                                                            )
                                                        )
                                                        onShowMessage("已启用自导入模型：${custom.displayName}")
                                                    },
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .testTag("select_custom_model_${custom.id}")
                                                ) {
                                                    Text(if (isSelected) "当前已启用" else "切换使用")
                                                }
                                                OutlinedButton(
                                                    onClick = { viewModel.deleteCustomImportedModel(context, custom.id) },
                                                    modifier = Modifier.testTag("delete_custom_model_${custom.id}")
                                                ) {
                                                    Icon(AppIcons.DeleteOutline, contentDescription = "移除自定义模型", modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("移除")
                                                }
                                            }
                                        }
                                    }
                                }
                                HorizontalDivider()
                            }

                            Text("内置推荐开源小模型（${LocalModelCatalog.presets.size} 档体积由小到大）", style = MaterialTheme.typography.labelLarge)

                            LocalModelCatalog.presets.forEach { preset ->
                                val status = remember(preset.id, downloadState, selectedLocalModelId, customModels) {
                                    aiManager.getInstallStatus(context, preset.id)
                                }
                                val isSelected = selectedLocalModelId == preset.id
                                val isDownloadingThis = downloadState is AiModelDownloadState.Downloading &&
                                    (downloadState as AiModelDownloadState.Downloading).modelId == preset.id
                                val presetRepoUrl = LocalModelCatalog.modelRepoUrl(selectedMirror, preset.repoId)

                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f)
                                    },
                                    border = BorderStroke(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(preset.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                                Text(
                                                    text = "${preset.repoId} · ${preset.license} · ${preset.sizeHint}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (status.installed) {
                                                Icon(
                                                    imageVector = Icons.Rounded.CheckCircle,
                                                    contentDescription = "已下载",
                                                    tint = IncomeJade
                                                )
                                            }
                                        }
                                        Text(
                                            text = preset.summary,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (!status.installed) {
                                                Button(
                                                    enabled = downloadState !is AiModelDownloadState.Downloading,
                                                    onClick = {
                                                        selectedLocalModelId = preset.id
                                                        viewModel.downloadLocalAiModel(context, preset.id, selectedMirror)
                                                    },
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .minimumInteractiveComponentSize()
                                                        .testTag("download_model_${preset.id}")
                                                ) {
                                                    Icon(AppIcons.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(if (isDownloadingThis) "下载中..." else "按需下载模型")
                                                }
                                            } else {
                                                Button(
                                                    onClick = {
                                                        selectedLocalModelId = preset.id
                                                        viewModel.saveAiConfig(
                                                            currentEditedConfig().copy(
                                                                providerMode = AiProviderMode.LOCAL_SMALL_MODEL,
                                                                localModelId = preset.id
                                                            )
                                                        )
                                                        onShowMessage("已启用本地模型：${preset.name}")
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text(if (isSelected) "当前已启用 (${status.totalBytes / 1024} KB)" else "切换使用 (${status.totalBytes / 1024} KB)")
                                                }
                                                OutlinedButton(
                                                    enabled = downloadState !is AiModelDownloadState.Downloading,
                                                    onClick = { viewModel.deleteLocalAiModel(context, preset.id) }
                                                ) {
                                                    Icon(AppIcons.DeleteOutline, contentDescription = "删除本地模型", modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("清理")
                                                }
                                            }
                                            OutlinedButton(
                                                onClick = { openUrlInBrowser(presetRepoUrl) },
                                                modifier = Modifier
                                                    .minimumInteractiveComponentSize()
                                                    .testTag("open_model_site_${preset.id}")
                                            ) {
                                                Icon(AppIcons.OpenInNew, contentDescription = "浏览器打开权重下载页", modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("网页下载")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Online OpenAI-Compatible API Card
            if (selectedMode == AiProviderMode.ONLINE_API) {
                item(key = "online_api_card") {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("在线 AI API 配置（OpenAI 兼容格式）", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "支持填入任意 OpenAI 兼容接口（DeepSeek、硅基流动、通义千问、OpenAI 或自建 Ollama/vLLM 网关），密钥仅保存在本地。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AssistChip(
                                    onClick = {
                                        apiUrlInput = "https://api.deepseek.com/v1/chat/completions"
                                        apiModelInput = "deepseek-chat"
                                    },
                                    label = { Text("DeepSeek") }
                                )
                                AssistChip(
                                    onClick = {
                                        apiUrlInput = "https://api.siliconflow.cn/v1/chat/completions"
                                        apiModelInput = "Qwen/Qwen2.5-7B-Instruct"
                                    },
                                    label = { Text("硅基流动") }
                                )
                                AssistChip(
                                    onClick = {
                                        apiUrlInput = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
                                        apiModelInput = "qwen-turbo"
                                    },
                                    label = { Text("通义千问") }
                                )
                                AssistChip(
                                    onClick = {
                                        apiUrlInput = "https://api.openai.com/v1/chat/completions"
                                        apiModelInput = "gpt-4o-mini"
                                    },
                                    label = { Text("OpenAI") }
                                )
                            }

                            OutlinedTextField(
                                value = apiUrlInput,
                                onValueChange = { apiUrlInput = it },
                                label = { Text("API 地址 (Base URL 或完整 /chat/completions)") },
                                placeholder = { Text("https://api.deepseek.com/v1/chat/completions") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("ai_api_url_input")
                            )

                            OutlinedTextField(
                                value = apiKeyInput,
                                onValueChange = { apiKeyInput = it },
                                label = { Text("API Key") },
                                placeholder = { Text("sk-...") },
                                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                trailingIcon = {
                                    IconButton(onClick = { showApiKey = !showApiKey }) {
                                        Icon(
                                            imageVector = if (showApiKey) AppIcons.VisibilityOff else AppIcons.Visibility,
                                            contentDescription = if (showApiKey) "隐藏密钥" else "显示密钥"
                                        )
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("ai_api_key_input")
                            )

                            OutlinedTextField(
                                value = apiModelInput,
                                onValueChange = { apiModelInput = it },
                                label = { Text("模型名称 (Model ID)") },
                                placeholder = { Text("例如：deepseek-chat / qwen-turbo") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("ai_api_model_input")
                            )

                            Button(
                                onClick = {
                                    viewModel.saveAiConfig(currentEditedConfig())
                                    onShowMessage("已保存在线 AI 接口配置")
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("ai_save_config_button")
                            ) {
                                Icon(AppIcons.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("保存 API 配置")
                            }
                        }
                    }
                }
            }

            // 3.5 Prompt Customization & Rules Reference Card
            if (selectedMode != AiProviderMode.DISABLED) {
                item(key = "ai_prompt_card") {
                    val editedConfig = currentEditedConfig()
                    val selectedRuleObjects = AiUnderstandingManager.resolveSelectedRules(editedConfig, allRules)
                    val effectivePromptPreview = AiUnderstandingManager.buildEffectiveSystemPrompt(
                        config = editedConfig,
                        selectedRules = selectedRuleObjects
                    )
                    val isUsingCustomPrompt = normalizedCustomPromptValue(promptEditorInput).isNotEmpty()

                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_prompt_settings_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("提示词与规则参考", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = if (isUsingCustomPrompt) {
                                            "当前使用自定义提示词 · 已选 ${selectedRuleObjects.size} 条规则作为参考"
                                        } else {
                                            "当前使用内置预设提示词 · 已选 ${selectedRuleObjects.size} 条规则作为参考"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        promptEditorInput = AiUnderstandingManager.DEFAULT_SYSTEM_PROMPT
                                        viewModel.saveAiConfig(currentEditedConfig().copy(customSystemPrompt = ""))
                                        onShowMessage("已重置为最新默认预设提示词（含零钱提现/本人转账不计收支规则）")
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("ai_reset_prompt_button")
                                ) {
                                    Icon(AppIcons.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("重置预设")
                                }
                            }

                            OutlinedTextField(
                                value = promptEditorInput,
                                onValueChange = { promptEditorInput = it.take(4000) },
                                label = { Text("系统提示词（System Prompt）") },
                                minLines = 5,
                                maxLines = 10,
                                textStyle = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("ai_prompt_editor_input")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.saveAiConfig(currentEditedConfig())
                                        onShowMessage("已保存提示词与规则参考配置")
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("ai_save_prompt_button")
                                ) {
                                    Icon(AppIcons.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("保存提示词")
                                }
                                OutlinedButton(
                                    onClick = { showPromptPreview = !showPromptPreview },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("ai_toggle_prompt_preview_button")
                                ) {
                                    Text(if (showPromptPreview) "收起预览" else "查看最终提示词")
                                }
                            }

                            HorizontalDivider()

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("选择识别规则作为 AI 提示词参考", style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        text = "选中的规则会注入系统提示词并在本地/在线推理时优先参考",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (allRules.isNotEmpty()) {
                                    AssistChip(
                                        onClick = {
                                            selectedPromptRuleIds = if (selectedPromptRuleIds.size >= allRules.size) {
                                                emptyList()
                                            } else {
                                                allRules.map { it.id }
                                            }
                                            viewModel.saveAiConfig(
                                                currentEditedConfig().copy(selectedPromptRuleIds = selectedPromptRuleIds)
                                            )
                                        },
                                        label = {
                                            Text(if (selectedPromptRuleIds.size >= allRules.size) "全不选" else "全选")
                                        },
                                        modifier = Modifier.testTag("ai_prompt_rules_select_all")
                                    )
                                }
                            }

                            if (allRules.isEmpty()) {
                                Text(
                                    text = "暂无自定义或内置识别规则，前往「识别规则」添加后即可在此勾选。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    allRules.forEach { rule ->
                                        val isChecked = rule.id in selectedPromptRuleIds
                                        val ruleLabel = buildString {
                                            append(rule.name.ifBlank { rule.channelName.ifBlank { "规则" } })
                                            if (rule.isIgnoreRule) append("(忽略)")
                                            else if (rule.defaultCategory.isNotBlank() && rule.defaultCategory != "自动") {
                                                append("·${rule.defaultCategory}")
                                            }
                                        }
                                        FilterChip(
                                            selected = isChecked,
                                            onClick = {
                                                selectedPromptRuleIds = if (isChecked) {
                                                    selectedPromptRuleIds - rule.id
                                                } else {
                                                    selectedPromptRuleIds + rule.id
                                                }
                                                viewModel.saveAiConfig(
                                                    currentEditedConfig().copy(selectedPromptRuleIds = selectedPromptRuleIds)
                                                )
                                            },
                                            label = { Text(ruleLabel) },
                                            modifier = Modifier.testTag("ai_prompt_rule_chip_${rule.id}")
                                        )
                                    }
                                }
                            }

                            if (showPromptPreview) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("ai_effective_prompt_preview")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "最终发送给模型的完整系统提示词预览",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = effectivePromptPreview,
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

            // 4. Live AI Parsing Test Card
            if (selectedMode != AiProviderMode.DISABLED) {
                item(key = "ai_test_card") {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("AI理解测试", style = MaterialTheme.typography.titleMedium)

                            AssistChip(
                                onClick = {
                                    testSampleInput = "【招商银行】您尾号8812储蓄卡于11月19日在星巴克（静安嘉里店）快捷支付人民币42.50元。"
                                },
                                label = { Text("填入示例通知文本测试") }
                            )

                            OutlinedTextField(
                                value = testSampleInput,
                                onValueChange = { testSampleInput = it },
                                label = { Text("待理解测试文本") },
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
                                    val cfg = currentEditedConfig()
                                    if (testSampleInput.isBlank()) {
                                        onShowMessage("请先输入或填入测试通知文本")
                                    } else {
                                        viewModel.testAiUnderstanding(
                                            context = context,
                                            config = cfg,
                                            sampleTitle = "AI理解测试",
                                            sampleContent = testSampleInput
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
                                    Text("测试 AI理解")
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
        }
    }
}
