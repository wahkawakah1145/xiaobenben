# 小本本 (XiaoBenBen)

一款基于 Kotlin 与 Jetpack Compose (Material Design 3) 构建的 Android 智能通知记账与多账本管理应用（当前版本 `v1.1.4.520`）。通过监听系统支付与银行动账通知实现秒级自动解析入账，集成多账本管理与分账本分析、标准 11 列表头与银行流水（含本次余额）账单模型、全维度与日期自动补 `-` / 可选时段筛选搜索、36+ 币种中英文/缩写检索与多源/自定义汇率更新、「账单工具」防重批量导入（支持农业银行等银行格式与数字智能转时间戳、仅导新增与全量预览、可选规则匹配 / AI 理解）、组合渠道按 `&` 拆分统计、美化版快速分析弹窗（纯文本复制）、账单与规则删除底栏一键撤回、秒级智能去重、月历每日双行收支展示、可自定义顺序与显隐的底部 Tab 栏、OLED 纯黑深色模式与增强型 Material You 动态调色盘、相册选图自定义桌面快捷方式、本地备份、WebDAV 同步与支持国内免费镜像源的手动 GitHub Release 检查更新等功能。

- **仓库地址**：[https://github.com/wahkawakah1145/xiaobenben](https://github.com/wahkawakah1145/xiaobenben)
- **包名**：`com.live150yo.lvshuiqingshan`
- **开源协议**：MIT License
- **系统支持**：Android 11 (API 30) 及以上 · `targetSdk 37`（Android 17）
- **构建技术栈**：Kotlin · Jetpack Compose (M3) · Room (v8) · DataStore · Navigation Compose · OkHttp · Coroutines/Flow

---

## 核心功能

### 1. 智能通知识别、秒级时间提取与 AI理解
- **全渠道通知自动识别与内部转账不计收支**：监听微信支付、支付宝、云闪付及国内主流银行短信/App 通知，自动识别金额、36 种常见货币、交易类型、商户、渠道、卡尾号、分类与 **精确到秒的交易发生时间**（`HH:mm:ss`）；**微信/支付宝零钱提现、提现到银行卡、零钱通/余额宝互转、本人转账/同名账户互转与信用卡还款自动识别为「转账 / 不计收支」**，避免虚增日常收支。
- **更聪明的规则自学习**：
  - 点击「非账单」自动生成并启用 **IGNORE 忽略规则**，此后同模板推广/提醒通知直接自动过滤，不再反复弹出询问。
  - 在弹窗中编辑任何带原始通知的账单后，自动按「通知模版签名」写入/覆盖专属模版规则，此后同格式通知均以**最后一次编辑结果**为准进行分类、渠道与商户匹配。
- **AI理解（「设置 -> AI理解」）**：
  - **预设系统提示词查看/编辑/重置 + 规则导入提示词**：内置最新版 JSON 结构化解析预设提示词（支持 36+ ISO 货币、秒级时间 `yyyy-MM-dd HH:mm:ss` 及零钱提现/本人转账不计收支规则），支持随时编辑（上限 4000 字）、一键重置为最新预设并自动迁移旧版默认提示词，同时支持多选自定义识别规则注入参考上下文。
  - **3 档内置推荐开源小模型（由小到大）+ 浏览器权重页 + 自定义权重导入**：支持从 HF-Mirror / HuggingFace 按需下载按体积从小到大排列的 3 档轻量开源模型（`ERNIE-3.0-Nano-Zh` ~130 KB -> `BGE-Small-Zh v1.5` ~560 KB -> `Qwen2.5-0.5B-Instruct` ~7.1 MB），提供一键**打开浏览器下载模型权重网站**按钮，也支持导入自定义 `.gguf` / `.onnx` / `.tflite` / `.safetensors` / `.bin` / `.json` / `.txt` 静态权重或词表文件（严格拦截可执行二进制）。
  - **在线 OpenAI 兼容 API**：支持填入 DeepSeek、硅基流动、通义千问、OpenAI 或自建网关接口地址、API Key 与模型名称。
- **权限管理与深度后台内存控制**：在「设置 -> 权限管理」中支持一键快捷打开**本软件的系统权限管理页**（兼容 HyperOS / MIUI 权限页与原生应用详情页），集中管理通知使用权、无障碍保活守护、电池优化白名单与可选 **Root (`su`) 进程保活**；全应用统一共享单一 `AppHttpClient`（零常驻核心线程、15 秒自动回收空闲连接与线程）、限制 SQLite 内存页缓存（`512 KB`）与 `wal_autocheckpoint = 200`，在退入后台（`onStop`）及系统 `onTrimMemory` 时自动执行 `PRAGMA shrink_memory` 与连接池/日志裁剪，并减少主页离屏预加载，在保证后台监听持续活跃的同时显著压低常驻 RAM 占用。

### 2. 标准 11 列 + 银行流水模型、智能筛选与账单主页
- **标准 11 列与银行流水账单结构**：对照微信/支付宝标准 11 列表头及银行导出格式（新增 `本次余额` 字段，Room v8 无损迁移）完善数据模型，支持在账单详情页查看、记账/编辑弹窗中折叠展开编辑，并完整纳入本地及 WebDAV JSON 备份。
- **全维度搜索、日期自动补全 `-` 与可选时段筛选**：首页与智能去重搜索同时匹配交易对方、商品、交易类型、当前状态、交易单号/日志号、商户单号、本次余额、分类、支付方式、卡尾号、备注、原始通知、金额，以及 **36 种币种的英文缩写与中文名/别名**；范围筛选输入日期数字时自动补全年月日之间的 `-`，并支持按 **每日时段**（`全天 / 凌晨 / 上午 / 午间 / 下午 / 晚间` 及自定义 `HH:mm` 区间，含跨午夜时段）与折算金额区间组合过滤；非本年账单自动展示完整年份且全局时间精确到秒（`HH:mm:ss`）。
- **美化版「快速分析」弹窗与账单/规则撤回**：点击首页统计卡片自由切换六大时间维度；筛选结果或多选条目点击「快速分析」弹出可视化统计卡片（KPI 指标、单笔极值、分类/渠道比例条与可展开纯文本预览），一键复制后仍保持纯文本；删除账单或**删除识别规则**后均在底栏弹出可左右滑关的撤销横栏，一键撤回恢复；双击底部/侧边 Tab 栏平滑回到顶部。

### 3. 账单工具（银行/文档防重导入、多源汇率、秒级去重与存储优化）
- **智能防重批量导入（「设置 -> 账单工具 -> 手动从文档或剪贴板导入」）**：完整支持微信/支付宝标准 11 列表头及**农业银行等银行流水导出格式**（如 `交易日期,交易时间,交易摘要,交易金额,本次余额,对手信息,日 志 号,交易渠道,交易附言`），自动按金额正负识别收支并将 `20251121` + `135144` 等数字日期时间智能转换为时间戳；预览阶段自动识别与现有账本或同批次重复的条目，**默认仅导入新增条目**，并支持一键切换「仅看新增」与「列出全部识别」核对明细；支持一键读取剪贴板以及可选「启用规则匹配」与「使用 AI 理解」。
- **默认 5 种常用外币汇率按需扩展 + 多源/自定义链接**：汇率默认启用最常用的 **5 种外币**（`USD / HKD / EUR / JPY / GBP` + 基准 `CNY`），其余 30+ 全球货币支持通过英文缩写或中文别名按需搜索添加、随时移除或一键恢复默认；汇率更新支持切换 **ExchangeRate-API**、**Currency-API (jsDelivr CDN / Cloudflare Pages)**、**Frankfurter (欧洲央行)** 或填写 **自定义 JSON 汇率链接**。
- **精确到秒的智能去重**：短时去重窗口支持精确到秒（新增 `10 秒内`、`30 秒内` 快捷档位以及 `秒 / 分 / 时` 三档自定义单位，默认 `1 分钟内`），长按条目可弹窗核对详情、编辑或删除。
- **多账本管理与数据空间优化**：支持隐藏底部「账本」Tab 后仍在账单工具内完整管理与分账本分析；内置 SQLite `VACUUM` 压缩、缓存清理与历史通知正文精简。

### 4. 全粒度收支分析、组合渠道拆分与月历双行收支视图
- **年度 / 月 / 周 / 日 四档粒度与组合渠道按 `&` 拆分**：全量展示金额、分类及渠道名称；渠道分布自动按 `&`、`+`、`、`、`/` 等符号拆分组合支付方式（如 `花呗&红包&现金抵价券` 分别记入各子渠道统计并支持点击单渠道反查关联账单）；点击图例查看对应明细时自动折叠其余未选图例，圆环饼图支持双指捏合无级缩放（`140dp ~ 300dp`）。
- **长距离可调下拉全屏四维缩放日历（左右横滑切周期）**：在收支分析顶部下拉（默认 `260 dp`，支持 `80 ~ 560 dp` 调节）弹出交互式 **年 / 月 / 周 / 日** 日历（双指捏合切换粒度）；**进入日历后锁定底部 Tab 滑动，左右横滑手势直接切换年份 / 月份 / 周次 / 日期**；月视图顶部含当月汇总条，每日单元格同时展示支出与收入双行金额。

### 5. 外观与布局二级菜单、轻量化体积与国内镜像更新
- **外观与布局 5 大二级菜单**（支持预测性返回与右滑手势）：
  1. **主题与配色**：Android 12+ 动态取色 5 种高辨识度调色盘（`Tonal Spot` / `Expressive` / `Neutral` / `Vibrant` / `Monochrome`）、深色模式 **OLED 纯黑背景 (`#000000`)**、圆形 HSV 色环与常驻 HEX 调色。
  2. **字体、DPI 与列表样式**：英文与数字字体、全局 DPI `0.80x ~ 1.35x`（点击「应用」确认生效）、卡片密度与徽标样式。
  3. **主页、顶栏与导航**：底部 Tab 排序与隐藏「账本」Tab、顶栏品牌自定义、金融 Tips 定制与 `+` 按钮位置。
  4. **动画、手势与提示横栏**：Tab 动画、预测性返回风格、下拉全屏日历距离、全局底部提示横栏停留时间，以及精简后的 **删除后提示横栏「仅手动关闭」单开关**（关闭后自动跟随全局时长）。
  5. **图标风格与桌面快捷方式**：6 套 `activity-alias` 图标（含动态莫奈徽标）与支持**系统相册选图**（居中圆角裁切）的自定义桌面快捷方式。
- **严格控制安装包体积与国内镜像检查更新**：全部新特性（36 种货币、多汇率源、GitHub 镜像、秒级去重与增强导入）零新增第三方依赖，继续保持轻量化 ARMv8 Release 构建；在「设置 -> 关于 -> 版本更新」中支持选择 **GitHub 官方直连** 或 **国内免费镜像源**（`akams.cn` / `gh-proxy.com` / `ghproxy.net`）手动检查 Release 并加速下载 APK。

---

## 目录结构

```text
app/src/main/java/com/example/
├── LedgerApplication.kt           # 应用级低内存回调与空闲缓存裁剪 (不影响后台保活)
├── data/
│   ├── AppHttpClient.kt           # 全应用共享单一 OkHttp 零常驻核心线程池与后台空闲内存回收
│   ├── LedgerDatabase.kt          # Room 数据库 (v8: 11列扩展+本次余额、多账本关联表、WAL自动检查点与VACUUM)
│   ├── LedgerRepository.kt        # 仓库层、扩展字段读写、模板学习/忽略规则、账单与规则撤销恢复、存储优化
│   ├── SettingsDataStore.kt       # 调色板、DPI 缩放、横栏时长、多汇率源/自定义URL、GitHub 镜像持久化
│   ├── GitHubReleaseUpdateManager.kt # 轻量手动 GitHub Release 检查与国内免费镜像源加速
│   ├── AiUnderstandingManager.kt  # AI 提示词与规则注入、3 档开源小模型、流式词表解析与 API 请求
│   ├── ManualImportManager.kt     # 11 列/农行等银行表头解析、数字智能时间戳转换、重复条目识别与仅导新增
│   ├── XlsxTableReader.kt         # 纯 Kotlin 零依赖 XLSX 解析器 (Zip + DOM XML)
│   ├── CurrencyExchangeManager.kt # 36 种货币多数据源 / 自定义链接通用 JSON 汇率解析器
│   ├── LedgerBackupCodec.kt       # 紧凑 JSON 备份编解码（含 11 列账单扩展与本次余额字段）
│   └── WebDavSyncManager.kt       # WebDAV 同步客户端
├── model/                         # 领域模型、36 种 CurrencyCatalog、ExchangeRateSource、GitHubUpdateMirror
├── parser/
│   ├── ChinaNotificationParser.kt # 全渠道通知解析、36 币种识别、秒级时间提取与 IGNORE 短路
│   ├── SmartDeduplicator.kt       # 精确到秒（10s/30s/1m 及秒/分/时自定义）智能聚类去重
│   ├── LocalSpendingAnalyzer.kt   # 年度/月/周/日多粒度收支洞察引擎
│   └── FinanceHumorTips.kt        # 金融黑色幽默语录库
├── service/                       # 通知监听、无障碍保活守护、Root 保活、系统权限页快捷跳转与备份调度
├── util/
│   └── DesktopIconSyncManager.kt  # activity-alias 图标切换、相册选图采样裁切与桌面快捷方式创建
├── ui/
│   ├── LedgerHomeScreen.kt        # 账单主页（全维度/多币种中英文搜索、统计维度、范围筛选、快速分析）
│   ├── LedgerBooksScreen.kt       # 多账本管理、账本明细、快速文本统计与分账本图表分析入口
│   ├── AnalyticsScreen.kt         # 收支分析（自动折叠图例、双指缩放饼图、下拉全屏缩放日历）
│   ├── SettingsScreen.kt          # 设置主页（账单工具 / 备份与恢复 / 外观与布局 / 权限管理 / 关于）
│   ├── LedgerManagementScreen.kt  # 账单工具多级菜单（账本、手动导入、预算、币种汇率、秒级去重、存储优化）
│   ├── AiSettingsScreen.kt        # AI理解（提示词编辑/重置、多选规则参考、3 档小模型与 API）
│   ├── PermissionsKeepAliveScreen.kt # 权限管理二级菜单（含一键跳转本软件系统权限管理页）
│   ├── AboutScreen.kt             # 关于页（支持国内免费 GitHub 镜像源的手动检查更新、仓库链接与协议）
│   ├── DataHubScreen.kt           # 备份与恢复（本地备份 / WebDAV）
│   ├── SmartDeduplicationScreen.kt# 秒级短时智能去重页面（秒/分/时自定义、全字段筛选、长按详情）
│   ├── SettingsSubScreens.kt      # 外观二级菜单、手动导入(规则/AI)、币种检索与多源汇率、WebDAV
│   ├── MainAppScaffold.kt         # 品牌顶栏、单开关删除撤销横栏联动全局时长、双击回顶与预测性返回
│   ├── theme/                     # 动态与自定义 Material You 配色引擎、DPI 密度覆盖、轻量矢量图标
│   └── components/                # 秒级日期时间格式化、11 列账单详情/编辑弹窗、月历双行收支视图
└── viewmodel/
    └── LedgerViewModel.kt         # 响应式状态管理（规则/AI 导入预览、维度过滤、撤销删除与规则联动）
```

---

## 本地构建与测试

确保环境已安装 JDK 21 与 Android SDK（通过 `ANDROID_SDK_ROOT` 配置）：

```bash
# 运行全部单元测试与 Robolectric 测试
gradle :app:testDebugUnitTest

# Termux 内存受限环境构建/测试参考
gradle :app:testDebugUnitTest --no-daemon --no-configuration-cache --max-workers=2

# 编译 Debug APK
gradle :app:assembleDebug

# 仅编译 ARMv8 (arm64-v8a) Release APK
gradle :app:assembleRelease -Parmv8Only
```

---

## GitHub Actions 自动打包签名说明

仓库配置了 `.github/workflows/release-armv8-apk.yml`，在推送 `v*` 标签或手动触发 `workflow_dispatch` 时自动执行单元测试、构建 `arm64-v8a` Release APK（自动注入当前 GitHub 仓库路径用于关于页链接与手动检查更新），并结合 `CHANGELOG.md` 最新章节生成带有更新日志、安装包体积与签名 SHA-256 的 GitHub Release。

签名证书加载优先级：
1. 若仓库配置了 `KEYSTORE_BASE64`、`STORE_PASSWORD` 与 `KEY_PASSWORD` Secrets，则优先使用自定义正式签名证书。
2. 否则自动使用仓库固定签名证书（`.github/ci-release-cert.txt` / `debug.keystore`），确保后续版本与上一版本可直接覆盖升级安装而不发生签名冲突。
