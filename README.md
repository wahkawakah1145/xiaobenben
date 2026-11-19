# 小本本 (XiaoBenBen)

一款基于 Kotlin 与 Jetpack Compose (Material Design 3) 构建的 Android 智能通知记账与账单管理应用。通过监听系统支付与银行动账通知实现自动解析入账，并集成多币种汇率对照、自定义规则学习、XLSX/CSV 智能表头导入、批量编辑、智能去重、本地备份与 WebDAV 同步等功能。

- **包名**：`com.live150yo.lvshuiqingshan`
- **系统支持**：Android 11 (API 30) 及以上 · `targetSdk 37`（Android 17）
- **构建技术栈**：Kotlin · Jetpack Compose (M3) · Room · DataStore · Navigation Compose · OkHttp · Coroutines/Flow

---

## 核心功能

### 1. 智能通知识别与规则引擎
- **全渠道通知自动识别**：监听微信支付、支付宝、云闪付及国内主流银行短信/App 通知，自动识别金额、币种、交易类型（支出 / 收入 / 退款 / 转账）、商户、渠道、卡尾号与分类，并自动过滤验证码等非账单消息。
- **待确认模式**：可在设置中开启「自动记录，进本软件后手动确认」，自动捕捉的账单会先进入待确认清单，确认后再计入统计。
- **待识别询问与自适应学习**：无法准确判定时自动收入「待识别询问」卡片，点选通知内提取的关键词标签即可快速生成/更新自定义识别规则，同时支持学习数字千分位/小数点格式（例如 `2111.10`、`2,111.10` 与欧式 `2.111,10`）。
- **每条规则皆可自由编辑**：所有内置与自定义规则均可在「通知识别与规则」二级菜单中测试、增删、启停与手动修改。
- **双通道保活机制**：支持开启「无障碍保活守护」（监听服务断开时自动重绑）、电池优化白名单引导以及可选的 **Root (`su`) 进程保活**。

### 2. 账单主页与长按多选批量编辑
- **长按多选 & 筛选结果批量操作**：在账单主页及任意搜索/筛选条件下，长按任意账单卡片即可进入多选模式（也可在筛选后一键「多选全部结果」）。
- **批量编辑与删除**：支持同时批量修改所选账单的 **收支类型、分类、支付渠道、商户名称、币种（自动刷新汇率快照）与备注**，或批量删除；按返回键或点击关闭按钮即可退出多选。
- **金融黑色幽默 Tips 与彩蛋**：顶部横幅支持轮换金融黑色幽默文案，可在「外观与布局」中自定义 Tag 文案与轮换语录；连续点击横幅 7 次可触发弹窗彩蛋（需点击指定按钮关闭）。

### 3. 多币种与汇率快照
- **多币种记账**：内置常用多币种支持，账单列表固定记录并显示记账当时的汇率与折算结果。
- **详情实时对照**：点开任意外币账单详情页，可同时对比「记账当时汇率折算金额」与「当前汇率数据库实时换算金额」。
- **按需联网更新**：汇率数据库展示数据来源与更新时间，仅在用户主动点击更新时联网拉取最新汇率。

### 4. 收支分析
- **多图表交互统计**：收入、支出与结余汇总展示；支出分类占比与支付渠道分布卡片右上角均支持自由切换 **饼图 / 柱状图**。
- **图例/列表下钻筛选**：点击任意图例或排行条目，即可弹窗查看该分类/渠道下的对应账单明细，并支持直接进入详情或编辑。

### 5. 导入与备份中心（统一二级菜单）
在「设置 -> 导入与备份中心」中集成了以下三大模块：
- **手动导入（支持 XLSX / CSV / TSV / JSON / 自由文本）**：
  - 支持直接选择微信支付、支付宝、银行导出的 `.xlsx`、`.csv`、`.tsv` 或 `.json` 文件，亦支持直接粘贴账单文本。
  - **自动表头定位**：自动跳过导出文件开头的多行账户统计、昵称或免责说明，精准识别真实表头列。
  - **复杂格式兼容**：支持分离的「支出金额 / 收入金额」列、正负号金额识别、自动排重「账户余额」干扰列、UTF-8 / GB18030 自动编码识别（适配支付宝 CSV）以及 Excel 序列日期解析。
- **本地备份与紧急提醒**：
  - 一键导出全量 JSON 备份至本地文件，支持从本地备份文件恢复合并。
  - 支持自定义定期备份提醒间隔（小时 / 天），到期后通过高优先级紧急通知渠道 (`IMPORTANT_HIGH`) 提醒，点击通知直达本地备份页。
- **WebDAV 云端同步**：支持配置 WebDAV 服务器地址、账号密码，手动上传快照或从云端拉取合并。

### 6. 智能去重
在「设置 -> 智能去重」二级菜单中：
- 自动整合「相同金额 + 同币种 + 同收支方向 + 交易对象名称相似」的疑似重复账单组（例如跨渠道重复入账的支付宝与银行卡通知）。
- 支持按 **时间窗口**（1小时内 / 24小时内 / 7天内 / 30天内 / 不限）、**渠道类型**（全部 / 仅跨渠道重复 / 仅同渠道重复）与 **关键词** 进行筛选。
- 支持一键「智能勾选重复项」（自动保留信息最完整的记录）或逐组选择「仅留此条 / 全部保留」，确认后批量清理。

### 7. 个性化外观与莫奈取色
- **Material You 动态取色 & 图标自适应**：支持跟随系统莫奈动态配色，桌面自适应图标支持 Android 13+ 主题单色取色。
- **自由排版与字体切换**：支持自定义卡片密度、FAB 按钮左右位置、顶部 Tips 与月度汇总卡上下顺序、徽章显隐，以及英文/数字字体切换（系统默认 / 内置等宽或圆润数字字体）。

---

## 目录结构

```text
app/src/main/java/com/example/
├── data/
│   ├── LedgerDatabase.kt          # Room 数据库 (transactions / parsing_rules / notification_inquiries)
│   ├── LedgerRepository.kt        # 仓库层与批量更新、导入去重事务
│   ├── ManualImportManager.kt     # XLSX / CSV / TSV / JSON / 文本自动表头解析导入
│   ├── XlsxTableReader.kt         # 纯 Kotlin 零依赖 XLSX 解析器 (Zip + DOM XML)
│   ├── CurrencyExchangeManager.kt # 多币种实时汇率更新与本地缓存
│   ├── LedgerBackupCodec.kt       # 本地 JSON 备份编解码
│   └── WebDavSyncClient.kt        # WebDAV 同步客户端
├── model/                         # 领域模型、设置与目录常量
├── parser/
│   ├── ChinaNotificationParser.kt # 全渠道通知解析、关键词候选提取与数字格式学习
│   ├── SmartDeduplicator.kt       # 相似商户与同金额账单智能聚类去重
│   ├── LocalSpendingAnalyzer.kt   # 本地统计与分析文案引擎
│   └── FinanceHumorTips.kt        # 金融黑色幽默语录库
├── service/                       # 通知监听、无障碍保活守护、Root 保活与紧急备份提醒
├── ui/
│   ├── LedgerHomeScreen.kt        # 账单主页（含长按多选、搜索结果批量操作、待询问/待确认）
│   ├── AnalyticsScreen.kt         # 收支分析（饼图/柱状图切换、下钻筛选）
│   ├── SettingsScreen.kt          # 设置主页
│   ├── DataHubScreen.kt           # 导入与备份中心（手动导入 / 本地备份 / WebDAV）
│   ├── SmartDeduplicationScreen.kt# 智能去重页面
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
