package com.pqsolutions.hdd_monitor.bluetooth

data class BleConfiguration(
    val clientId: String,
    val clientName: String,
    val panelId: String,
    val panelName: String,
    val panelLocation: String,
    val wifiSsid: String,
    val wifiPassword: String
) {
    fun toBleMessage(): String {
        val shortPanelId = panelId.substringAfterLast("_")
        val shortClientId = clientId.substringAfterLast("_")
        return buildString {
            append("cid:$shortClientId,")
            append("pid:$shortPanelId,")
            append("ssid:$wifiSsid,")
            append("pwd:$wifiPassword,")
            append("name:$panelName,")
            append("loc:$panelLocation")
        }
    }
}