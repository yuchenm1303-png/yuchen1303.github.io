package com.yuchen.ailedger.service

import java.time.LocalTime

sealed class MobileCommand {
    abstract val title: String
    abstract val summary: String

    data class SetAlarm(
        val hour: Int,
        val minute: Int,
        val label: String,
        val dateLabel: String,
    ) : MobileCommand() {
        override val title: String = "设置系统闹钟"
        override val summary: String = "$dateLabel ${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')} · $label"
    }

    data class OpenApp(
        val appName: String,
        val packageName: String?,
    ) : MobileCommand() {
        override val title: String = "打开手机应用"
        override val summary: String = appName
    }

    data class Navigate(
        val destination: String,
        val mode: String,
    ) : MobileCommand() {
        override val title: String = "地图导航"
        override val summary: String = "到 $destination"
    }
}

object MobileCommandParser {
    fun parse(text: String): MobileCommand? {
        val clean = text.trim()
        if (clean.isBlank()) return null
        return parseAlarm(clean) ?: parseNavigation(clean) ?: parseOpenApp(clean)
    }

    private fun parseAlarm(text: String): MobileCommand.SetAlarm? {
        if (!Regex("(闹钟|叫我|提醒我|提醒一下|叫醒|起床)").containsMatchIn(text)) return null
        val match = Regex("(\\d{1,2})(?:[:：点时](\\d{1,2})?分?)?").find(text) ?: return null
        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }?.toIntOrNull() ?: 0
        if (minute !in 0..59) return null
        hour = normalizeMeridiem(hour, text)
        if (hour !in 0..23) return null

        val dateLabel = when {
            text.contains("后天") -> "后天"
            Regex("明天|明早|明晚").containsMatchIn(text) -> "明天"
            Regex("今天|今晚").containsMatchIn(text) -> "今天"
            LocalTime.of(hour, minute).isBefore(LocalTime.now()) -> "明天"
            else -> "今天"
        }
        val label = buildAlarmLabel(text)
        return MobileCommand.SetAlarm(hour = hour, minute = minute, label = label, dateLabel = dateLabel)
    }

    private fun normalizeMeridiem(hour: Int, text: String): Int {
        if (Regex("下午|晚上|傍晚|今晚").containsMatchIn(text) && hour < 12) return hour + 12
        if (text.contains("中午") && hour < 11) return hour + 12
        if (Regex("凌晨|早上|上午|明早|明天早上").containsMatchIn(text) && hour == 12) return 0
        return hour
    }

    private fun buildAlarmLabel(text: String): String {
        val afterKeyword = Regex("(?:提醒我|叫我|叫醒我|闹钟)(.*)$").find(text)?.groupValues?.getOrNull(1).orEmpty()
        val cleaned = afterKeyword
            .replace(Regex("(明天|后天|今天|今晚|明早|明晚|上午|下午|晚上|早上|凌晨|中午)"), "")
            .replace(Regex("\\d{1,2}(?:[:：点时]\\d{0,2})?分?"), "")
            .replace(Regex("^(去|要|一下|起床)"), "")
            .trim()
        return cleaned.ifBlank { if (Regex("起床|叫醒").containsMatchIn(text)) "起床" else "AI 助手提醒" }
    }

    private fun parseOpenApp(text: String): MobileCommand.OpenApp? {
        val match = Regex("(?:打开|启动|帮我打开)\\s*([\\u4e00-\\u9fa5A-Za-z0-9]+)$").find(text) ?: return null
        val appName = match.groupValues[1].trim()
        if (appName.isBlank() || Regex("闹钟|提醒|记账|地图|导航").containsMatchIn(appName)) return null
        return MobileCommand.OpenApp(appName = appName, packageName = knownPackage(appName))
    }

    private fun knownPackage(appName: String): String? {
        val clean = appName.lowercase()
        return when {
            appName.contains("微信") || clean == "wechat" -> "com.tencent.mm"
            appName.contains("支付宝") || clean == "alipay" -> "com.eg.android.AlipayGphone"
            appName.contains("高德") || clean == "amap" -> "com.autonavi.minimap"
            appName.contains("百度地图") || clean == "baidumap" -> "com.baidu.BaiduMap"
            appName.contains("QQ", ignoreCase = true) -> "com.tencent.mobileqq"
            appName.contains("淘宝") -> "com.taobao.taobao"
            appName.contains("京东") -> "com.jingdong.app.mall"
            else -> null
        }
    }

    private fun parseNavigation(text: String): MobileCommand.Navigate? {
        if (!Regex("(导航|路线|带我去|回家|到家|怎么走|怎么去|去学校|去公司|去宿舍|去寝室|去家里)").containsMatchIn(text)) return null
        val destinationMatch = Regex("(?:导航(?:到|去)?|路线到|带我去|怎么去|怎么到)\\s*([^，。；;\\n]+)").find(text)
            ?: Regex("去\\s*([^，。；;\\n]+?)(?:怎么走|怎么去|路线|导航)?$").find(text)
            ?: Regex("(?:回|到)(家|学校|公司|宿舍|寝室)$").find(text)
        var destination = destinationMatch?.groupValues?.getOrNull(1)?.trim().orEmpty()
        if (Regex("回家|到家|去家|家里|我家").containsMatchIn(text)) destination = "家"
        if (Regex("去学校|到学校|回学校").containsMatchIn(text)) destination = "学校"
        if (Regex("去公司|到公司|回公司|去单位|到单位").containsMatchIn(text)) destination = "公司"
        if (Regex("去宿舍|回宿舍|到宿舍|去寝室|回寝室").containsMatchIn(text)) destination = "宿舍"
        destination = cleanDestination(destination)
        if (destination.isBlank() || Regex("^(打开|启动)?(百度地图|高德地图|地图)$").matches(destination)) return null
        return MobileCommand.Navigate(destination = destination, mode = inferTravelMode(text))
    }

    private fun cleanDestination(value: String): String {
        return value.trim()
            .replace(Regex("^(百度地图|高德地图|地图|帮我|请|给我|用百度|用高德|打开地图|开车|驾车|步行|走路|骑行|公交|地铁|坐公交|坐地铁)"), "")
            .replace(Regex("(怎么走|怎么去|路线|导航|导航一下|带路)$"), "")
            .replace(Regex("^(到|去|回)"), "")
            .trim()
    }

    private fun inferTravelMode(text: String): String = when {
        Regex("公交|地铁|轨道|轻轨|换乘|坐车|公共交通").containsMatchIn(text) -> "transit"
        Regex("步行|走路|步走").containsMatchIn(text) -> "walking"
        Regex("骑行|骑车|单车|自行车|电动车").containsMatchIn(text) -> "riding"
        else -> "driving"
    }
}
