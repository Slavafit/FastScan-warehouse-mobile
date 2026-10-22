package com.ocr.fastscan

import android.os.SystemClock
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

@ExperimentalGetImage
class TrackAnalyzer(
    private val onResult: (String) -> Unit
) : ImageAnalysis.Analyzer {
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val barcodeScanner = BarcodeScanning.getClient()
    private val orderNumberStabilizer = ScanResultStabilizer(requiredMatches = 2)
    private val barcodeStabilizer = ScanResultStabilizer()
    private val startedAtMs = SystemClock.elapsedRealtime()

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        textRecognizer.process(image)
            .addOnSuccessListener { visionText ->
                val orderNumber = OrderNumberTextParser.parse(visionText.text)
                if (orderNumber != null) {
                    orderNumberStabilizer.add(orderNumber)?.let(onResult)
                    imageProxy.close()
                } else if (SystemClock.elapsedRealtime() - startedAtMs >= BARCODE_FALLBACK_DELAY_MS) {
                    scanBarcode(image, imageProxy)
                } else {
                    imageProxy.close()
                }
            }
            .addOnFailureListener {
                if (SystemClock.elapsedRealtime() - startedAtMs >= BARCODE_FALLBACK_DELAY_MS) {
                    scanBarcode(image, imageProxy)
                } else {
                    imageProxy.close()
                }
            }
    }

    private fun scanBarcode(image: InputImage, imageProxy: ImageProxy) {
        barcodeScanner.process(image)
            .addOnSuccessListener { barcodes ->
                barcodes.firstNotNullOfOrNull { it.rawValue?.takeIf(String::isNotBlank) }
                    ?.let(barcodeStabilizer::add)
                    ?.let(onResult)
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    companion object {
        private const val BARCODE_FALLBACK_DELAY_MS = 1_200L
    }
}
