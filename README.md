# 小本本 (XiaoBenBen)

一款基于 Kotlin 与 Jetpack Compose (Material Design 3) 构建的 Android 智能通知记账与账单管理应用。通过监听系统支付与银行动账通知实现自动解析入账，并集成「账单工具」多级菜单（记账预算、币种汇率、短时智能去重、数据空间与存储优化）、AI理解（本地开源小模型按需下载 / 在线 OpenAI 兼容 API）、交易发生时间识别与编辑、全维度（年/月/周/日）收支分析、主页横滑与多级菜单手势返回、XLSX/CSV 智能表头导入、批量编辑、本地备份与 WebDAV 同步等功能。

- **包名**：`com.live150yo.lvshuiqingshan`
- **开源协议**：MIT License
- **系统支持**：Android 11 (API 30) 及以上 · `targetSdk 37`（Android 17）
- **构建技术栈**：Kotlin · Jetpack Compose (M3) · Room · DataStore · Navigation Compose · OkHttp · Coroutines/Flow

---

## 核心功能

### 1. 智能通知识别、时间提取与 AI理解
- **全渠道通知自动识别**：监听微信支付、支付宝、云闪付及国内主流银行短信/App 通知，自动识别金额、币种、交易类型（支出 / 收入 / 退款 / 转账）、商户、渠道、卡尾号、分类与 **交易发生时间**（避免通知延迟导致时间偏差），并自动过滤验证码等非账单消息。
- **更聪明的规则自学习**：
  - 点击「非账单」自动生成并启用 **IGNORE 忽略规则**，此后同模板推广/提醒通知直接自动过滤，不再反复弹出询问。
  - 在弹窗中编辑任何带原始通知的账单后，自动按「通知模版签名」写入/覆盖专属模版规则，此后同格式通知均以**最后一次编辑结果**为准进行分类、渠道与商户匹配。
- **AI理解（「设置 -> AI理解」）**：
  - **3 档内置推荐开源小模型（由小到大）+ 浏览器权重页 + 自定义权重导入**：支持从 HF-Mirror / HuggingFace 按需下载按体积从小到大排列的 3 档轻量开源模型（`ERNIE-3.0-Nano-Zh` ~130 KB -> `BGE-Small-Zh v1.5` ~560 KB -> `Qwen2.5-0.5B-Instruct` ~7.1 MB），提供一键**打开浏览器下载模型权重网站**按钮，也支持导入自定义 `.gguf` / `.onnx` / `.tflite` / `.safetensors` / `.bin` / `.json` / `.txt` 静态权重或词表文件（严格拦截可执行二进制）。
  - **在线 OpenAI 兼容 API**：支持填入 DeepSeek、硅基流动、通义千问、OpenAI 或自建网关接口地址、API Key 与模型名称。
- **权限管理**：在「设置 -> 权限管理」中集中管理通知使用权、无障碍保活守护、电池优化白名单与可选的 **Root (`su`) 进程保活**。

### 2. 账单主页、快速录入与撤销提示
- **多功能 `+` 展开按钮 & 无打扰首页**：移除已运行时的冗余常驻提示磁贴，点击首页 `+` 按钮平滑展开「记一笔」与「识别规则」快捷入口；支持在外观设置中配置位置为左下角或右下角，**双击底部/侧边 Tab 栏**可平滑回到当前页顶部。
- **左对齐自定义顶部品牌栏**：主页顶部支持自定义是否显示应用图标、自定义软件内显示名称与副标题、图标大小/圆角/间距与标题字号粗细，提供简约 SVG 绘图图标并同步桌面图标与莫奈/自定义取色。
- **原始记录可选择复制 & 时间精准编辑**：编辑账单弹窗顶部常驻「原始通知记录」区块，支持长按自由划选及一键复制；所有新增/编辑弹窗均可调整具体发生日期与时间。
- **删除撤销底部横栏**：删除（或批量删除）账单后底部弹出「已删除...」浮动横栏，留足显示时长支持一键 **撤销恢复**，左右滑动横栏即可快捷关闭。
- **长按多选 & 金融黑色幽默彩蛋**：长按账单或筛选结果一键全选并批量编辑；首页顶部横幅默认 Tag「我告诉你」，连续点击 7 次弹出大字无标题彩蛋弹窗，提供「残忍退出」与「开心收下」按钮。

### 3. 全粒度收支分析、双指缩放饼图与全屏缩放日历
- **年度 / 月 / 周 / 日 四档粒度**：支持自由切换粒度与前翻后翻，全量无精简展示金额、分类及渠道名称，并优化底部维度分析卡片呈现。
- **双指捏合可调大饼图**：分类占比居中展示大尺寸圆环饼图，支持**双指捏合手势**无级缩放饼图大小（`140dp ~ 300dp`）以隐藏冗余滑块。
- **下拉全屏四维缩放日历**：在收支分析顶部继续下拉（触发距离默认 `128 dp` 防误触，支持 `72 dp ~ 220 dp` 自定义调节）丝滑全屏弹出交互式 **年 / 月 / 周 / 日** 收支日历（保留底部 Tab 栏），上滑或点击 Tab 栏平滑退出，支持**双指捏合缩放手势**与按钮在年/月/周/日之间平滑切换。

### 4. 外观调色板、DPI 确认生效与丝滑返场
- **自定义与动态 Material You 调色板**：无论动态取色或手动调色均支持选择 5 种调色板衍生风格（`Tonal Spot` / `Expressive` / `Neutral` / `Vibrant` / `Monochrome`）；手动调色配备圆形 HSV 色环、HSV 与 RGB 滑块/输入框及顶部常驻 `HEX` 输入框。
- **全局页面 DPI 自定义**：提供 `0.80x ~ 1.35x` 连续滑块（含 `0.85 紧凑 / 1.00 标准 / 1.15 舒适 / 1.30 大屏` 4 档磁吸定位）与精确倍率输入框，调整后需点击 **「应用」** 按钮确认生效。
- **多级菜单预测性返回与横滑返回**：「账单工具」多级菜单集成记账与预算、币种汇率（含自动更新频率）、**默认 1 分钟短时窗口**智能去重与数据空间存储优化；「备份与恢复」统一承接 XLSX/CSV 导入、本地备份与 WebDAV 同步；所有多级子菜单支持稳定无残影的系统预测性返回与右滑手势返回上一级。

### 5. 轻量化安装包与关于
- 极简自绘矢量 SVG 图标引擎替代冗余栅格图片与扩展图标库，配合 Release R8 混淆与资源裁剪严格控制安装包体积；「设置 -> 关于」展示当前同步风格应用图标、版本号、上游说明、MIT 协议及可跳转浏览器的 GitHub 仓库与 AI Studio 链接。

---

## 目录结构

```text
app/src/main/java/com/example/
├── data/
│   ├── LedgerDatabase.kt          # Room 数据库 (v5: 模版签名索引、VACUUM/PRUNE 清理)
│   ├── LedgerRepository.kt        # 仓库层、模板学习/忽略规则、撤销恢复、存储优化
│   ├── SettingsDataStore.kt       # 调色板、DPI 缩放、Pull 日历阈值、品牌顶栏与 AI 配置持久化
│   ├── AiUnderstandingManager.kt  # 3 档推荐开源小模型下载、网页跳转、自定义权重与 OpenAI API
│   ├── ManualImportManager.kt     # XLSX / CSV / TSV / JSON / 文本自动表头与时间解析导入
│   ├── XlsxTableReader.kt         # 纯 Kotlin 零依赖 XLSX 解析器 (Zip + DOM XML)
│   ├── CurrencyExchangeManager.kt # 多币种实时汇率更新与本地缓存
│   ├── LedgerBackupCodec.kt       # 紧凑 JSON 备份编解码
│   └── WebDavSyncClient.kt        # WebDAV 同步客户端
├── model/                         # 领域模型、AppIconStyle、CustomPaletteStyle、IconVisualStyle
├── parser/
│   ├── ChinaNotificationParser.kt # 全渠道通知解析、模板签名生成、IGNORE 短路与时间提取
│   ├── SmartDeduplicator.kt       # 默认 1 分钟短时同金额与相似商户智能聚类去重
│   ├── LocalSpendingAnalyzer.kt   # 年度/月/周/日多粒度收支洞察引擎
│   └── FinanceHumorTips.kt        # 金融黑色幽默语录库
├── service/                       # 通知监听、无障碍保活守护、Root 保活与备份通知调度
├── ui/
│   ├── LedgerHomeScreen.kt        # 账单主页（多功能展开 + FAB、批量操作、彩蛋、待询问）
│   ├── AnalyticsScreen.kt         # 收支分析（双指缩放饼图、可调下拉全屏缩放日历）
│   ├── SettingsScreen.kt          # 设置主页（备份与恢复 / 权限管理等二级菜单入口）
│   ├── LedgerManagementScreen.kt  # 账单工具多级菜单（记账预算 / 存储优化与子路由入口）
│   ├── AiSettingsScreen.kt        # AI理解（3 档模型按需下载 / 浏览器权重下载 / 自导入 / 在线 API）
│   ├── PermissionsKeepAliveScreen.kt # 权限管理二级菜单
│   ├── AboutScreen.kt             # 关于二级菜单（图标、版本、MIT 协议、作者仓库与 AI Studio 链接）
│   ├── DataHubScreen.kt             # 备份与恢复（手动导入 / 本地备份 / WebDAV）
│   ├── SmartDeduplicationScreen.kt# 短时智能去重页面（默认 1 分钟 · 自定义时间窗口）
│   ├── SettingsSubScreens.kt      # 通知识别与规则、币种汇率、外观调色/DPI 确认/下拉阈值设置
│   ├── MainAppScaffold.kt         # 品牌顶栏、双击 Tab 回顶、无残影预测性/横滑返回容器
│   ├── theme/                     # 动态与自定义 Material You 配色引擎、DPI 密度覆盖、轻量矢量图标
│   └── components/                # CustomColorPickerDialog、AnalyticsCalendarSection、弹窗与 SVG 头像
└── viewmodel/
    └── LedgerViewModel.kt         # 响应式状态管理（含撤销删除、自定义权重与日历/规则联动）
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
