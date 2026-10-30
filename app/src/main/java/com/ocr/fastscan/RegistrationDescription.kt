package com.ocr.fastscan

internal object RegistrationDescription {
    fun resolve(recognizedOrderNumber: String?): String? =
        recognizedOrderNumber
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { "Номер заказа: $it" }

    fun extractOrderNumber(stockComment: String?): String? {
        val value = stockComment?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return ORDER_NUMBER_REGEX.find(value)?.value
    }

    private val ORDER_NUMBER_REGEX = Regex("""\d+""")
}
