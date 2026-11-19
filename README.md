# 小本本 (XiaoBenBen)

一款基于 Kotlin 与 Jetpack Compose (Material Design 3) 构建的 Android 智能通知记账与账单管理应用。通过监听系统支付与银行动账通知实现自动解析入账，并集成「账单工具」多级菜单（记账预算、币种汇率、短时智能去重、数据空间与存储优化）、AI理解（本地开源小模型按需下载 / 在线 OpenAI 兼容 API）、交易发生时间识别与编辑、全维度（年/月/周/日）收支分析、主页横滑与多级菜单手势返回、XLSX/CSV 智能表头导入、批量编辑、本地备份与 WebDAV 同步等功能。

- **包名**：`com.live150yo.lvshuiqingshan`
- **开源协议**：MIT License
- **系统支持**：Android 11 (API 30) 及以上 · `targetSdk 37`（Android 17）
- **构建技术栈**：Kotlin · Jetpack Compose (M3) · Room · DataStore · Navigation Compose · OkHttp · Coroutines/Flow

---

## 核心功能

### 1. 智能通知识别、时间提取与 AI理解
- **全渠道通知自动识别**：监听微信支付、支付宝、云闪付及国内主流银行短信/App 通知，自动识别金额、币种、交易类型（支出 / 收入 / 退款 / 转账）、商户、渠道、卡尾号、分类与 **交易发生时间**（自动从正文日期/时间或「今天/昨天」推算实际消费时刻，避免通知延迟导致时间偏差），并自动过滤验证码等非账单消息。
- **AI理解（「设置 -> AI理解」）**：
  - **本地开源小模型（按需下载）**：支持从 HF-Mirror / HuggingFace 按需下载开源中文轻量模型（`BGE-Small-Zh v1.5`、`ERNIE-3.0-Nano-Zh`、`Qwen2.5-0.5B-Instruct`）词表与配置，离线推理提取商户、分类、金额与交易发生时间，不加载外部可执行二进制，安全合规。
  - **在线 OpenAI 兼容 API**：支持用户自定义填入 DeepSeek、硅基流动、通义千问、OpenAI 或自建 Ollama / vLLM 网关地址、API Key 与模型名称，在通知测试台中以「AI理解」直接取代传统语义解析。
- **待识别询问与规则学习**：无法准确判定时自动收入「待识别询问」卡片，点选通知内提取的关键词标签即可快速生成/更新自定义识别规则，支持学习数字千分位/小数格式与编辑具体消费时间。
- **双通道保活机制**：在「设置 -> 系统权限与进程保活」中集中管理通知使用权、无障碍保活守护、电池优化白名单与可选的 **Root (`su`) 进程保活**。

### 2. 账单主页与长按多选批量编辑
- **账单时间自由编辑**：所有新增、编辑与待询问入账弹窗均支持精确调整账单日期与具体时间（内置日期选择器及「此刻 / -1小时 / 昨天此时」快捷校准）。
- **长按多选 & 筛选结果批量操作**：在账单主页及任意搜索/筛选条件下，长按任意账单卡片即可进入多选模式（也可在筛选后一键「多选全部结果」）。
- **批量编辑与删除**：支持同时批量修改所选账单的 **收支类型、分类、支付渠道、商户名称、币种（自动刷新汇率快照）与备注**，或批量删除；按返回键或点击关闭按钮即可退出多选。
- **金融黑色幽默 Tips 与彩蛋**：首页顶部横幅默认展示 Tag「我告诉你」（外观设置输入框默认留空，可按需自定义覆盖）与黑色幽默理财文案；连续点击横幅 7 次可触发弹窗彩蛋（需点击指定按钮关闭）。

### 3. 账单工具多级中心（「设置 -> 账单工具」）
以清爽的多级菜单组织核心账务与数据空间控制：
- **记账与预算**：配置自动记账开关、进软件后手动确认模式、入账通知提醒与每月支出预算。
- **币种与汇率**：设置统计主币种，查看外币即时折算与历史汇率快照；支持配置 **汇率数据库自动更新频率**（仅手动更新 / 每 6 小时 / 每天 / 每 3 天 / 每周），并在应用回到前台或切换配置后按周期自动刷新。
- **智能去重**：仅识别短时间窗口内「相同金额 + 同币种 + 同收支方向 + 交易对象相似」的重复入账，自动排除长期周期消费；内置 5 分钟 ~ 24 小时常用预设，并支持自定义分钟/小时窗口（最高 7 天）、跨渠道/同渠道与关键词筛选。
- **数据空间与存储优化**：实时统计 SQLite 主库、WAL 日志、DataStore、本地模型与缓存体积；支持 SQLite `VACUUM` 增量压缩、WAL 归档收缩与一键精简 30 天前已确认账单的冗余原始通知正文，持续瘦身用户数据体积。

### 4. 全粒度收支分析（完整展示无缩略）
- **年度 / 月 / 周 / 日 四档粒度**：支持按年度、自然月、自然周（周一至周日）与单日细粒度查看账单结构，并可前翻、后翻或一键「回到今天」。
- **完整数据呈现**：取消条目标签截断与整数缩写，金额与分类/渠道名称支持自适应缩字号、轻度缩宽及双行折行完整展示，柱状图项目过多时支持横向流畅滑动查阅。
- **多图表交互与下钻**：支出分类占比与渠道分布均支持 **饼图 / 柱状图** 切换，点击图例或排行条目即可下钻筛选并编辑对应账单。

### 5. 导航动画与手势交互（「设置 -> 外观与布局」）
- **主页横滑切换 Tab**：账单、收支分析、设置三个一级页面支持左右横滑无缝切换；点击底部/侧边 Tab 时支持自定义切换动画（平滑滑动 / 淡入渐隐 / 无动画）。
- **预测性返回与多级菜单横滑返回**：支持 Android 预测性返回手势过渡（缩放退场 / 右滑退场 / 轻柔淡出），所有二级、三级菜单页面均支持向右横滑实时预览并返回上一级菜单。

### 6. 导入与备份中心（「设置 -> 导入与备份中心」）
- **手动导入（支持 XLSX / CSV / TSV / JSON / 自由文本）**：自动跳过前置说明定位真实表头，支持分离的「支出金额 / 收入金额」列、自动排重「账户余额」列、UTF-8 / GB18030 自动识别与 Excel 序列日期解析。
- **本地备份与通知提醒**：一键导出/恢复紧凑型 JSON 备份（省略冗余默认值缩小体积，向后兼容）；支持自定义定期备份提醒间隔（小时 / 天），到期后通过高重要性通知通道提醒。
- **WebDAV 云端同步**：支持配置 WebDAV 服务器地址、账号密码，手动上传快照或从云端拉取合并。

### 7. 轻量化安装包构建与关于
- **安装包瘦身**：内置自绘轻量矢量图标替代冗余图标依赖包、静态图片 WebP 压缩、Release 构建开启 R8 代码混淆与资源裁剪，显著降低 APK 安装包体积。
- **关于（「设置 -> 关于」）**：展示应用图标、版本号与 Build 号、上游项目说明、MIT 开源协议详情，以及可点击跳转浏览器的 GitHub 仓库与开发工具（Google AI Studio）卡片框。

---

## 目录结构

```text
app/src/main/java/com/example/
├── data/
│   ├── LedgerDatabase.kt          # Room 数据库 (增量空间回收与 VACUUM/PRUNE 清理)
│   ├── LedgerRepository.kt        # 仓库层、批量更新、空间统计与存储优化
│   ├── SettingsDataStore.kt       # 外观、手势动画、预算、汇率频率与 AI 配置持久化
│   ├── AiUnderstandingManager.kt  # 本地开源小模型按需下载推理 + OpenAI 兼容 API（含交易时间提取）
│   ├── ManualImportManager.kt     # XLSX / CSV / TSV / JSON / 文本自动表头与时间解析导入
│   ├── XlsxTableReader.kt         # 纯 Kotlin 零依赖 XLSX 解析器 (Zip + DOM XML)
│   ├── CurrencyExchangeManager.kt # 多币种实时汇率更新与本地缓存
│   ├── LedgerBackupCodec.kt       # 紧凑 JSON 备份编解码
│   └── WebDavSyncClient.kt        # WebDAV 同步客户端
├── model/                         # 领域模型、粒度、手势样式、存储空间模型与目录常量
├── parser/
│   ├── ChinaNotificationParser.kt # 全渠道通知与交易发生时间解析、关键词候选与数字格式学习
│   ├── SmartDeduplicator.kt       # 短时同金额与相似商户智能聚类去重
│   ├── LocalSpendingAnalyzer.kt   # 年度/月/周/日多粒度收支洞察引擎
│   └── FinanceHumorTips.kt        # 金融黑色幽默语录库
├── service/                       # 通知监听、无障碍保活守护、Root 保活与备份通知调度
├── ui/
│   ├── LedgerHomeScreen.kt        # 账单主页（长按多选、批量操作、待询问/待确认）
│   ├── AnalyticsScreen.kt         # 收支分析（年/月/周/日切换、全量不缩略图表与下钻）
│   ├── SettingsScreen.kt          # 设置主页（纯二级菜单入口）
│   ├── LedgerManagementScreen.kt  # 账单工具多级菜单（记账预算 / 存储优化与子路由入口）
│   ├── AiSettingsScreen.kt        # AI理解（本地小模型下载 / 在线 API 配置）
│   ├── PermissionsKeepAliveScreen.kt # 系统权限与进程保活二级菜单
│   ├── AboutScreen.kt             # 关于二级菜单（图标、版本、MIT 协议、作者仓库与 AI Studio 链接）
│   ├── DataHubScreen.kt             # 导入与备份中心（手动导入 / 本地备份 / WebDAV）
│   ├── SmartDeduplicationScreen.kt# 短时智能去重页面（含自定义时间窗口）
│   ├── SettingsSubScreens.kt      # 通知识别与规则、币种汇率、外观布局及手势动画设置
│   ├── MainAppScaffold.kt         # HorizontalPager 主页横滑、PredictiveBack 与多级菜单 SwipeBackContainer
│   ├── theme/                     # Material 3 主题与轻量矢量 AppIcons
│   └── components/                # 账单详情、含时间编辑器的单条编辑、BatchEditDialog 批量编辑弹窗
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
