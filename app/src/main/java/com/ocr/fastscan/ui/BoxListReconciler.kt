package com.ocr.fastscan.ui

import com.ocr.fastscan.data.model.BoxItem
import java.util.Locale

internal object BoxListReconciler {
    fun normalizedTracking(value: String?): String =
        value.orEmpty().trim().uppercase(Locale.ROOT)

    fun identityKey(box: BoxItem): String? =
        box.guid?.takeIf { it.isNotBlank() }
            ?: box.codeid?.takeIf { it.isNotBlank() }

    fun sameTracking(first: String?, second: String?): Boolean {
        val normalizedFirst = normalizedTracking(first)
        return normalizedFirst.isNotEmpty() && normalizedFirst == normalizedTracking(second)
    }

    fun isSameBox(first: BoxItem, second: BoxItem): Boolean {
        val firstKey = identityKey(first)
        val secondKey = identityKey(second)
        val sameIdentity = firstKey != null && secondKey != null && firstKey == secondKey
        return sameIdentity || sameTracking(first.trackingCode, second.trackingCode)
    }

    fun deduplicateAndPrioritize(
        boxes: List<BoxItem>,
        preferredTrackingCode: String?
    ): List<BoxItem> {
        val dedupedBoxes = linkedMapOf<String, BoxItem>()
        boxes.forEach { box ->
            val normalizedTracking = normalizedTracking(box.trackingCode)
            val key = if (normalizedTracking.isNotEmpty()) {
                "tracking:$normalizedTracking"
            } else {
                identityKey(box)?.let { "identity:$it" }
            } ?: return@forEach

            val current = dedupedBoxes[key]
            dedupedBoxes[key] = when {
                current == null -> box
                sameTracking(box.trackingCode, preferredTrackingCode) -> box
                sameTracking(current.trackingCode, preferredTrackingCode) -> current
                else -> box
            }
        }

        return dedupedBoxes.values.sortedByDescending {
            sameTracking(it.trackingCode, preferredTrackingCode)
        }
    }
}
