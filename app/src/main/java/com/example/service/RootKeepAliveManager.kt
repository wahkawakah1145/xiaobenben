package com.example.service

import android.content.ComponentName
import android.content.Context
import android.os.Process
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

data class RootKeepAliveStatus(
    val isRootAvailable: Boolean = false,
    val checked: Boolean = false,
    val lastResultMessage: String = ""
)

object RootKeepAliveManager {

    suspend fun checkRootAvailable(): RootKeepAliveStatus = withContext(Dispatchers.IO) {
        try {
            val result = executeSuCommand("id")
            val hasRoot = result.first && result.second.contains("uid=0")
            RootKeepAliveStatus(
                isRootAvailable = hasRoot,
                checked = true,
                lastResultMessage = if (hasRoot) {
                    "已检测到 Root 权限 (uid=0)"
                } else {
                    "未检测到 Root (su) 权限"
                }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            RootKeepAliveStatus(
                isRootAvailable = false,
                checked = true,
                lastResultMessage = "未检测到 Root (su) 权限"
            )
        }
    }

    suspend fun applyRootKeepAlive(context: Context): RootKeepAliveStatus = withContext(Dispatchers.IO) {
        try {
            val check = executeSuCommand("id")
            if (!check.first || !check.second.contains("uid=0")) {
                return@withContext RootKeepAliveStatus(
                    isRootAvailable = false,
                    checked = true,
                    lastResultMessage = "Root 保活执行失败：当前设备未授予 su 权限"
                )
            }

            val pkg = context.packageName
            val pid = Process.myPid()
            val listenerComp = "$pkg/${AutoBookkeepingNotificationService::class.java.name}"

            val script = listOf(
                "dumpsys deviceidle whitelist +$pkg",
                "cmd appops set $pkg RUN_IN_BACKGROUND allow",
                "cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow",
                "am set-standby-bucket $pkg active",
                "cmd notification allow_listener $listenerComp",
                "echo -800 > /proc/$pid/oom_score_adj 2>/dev/null || true"
            ).joinToString("; ")

            val execResult = executeSuCommand(script)
            runCatching {
                NotificationListenerService.requestRebind(
                    ComponentName(context, AutoBookkeepingNotificationService::class.java)
                )
            }

            if (execResult.first) {
                RootKeepAliveStatus(
                    isRootAvailable = true,
                    checked = true,
                    lastResultMessage = "已通过 Root 写入 Doze 白名单、后台常驻、监听授权与进程优先级 (PID $pid)"
                )
            } else {
                RootKeepAliveStatus(
                    isRootAvailable = true,
                    checked = true,
                    lastResultMessage = "部分指令受系统限制：${execResult.second.ifBlank { "请检查 SELinux 或 Magisk 授权" }}"
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RootKeepAliveStatus(
                isRootAvailable = false,
                checked = true,
                lastResultMessage = "Root 保活执行异常：${e.localizedMessage ?: "无法调用 su"}"
            )
        }
    }

    private fun executeSuCommand(command: String): Pair<Boolean, String> {
        var process: java.lang.Process? = null
        return try {
            process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(5, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return false to "命令执行超时"
            }
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            (process.exitValue() == 0) to output
        } catch (e: Exception) {
            false to (e.localizedMessage ?: "su 不可用")
        } finally {
            process?.destroy()
        }
    }
}
