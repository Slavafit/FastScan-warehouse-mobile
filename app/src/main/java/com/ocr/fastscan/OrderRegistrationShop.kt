package com.ocr.fastscan

internal object TrackScanSource {
    const val ORDER_NUMBER = "order_number"
    const val BARCODE = "barcode"
}

internal object OrderRegistrationShop {
    fun resolve(currentTracking: String, orderNumberTracking: String?): String? =
        if (orderNumberTracking != null && currentTracking == orderNumberTracking) "Zara" else null
}
