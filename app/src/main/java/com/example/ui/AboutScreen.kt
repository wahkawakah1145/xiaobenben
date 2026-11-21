package com.example.ui

import com.example.model.GitHubUpdateMirror
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.data.GitHubReleaseUpdateManager
import com.example.data.ReleaseUpdateInfo
import com.example.model.AppIconStyle
import com.example.ui.components.AppBrandIconAvatar
import com.example.ui.theme.AppIcons
import com.example.ui.theme.ExpenseCoral
import com.example.ui.theme.IncomeJade
import kotlinx.coroutines.launch

private const val DEFAULT_GITHUB_REPO = "wahkawakah1145/xiaobenben"
private const val AI_STUDIO_TOOL_URL = "https://aistudio.google.com/"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutScreen(
    onShowMessage: (String) -> Unit,
    appIconStyleId: String = AppIconStyle.EMERALD.id,
    selectedMirror: GitHubUpdateMirror = GitHubUpdateMirror.DIRECT,
    onSelectMirror: (GitHubUpdateMirror) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repoPath = remember {
        BuildConfig.GITHUB_REPO.trim().takeIf { it.contains("/") } ?: DEFAULT_GITHUB_REPO
    }
    val githubRepoUrl = remember(repoPath) { "https://github.com/$repoPath" }

    var showLicenseDialog by remember { mutableStateOf(false) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateStatusText by remember { mutableStateOf<String?>(null) }
    var updateStatusIsError by remember { mutableStateOf(false) }
    var updateStatusHasNew by remember { mutableStateOf(false) }
    var latestReleaseDialog by remember { mutableStateOf<ReleaseUpdateInfo?>(null) }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero App Card with App Icon & Version
            item(key = "about_hero") {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                                    )
                                )
                            )
                            .padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AppBrandIconAvatar(
                                styleId = appIconStyleId,
                                size = 84.dp,
                                cornerDp = 22.dp,
                                modifier = Modifier.testTag("about_app_icon")
                            )

                            Text(
                                text = stringResource(id = R.string.app_name),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )

                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = "版本 v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                        .testTag("about_version_text")
                                )
                            }

                            Text(
                                text = "包名：${BuildConfig.APPLICATION_ID}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Manual Update Checker Card (GitHub Releases Latest, manual only)
            item(key = "about_update_card") {
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
                            Icon(
                                imageVector = AppIcons.SystemUpdate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text("版本更新", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "监听 GitHub Release 最新版本 · 仅支持手动检查与手动更新",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "更新检查与下载源（可选国内免费 GitHub 镜像源）",
                                style = MaterialTheme.typography.labelLarge
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                GitHubUpdateMirror.entries.forEach { mirror ->
                                    FilterChip(
                                        selected = selectedMirror == mirror,
                                        onClick = { onSelectMirror(mirror) },
                                        label = { Text(mirror.label) },
                                        modifier = Modifier.testTag("about_mirror_${mirror.name}")
                                    )
                                }
                            }
                            Text(
                                text = selectedMirror.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            enabled = !checkingUpdate,
                            onClick = {
                                checkingUpdate = true
                                updateStatusText = null
                                updateStatusIsError = false
                                updateStatusHasNew = false
                                scope.launch {
                                    val result = GitHubReleaseUpdateManager.checkLatestRelease(
                                        repoPath = repoPath,
                                        currentVersionName = BuildConfig.VERSION_NAME,
                                        mirror = selectedMirror
                                    )
                                    checkingUpdate = false
                                    result
                                        .onSuccess { info ->
                                            if (info.hasNewerVersion) {
                                                updateStatusHasNew = true
                                                updateStatusText = "发现新版本 ${info.tagName}（当前 v${BuildConfig.VERSION_NAME}）"
                                                latestReleaseDialog = info
                                            } else {
                                                updateStatusHasNew = false
                                                val msg = "当前已是最新版本（线上最新：${info.tagName}）"
                                                updateStatusText = msg
                                                onShowMessage(msg)
                                            }
                                        }
                                        .onFailure { err ->
                                            updateStatusIsError = true
                                            val msg = err.message ?: "检查更新失败，请稍后重试"
                                            updateStatusText = msg
                                            onShowMessage(msg)
                                        }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("about_check_update_button")
                        ) {
                            if (checkingUpdate) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("正在检查 GitHub Release...")
                            } else {
                                Icon(AppIcons.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("检查更新")
                            }
                        }

                        updateStatusText?.let { status ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = when {
                                    updateStatusIsError -> ExpenseCoral.copy(alpha = 0.12f)
                                    updateStatusHasNew -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                    else -> IncomeJade.copy(alpha = 0.12f)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = status,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when {
                                        updateStatusIsError -> ExpenseCoral
                                        updateStatusHasNew -> MaterialTheme.colorScheme.onPrimaryContainer
                                        else -> IncomeJade
                                    },
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Author & Links Card (Clickable boxes opening browser)
            item(key = "about_author_box") {
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
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text("作者与相关链接", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "开发者：wahkawakah1145 · 点击下方卡片框可直接通过浏览器跳转",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        ClickableLinkBox(
                            icon = AppIcons.Code,
                            title = "GitHub 仓库地址",
                            url = githubRepoUrl,
                            subtitle = "查看源码、提交 Issue 与 ARMv8 Release 构建发布",
                            testTag = "about_github_box",
                            onClick = { openUrlInBrowser(context, githubRepoUrl, onShowMessage) }
                        )

                        ClickableLinkBox(
                            icon = AppIcons.AutoAwesome,
                            title = "本工具（Google AI Studio）",
                            url = AI_STUDIO_TOOL_URL,
                            subtitle = "用于构建与迭代本 Android 应用的开发工作台",
                            testTag = "about_aistudio_box",
                            onClick = { openUrlInBrowser(context, AI_STUDIO_TOOL_URL, onShowMessage) }
                        )
                    }
                }
            }

            // Open Source License as subtle bottom fine print
            item(key = "about_license_footer") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "基于 MIT License 开源 · 点击查看开源协议",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showLicenseDialog = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("about_license_footer_text")
                    )
                }
            }
        }
    }

    latestReleaseDialog?.let { info ->
        AlertDialog(
            onDismissRequest = { latestReleaseDialog = null },
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("发现新版本 ${info.tagName}")
                    Text(
                        text = "当前版本 v${BuildConfig.VERSION_NAME} · 仅支持手动前往下载更新",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (info.apkAssetName != null) {
                        val sizeText = if (info.apkSizeBytes > 0L) {
                            " (%.2f MB)".format(info.apkSizeBytes / (1024.0 * 1024.0))
                        } else ""
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "通道：${info.usedMirrorLabel}\n安装包：${info.apkAssetName}$sizeText",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                    }
                    HorizontalDivider()
                    Text(
                        text = info.body.ifBlank { "该 Release 未提供详细更新说明，可前往 GitHub Release 页面查看。" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { latestReleaseDialog = null }) {
                        Text("暂不更新")
                    }
                    if (!info.apkDownloadUrl.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = {
                                val url = info.apkDownloadUrl
                                latestReleaseDialog = null
                                openUrlInBrowser(context, url, onShowMessage)
                            },
                            modifier = Modifier.testTag("about_update_download_apk_button")
                        ) {
                            Text("下载 APK")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val url = info.htmlUrl
                        latestReleaseDialog = null
                        openUrlInBrowser(context, url, onShowMessage)
                    },
                    modifier = Modifier.testTag("about_update_open_release_button")
                ) {
                    Text("查看 Release")
                }
            }
        )
    }

    if (showLicenseDialog) {
        AlertDialog(
            onDismissRequest = { showLicenseDialog = false },
            title = { Text("MIT License") },
            text = {
                Text(
                    text = "Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files, to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software.\n\nTHE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND.",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                TextButton(onClick = { showLicenseDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }
}

@Composable
private fun ClickableLinkBox(
    icon: ImageVector,
    title: String,
    url: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = AppIcons.OpenInNew,
                contentDescription = "在浏览器中打开",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun openUrlInBrowser(
    context: Context,
    url: String,
    onShowMessage: (String) -> Unit
) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(intent)
    }.onFailure {
        onShowMessage("未能打开浏览器，请复制链接访问：$url")
    }
}
