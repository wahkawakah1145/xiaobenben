# 小本本 (XiaoBenBen)

一款基于 Kotlin 与 Jetpack Compose (Material Design 3) 构建的 Android 智能通知记账与账单管理应用。通过监听系统支付与银行动账通知实现自动解析入账，并集成「账单管理」中心、短时智能去重、汇率自动更新频率、AI 理解接口（本地开源小模型按需下载 / 在线 OpenAI 兼容 API）、XLSX/CSV 智能表头导入、批量编辑、本地备份与 WebDAV 同步等功能。

- **包名**：`com.live150yo.lvshuiqingshan`
- **开源协议**：MIT License
- **系统支持**：Android 11 (API 30) 及以上 · `targetSdk 37`（Android 17）
- **构建技术栈**：Kotlin · Jetpack Compose (M3) · Room · DataStore · Navigation Compose · OkHttp · Coroutines/Flow

---

## 核心功能

### 1. 智能通知识别、规则与 AI 理解接口
- **全渠道通知自动识别**：监听微信支付、支付宝、云闪付及国内主流银行短信/App 通知，自动识别金额、币种、交易类型（支出 / 收入 / 退款 / 转账）、商户、渠道、卡尾号与分类，并自动过滤验证码等非账单消息。
- **AI 理解接口（「设置 -> AI 理解接口」）**：
  - **本地开源小模型（按需下载）**：支持从 HF-Mirror / HuggingFace 按需下载开源中文轻量模型（`BGE-Small-Zh v1.5`、`ERNIE-3.0-Nano-Zh`、`Qwen2.5-0.5B-Instruct`）词表与配置，纯离线推理提取商户、分类与金额，不加载外部可执行二进制，安全合规。
  - **在线 OpenAI 兼容 API**：支持用户自定义填入 DeepSeek、硅基流动、通义千问、OpenAI 或自建 Ollama / vLLM 网关地址、API Key 与模型名称，可在界面及通知测试台中直接测试解析。
- **待识别询问与规则学习**：无法准确判定时自动收入「待识别询问」卡片，点选通知内提取的关键词标签即可快速生成/更新自定义识别规则，同时支持学习数字千分位/小数点格式（例如 `2111.10`、`2,111.10` 与欧式 `2.111,10`）。
- **双通道保活机制**：在「设置 -> 系统权限与进程保活」中集中管理通知使用权、无障碍保活守护、电池优化白名单与可选的 **Root (`su`) 进程保活**。

### 2. 账单主页与长按多选批量编辑
- **长按多选 & 筛选结果批量操作**：在账单主页及任意搜索/筛选条件下，长按任意账单卡片即可进入多选模式（也可在筛选后一键「多选全部结果」）。
- **批量编辑与删除**：支持同时批量修改所选账单的 **收支类型、分类、支付渠道、商户名称、币种（自动刷新汇率快照）与备注**，或批量删除；按返回键或点击关闭按钮即可退出多选。
- **金融黑色幽默 Tips 与彩蛋**：首页顶部横幅默认展示 Tag「我告诉你」（外观设置输入框默认留空，可按需自定义覆盖）与黑色幽默理财文案；连续点击横幅 7 次可触发弹窗彩蛋（需点击指定按钮关闭）。

### 3. 账单管理中心（「设置 -> 账单管理」）
将常用账务控制整合进「账单管理」二级菜单的三大标签页：
- **记账与预算**：配置自动记账开关、进软件后手动确认模式、语义增强、入账提醒与每月支出预算。
- **币种与汇率**：设置统计主币种，查看外币即时折算与历史汇率快照；支持配置 **汇率数据库自动更新频率**（仅手动更新 / 每 6 小时 / 每天 / 每 3 天 / 每周），并在应用回到前台或切换配置后按周期自动刷新。
- **短时智能去重**：仅识别短时间窗口内「相同金额 + 同币种 + 同收支方向 + 交易对象相似」的重复入账，自动排除长期周期消费；内置 5 分钟 ~ 24 小时常用预设，并支持自定义分钟/小时窗口（最高 7 天）、跨渠道/同渠道与关键词筛选。

### 4. 收支分析
- **多图表交互统计**：收入、支出与结余汇总展示；支出分类占比与支付渠道分布卡片右上角均支持自由切换 **饼图 / 柱状图**。
- **图例/列表下钻筛选**：点击任意图例或排行条目，即可弹窗查看该分类/渠道下的对应账单明细，并支持直接进入详情或编辑。

### 5. 导入与备份中心（「设置 -> 导入与备份中心」）
- **手动导入（支持 XLSX / CSV / TSV / JSON / 自由文本）**：自动跳过前置说明定位真实表头，支持分离的「支出金额 / 收入金额」列、自动排重「账户余额」列、UTF-8 / GB18030 自动识别与 Excel 序列日期解析。
- **本地备份与通知提醒**：一键导出/恢复 JSON 备份；支持自定义定期备份提醒间隔（小时 / 天），到期后通过高重要性通知通道提醒，点击直达本地备份页。
- **WebDAV 云端同步**：支持配置 WebDAV 服务器地址、账号密码，手动上传快照或从云端拉取合并。

### 6. 设置结构、外观与关于
- **纯二级菜单设置页**：设置首页全量收拢为二级菜单入口（账单管理、通知识别与规则、AI 理解接口、导入与备份中心、外观与布局、系统权限与进程保活、关于），页面清爽直观。
- **外观与布局**：支持 Material You 莫奈动态取色、桌面主题单色图标、深浅色模式、英文与数字字体切换、卡片密度与元素位置调整。
- **关于（「设置 -> 关于」）**：展示应用图标、版本号与 Build 号、上游项目说明、MIT 开源协议详情，以及可点击跳转浏览器的 GitHub 仓库与开发工具（Google AI Studio）卡片框。

---

## 目录结构

```text
app/src/main/java/com/example/
├── data/
│   ├── LedgerDatabase.kt          # Room 数据库 (transactions / parsing_rules / notification_inquiries)
│   ├── LedgerRepository.kt        # 仓库层与批量更新、导入去重事务
│   ├── SettingsDataStore.kt       # 外观、预算、汇率自动更新频率与 AI 配置持久化
│   ├── AiUnderstandingManager.kt  # 本地开源小模型按需下载/词表推理 + OpenAI 兼容 API 调用
│   ├── ManualImportManager.kt     # XLSX / CSV / TSV / JSON / 文本自动表头解析导入
│   ├── XlsxTableReader.kt         # 纯 Kotlin 零依赖 XLSX 解析器 (Zip + DOM XML)
│   ├── CurrencyExchangeManager.kt # 多币种实时汇率更新与本地缓存
│   ├── LedgerBackupCodec.kt       # 本地 JSON 备份编解码
│   └── WebDavSyncClient.kt        # WebDAV 同步客户端
├── model/                         # 领域模型、设置、AI 配置与目录常量
├── parser/
│   ├── ChinaNotificationParser.kt # 全渠道通知解析、关键词候选提取与数字格式学习
│   ├── SmartDeduplicator.kt       # 短时同金额与相似商户智能聚类去重
│   ├── LocalSpendingAnalyzer.kt   # 本地统计与分析文案引擎
│   └── FinanceHumorTips.kt        # 金融黑色幽默语录库
├── service/                       # 通知监听、无障碍保活守护、Root 保活与备份通知调度
├── ui/
│   ├── LedgerHomeScreen.kt        # 账单主页（含长按多选、搜索结果批量操作、待询问/待确认）
│   ├── AnalyticsScreen.kt         # 收支分析（饼图/柱状图切换、下钻筛选）
│   ├── SettingsScreen.kt          # 设置主页（纯二级菜单入口）
│   ├── LedgerManagementScreen.kt  # 账单管理二级菜单（记账与预算 / 币种与汇率 / 智能去重）
│   ├── AiSettingsScreen.kt        # AI 理解接口二级菜单（本地小模型下载 / 在线 API 配置）
│   ├── PermissionsKeepAliveScreen.kt # 系统权限与进程保活二级菜单
│   ├── AboutScreen.kt             # 关于二级菜单（图标、版本、MIT 协议、作者仓库与 AI Studio 链接）
│   ├── DataHubScreen.kt           # 导入与备份中心（手动导入 / 本地备份 / WebDAV）
│   ├── SmartDeduplicationScreen.kt# 短时智能去重页面（含自定义时间窗口）
│   ├── SettingsSubScreens.kt      # 通知识别与规则、币种汇率、外观布局及子模块
│   ├── MainAppScaffold.kt         # 顶层自适应 NavigationBar / NavigationRail 架构
│   └── components/                # 账单详情、单条编辑与 BatchEditDialog 批量编辑弹窗
└── viewmodel/
    └── LedgerViewModel.kt         # 响应式状态管理
```

---

## 本地构建与测试

确保环境已安装 JDK 21 与 Android SDK（通过 `ANDROID_SDK_ROOT` 配置）：

```bash
# 运行全部单元测试与 Robolectric 测试
gradle :app:testDebugUnitTest

# 编译 Debug APK
gradle :app:assembleDebug

# 仅编译 ARMv8 (arm64-v8a) Release APK
gradle :app:assembleRelease -Parmv8Only
```

---

## GitHub Actions 自动打包签名说明

仓库配置了 `.github/workflows/release-armv8-apk.yml`，在推送 `v*` 标签或手动触发 `workflow_dispatch` 时自动执行单元测试、构建 `arm64-v8a` Release APK 并结合 `CHANGELOG.md` 生成带有更新日志的 GitHub Release。

签名证书加载优先级：
1. 若仓库配置了 `KEYSTORE_BASE64`、`STORE_PASSWORD` 与 `KEY_PASSWORD` Secrets，则优先使用自定义正式签名证书。
2. 否则自动使用仓库固定签名证书（`.github/ci-release-cert.txt` / `debug.keystore`），确保后续版本与上一版本可直接覆盖升级安装而不发生签名冲突。
