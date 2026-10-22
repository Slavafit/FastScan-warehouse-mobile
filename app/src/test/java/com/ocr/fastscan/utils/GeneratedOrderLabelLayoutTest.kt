package com.ocr.fastscan.utils

import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedOrderLabelLayoutTest {
    @Test
    fun `generated qr stays above tracking text`() {
        val gap = GeneratedOrderLabelLayout.TRACKING_TEXT_Y -
            GeneratedOrderLabelLayout.estimatedQrBottom

        assertTrue(
            "QR and tracking text gap is only $gap dots",
            gap >= GeneratedOrderLabelLayout.MIN_TEXT_GAP_DOTS
        )
    }

    @Test
    fun `zpl uses verified qr and text coordinates`() {
        val tracking = "ONZA12345678"
        val zpl = GeneratedOrderLabelLayout.buildCodeBlock(tracking, LABEL_WIDTH_DOTS)

        assertTrue(zpl.contains("^FO287,0^BQN,2,9^FDQA,$tracking^FS"))
        assertTrue(zpl.contains("^FO60,350^FB680,1,0,C,0^FD$tracking^FS"))
    }

    @Test
    fun `qr and tracking text share the label center`() {
        val qrCenterTimesTwo = GeneratedOrderLabelLayout.qrX(LABEL_WIDTH_DOTS) * 2 +
            GeneratedOrderLabelLayout.qrPrintedSizeDots
        val textCenterTimesTwo = GeneratedOrderLabelLayout.TRACKING_TEXT_HORIZONTAL_MARGIN * 2 +
            GeneratedOrderLabelLayout.trackingTextWidth(LABEL_WIDTH_DOTS)

        assertTrue(
            "QR and text centers differ by more than one printer dot",
            kotlin.math.abs(qrCenterTimesTwo - textCenterTimesTwo) <= 1
        )
    }

    private companion object {
        const val LABEL_WIDTH_DOTS = 800
    }
}
