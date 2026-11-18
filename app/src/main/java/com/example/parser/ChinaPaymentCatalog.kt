package com.example.parser

import com.example.model.ChannelCatalogItem
import com.example.model.SourceApp

object ChinaPaymentCatalog {

    val smsPackageNames = setOf(
        "com.android.mms",
        "com.google.android.apps.messaging",
        "com.huawei.mms",
        "com.miui.mms",
        "com.coloros.mms",
        "com.vivo.mms",
        "com.samsung.android.messaging"
    )

    val channels: List<ChannelCatalogItem> = listOf(
        ChannelCatalogItem(
            id = "alipay",
            name = "支付宝",
            shortCode = "支",
            categoryLabel = "第三方支付",
            packageNames = listOf("com.eg.android.AlipayGphone"),
            smsCodes = emptyList(),
            sampleNotificationTitle = "支付宝支付助手",
            sampleNotificationBody = "你已成功向瑞幸咖啡(中关村店)付款18.50元，使用花呗扣款。"
        ),
        ChannelCatalogItem(
            id = "wechat",
            name = "微信支付",
            shortCode = "微",
            categoryLabel = "第三方支付",
            packageNames = listOf("com.tencent.mm"),
            smsCodes = emptyList(),
            sampleNotificationTitle = "微信支付",
            sampleNotificationBody = "微信支付：你已向「美团外卖」付款38.60元，支付方式：零钱通。"
        ),
        ChannelCatalogItem(
            id = "unionpay",
            name = "云闪付",
            shortCode = "银",
            categoryLabel = "第三方支付",
            packageNames = listOf("com.unionpay"),
            smsCodes = listOf("95516"),
            sampleNotificationTitle = "云闪付动账提醒",
            sampleNotificationBody = "您尾号6209的银联卡于今日在永辉超市消费人民币126.80元，享受立减优惠。"
        ),
        ChannelCatalogItem(
            id = "cmb",
            name = "招商银行",
            shortCode = "招",
            categoryLabel = "商业银行",
            packageNames = listOf("com.cmbchina.pb", "com.cmbchina.ccd.pluto.cmbActivity"),
            smsCodes = listOf("95555"),
            sampleNotificationTitle = "95555 招商银行",
            sampleNotificationBody = "【招商银行】您账户8812于11月18日08:30在星巴克(静安寺店)消费人民币42.00元，可用余额3,210.50元。"
        ),
        ChannelCatalogItem(
            id = "icbc",
            name = "工商银行",
            shortCode = "工",
            categoryLabel = "国有大行",
            packageNames = listOf("com.icbc", "com.icbc.im"),
            smsCodes = listOf("95588"),
            sampleNotificationTitle = "95588 工商银行",
            sampleNotificationBody = "【工商银行】您尾号1024卡11月18日09:15工资入账人民币18,500.00元，余额26,430.20元。"
        ),
        ChannelCatalogItem(
            id = "ccb",
            name = "建设银行",
            shortCode = "建",
            categoryLabel = "国有大行",
            packageNames = listOf("com.chinamworld.main"),
            smsCodes = listOf("95533"),
            sampleNotificationTitle = "95533 建设银行",
            sampleNotificationBody = "【建设银行】您尾号3391龙卡信用卡11月18日在盒马鲜生消费支出人民币158.40元，当前可用额度48,200元。"
        ),
        ChannelCatalogItem(
            id = "abc",
            name = "农业银行",
            shortCode = "农",
            categoryLabel = "国有大行",
            packageNames = listOf("com.android.bankabc"),
            smsCodes = listOf("95599"),
            sampleNotificationTitle = "95599 农业银行",
            sampleNotificationBody = "【中国农业银行】您尾号5570账户于11月18日向滴滴出行快捷支付人民币29.30元，余额4,102.10元。"
        ),
        ChannelCatalogItem(
            id = "boc",
            name = "中国银行",
            shortCode = "中",
            categoryLabel = "国有大行",
            packageNames = listOf("com.chinamworld.bocmbci"),
            smsCodes = listOf("95566"),
            sampleNotificationTitle = "95566 中国银行",
            sampleNotificationBody = "【中国银行】您的借记卡账户3048于11月18日支取人民币85.00元（京东商城消费），交易后余额12,300.00元。"
        ),
        ChannelCatalogItem(
            id = "bocom",
            name = "交通银行",
            shortCode = "交",
            categoryLabel = "国有大行",
            packageNames = listOf("com.bankcomm.Bankcomm"),
            smsCodes = listOf("95559"),
            sampleNotificationTitle = "95559 交通银行",
            sampleNotificationBody = "【交通银行】您尾号7721信用卡消费人民币215.00元（山姆会员商店），最红星期五返现已到账。"
        ),
        ChannelCatalogItem(
            id = "psbc",
            name = "邮储银行",
            shortCode = "邮",
            categoryLabel = "国有大行",
            packageNames = listOf("com.yitong.mbank.psbc"),
            smsCodes = listOf("95580"),
            sampleNotificationTitle = "95580 邮储银行",
            sampleNotificationBody = "【邮储银行】您尾号4418账户支取人民币120.00元，商户：国家电网电费代扣。"
        ),
        ChannelCatalogItem(
            id = "pingan",
            name = "平安银行",
            shortCode = "平",
            categoryLabel = "商业银行",
            packageNames = listOf("com.pingan.paces.ccms"),
            smsCodes = listOf("95511"),
            sampleNotificationTitle = "95511 平安银行",
            sampleNotificationBody = "【平安银行】您尾号9013信用卡在华润万家刷卡消费RMB88.50元。"
        ),
        ChannelCatalogItem(
            id = "spdb",
            name = "浦发银行",
            shortCode = "浦",
            categoryLabel = "商业银行",
            packageNames = listOf("com.spdbccc.app", "cn.com.spdb.mobilebank.per"),
            smsCodes = listOf("95528"),
            sampleNotificationTitle = "95528 浦发银行",
            sampleNotificationBody = "【浦发银行】您尾号6615卡发生一笔消费，金额人民币64.00元，商户：喜茶HEYTEA。"
        ),
        ChannelCatalogItem(
            id = "cib",
            name = "兴业银行",
            shortCode = "兴",
            categoryLabel = "商业银行",
            packageNames = listOf("com.cib.cibmb"),
            smsCodes = listOf("95561"),
            sampleNotificationTitle = "95561 兴业银行",
            sampleNotificationBody = "【兴业银行】您尾号2180借记卡于11月18日收入人民币256.80元（理财产品收益到账）。"
        ),
        ChannelCatalogItem(
            id = "citic",
            name = "中信银行",
            shortCode = "信",
            categoryLabel = "商业银行",
            packageNames = listOf("com.ecitic.bank.mobile"),
            smsCodes = listOf("95558"),
            sampleNotificationTitle = "95558 中信银行",
            sampleNotificationBody = "【中信银行】您尾号5129信用卡在海底捞火锅消费人民币318.00元。"
        ),
        ChannelCatalogItem(
            id = "ceb",
            name = "光大银行",
            shortCode = "光",
            categoryLabel = "商业银行",
            packageNames = listOf("com.cebbank.mobile.cemb"),
            smsCodes = listOf("95595"),
            sampleNotificationTitle = "95595 光大银行",
            sampleNotificationBody = "【光大银行】您尾号7340信用卡在麦当劳消费人民币35.00元。"
        ),
        ChannelCatalogItem(
            id = "cmbc",
            name = "民生银行",
            shortCode = "民",
            categoryLabel = "商业银行",
            packageNames = listOf("cn.com.cmbc.newmbank"),
            smsCodes = listOf("95568"),
            sampleNotificationTitle = "95568 民生银行",
            sampleNotificationBody = "【民生银行】您尾号8204账户于今日在叮咚买菜支出人民币52.90元。"
        ),
        ChannelCatalogItem(
            id = "cgb",
            name = "广发银行",
            shortCode = "广",
            categoryLabel = "商业银行",
            packageNames = listOf("com.cgbchina.xpt"),
            smsCodes = listOf("95508"),
            sampleNotificationTitle = "95508 广发银行",
            sampleNotificationBody = "【广发银行】您尾号1906发现精彩卡在万达影城消费人民币78.00元。"
        )
    )

    val watchedPackageMap: Map<String, ChannelCatalogItem> = buildMap {
        channels.forEach { item ->
            item.packageNames.forEach { pkg -> put(pkg, item) }
        }
    }

    fun isWatchedPackage(packageName: String): Boolean {
        return watchedPackageMap.containsKey(packageName) || smsPackageNames.contains(packageName)
    }

    fun resolveSourceApp(packageName: String, text: String): SourceApp {
        return when {
            packageName == "com.eg.android.AlipayGphone" || text.contains("支付宝") || text.contains("花呗") -> SourceApp.ALIPAY
            packageName == "com.tencent.mm" || text.contains("微信支付") || text.contains("微信转账") -> SourceApp.WECHAT
            packageName == "com.unionpay" || text.contains("云闪付") -> SourceApp.UNIONPAY
            smsPackageNames.contains(packageName) -> SourceApp.BANK_SMS
            watchedPackageMap.containsKey(packageName) -> SourceApp.BANK_APP
            text.contains("银行") || Regex("955\\d{2}").containsMatchIn(text) -> SourceApp.BANK_SMS
            else -> SourceApp.LOCAL_BENCH
        }
    }
}
