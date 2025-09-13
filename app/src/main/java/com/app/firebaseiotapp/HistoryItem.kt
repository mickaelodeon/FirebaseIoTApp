package com.app.firebaseiotapp

data class TurbidityReading(
    val turbidity: Double = 0.0,
    val timestamp: String = "",
    val unit: String = "NTU",
    val device_id: String = ""
)

data class DeviceStatus(
    val status: String = "",
    val wifi_rssi: Int = 0,
    val free_heap: Long = 0
)

data class Alert(
    val type: String = "",
    val value: Double = 0.0,
    val message: String = ""
)
