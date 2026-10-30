package com.ocr.fastscan

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

@ExperimentalGetImage
class TrackAnalyzer(
    private val onResult: (tracking: String, orderNumber: String?) -> Unit
) : ImageAnalysis.Analyzer {
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val barcodeScanner = BarcodeScanning.getClient()
    private val orderNumberStabilizer = ScanResultStabilizer(requiredMatches = 2)
    private val barcodeStabilizer = ScanResultStabilizer()
    private var recognizedOrderNumber: String? = null

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
                    orderNumberStabilizer.add(orderNumber)?.let {
                        recognizedOrderNumber = it
                    }
                }
                scanBarcode(image, imageProxy)
            }
            .addOnFailureListener {
                scanBarcode(image, imageProxy)
            }
    }

    private fun scanBarcode(image: InputImage, imageProxy: ImageProxy) {
        barcodeScanner.process(image)
            .addOnSuccessListener { barcodes ->
                barcodes.firstNotNullOfOrNull { it.rawValue?.takeIf(String::isNotBlank) }
                    ?.let(barcodeStabilizer::add)
                    ?.let { onResult(it, recognizedOrderNumber) }
            }
            .addOnCompleteListener { imageProxy.close() }
    }
}
