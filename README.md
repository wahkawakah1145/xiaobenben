小本本 (XiaoBenBen)

«一个由 AI 辅助开发的 Android 记账应用。记录收入、支出，以及人类是如何把钱花没的。»

<p align="center">
  <b>AI-assisted · Android · Personal Finance</b>
</p>项目简介

小本本 是一个 Android 记账应用，旨在提供日常收支记录、账单分析、智能识别与本地数据备份等功能。

本项目最初使用 Google Gemini 进行辅助开发，作者在一天的模型使用额度内完成了主要开发工作。

是的，某种意义上属于 Vibe Coding 的产物。

«Built with Google Gemini, fueled by one day's worth of quota, and questionable engineering decisions.

AI slop? Perhaps. But it might actually be useful.»

项目仍在持续完善中，欢迎体验、反馈问题以及提交改进建议。

功能特性

1. 收支管理

- 日常收入与支出记录
- 账单详情查看与编辑
- 支持不同收支分类
- 支持外币账单记录
- 保存记账时的汇率快照与折算结果

2. 智能记账与规则学习

- 基于通知内容识别账单信息
- 支持自定义通知识别规则
- 自动提取关键词作为规则候选标签
- 支持手动编辑识别规则
- 学习常见数字格式，包括千分位与小数格式
- 支持测试识别规则

3. 数据统计与分析

- 支出分类占比分析
- 收支渠道分布分析
- 支持饼图与柱状图切换
- 支持从统计结果查看对应账单
- 支持查看账单详情与编辑

4. 数据备份与恢复

- 导出 JSON 格式账单备份
- 从本地 JSON 文件恢复数据
- 支持合并恢复
- 支持定期备份提醒
- 可自定义备份提醒间隔
- 使用高优先级通知渠道推送备份提醒

5. 个性化体验

- Material You 动态取色图标
- 首页 Tips 文案自定义
- 黑色幽默风格的金融 Tips
- 隐藏常规保存弹窗
- 其他界面与布局设置

6. 系统兼容性

- 最低支持 Android 11（API 30）
- Target SDK：Android 17（API 37）
- 支持无障碍及 Root 进程保活相关功能

截图

暂未整理应用截图。

欢迎后续补充真实运行截图。

安装

方式一：下载 APK

前往项目的 "Releases" (https://github.com/wahkawakah1145/xiaobenben/releases) 页面获取安装包。

目前如果没有发布 Release，可以通过源码自行构建。

方式二：自行编译

环境要求：

- Android Studio
- JDK（以项目 Gradle 配置要求为准）
- Android SDK
- Git

克隆项目：

git clone https://github.com/wahkawakah1145/xiaobenben.git
cd xiaobenben

使用 Gradle 构建 Debug APK：

./gradlew assembleDebug

Windows：

gradlew.bat assembleDebug

构建完成后，APK 通常位于：

app/build/outputs/apk/debug/app-debug.apk

也可以使用 Android Studio 打开项目，等待 Gradle Sync 完成后，通过 Build > Build Bundle(s) / APK(s) > Build APK(s) 进行构建。

项目信息

项目| 信息
名称| 小本本
平台| Android
最低系统版本| Android 11
Minimum SDK| 30
Target SDK| 37
Application ID| "com.live150yo.lvshuiqingshan"
构建系统| Gradle
语言与框架| 以项目源码为准
开发方式| AI-assisted Vibe Coding

数据与隐私

本项目提供本地 JSON 数据备份与恢复功能。

- 建议定期导出账单备份。
- 不要将包含个人账单信息的备份文件上传至公开仓库。
- 通知读取、无障碍及 Root 相关功能涉及系统权限，请在使用前了解其作用。
- 外币账单的历史汇率快照与当前汇率换算结果并不相同，请注意区分。

具体数据存储、联网行为及第三方服务情况，请以实际源码实现为准。

更新日志

详见 "CHANGELOG.md" (CHANGELOG.md)。

免责声明

本项目主要用于个人学习与日常记账。

- 软件由 AI 辅助生成，代码质量和功能稳定性不作商业级保证。
- 可能存在未发现的 Bug、边界问题或不合理的实现。
- 请定期备份重要账单数据。
- 请勿将尚未验证的数据识别结果直接视为准确的财务记录。

贡献与反馈

欢迎通过 GitHub Issues 提交 Bug、功能建议或使用反馈。

- "提交 Issue" (https://github.com/wahkawakah1145/xiaobenben/issues)
- "查看源码" (https://github.com/wahkawakah1145/xiaobenben)
- "更新日志" (https://github.com/wahkawakah1145/xiaobenben/blob/main/CHANGELOG.md)

License

请以仓库中的 LICENSE 文件为准。

---

<p align="center">
  <sub>Made with Gemini, a questionable amount of confidence, and a desire to know where the money went.</sub>
</p>
