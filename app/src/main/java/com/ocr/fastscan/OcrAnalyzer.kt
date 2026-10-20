package com.ocr.fastscan

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.regex.Pattern

class OcrAnalyzer(
    private val mode: String,
    private val onResult: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val resultStabilizer = ScanResultStabilizer()

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        if (mode == "weight") {
            // Вес теперь приходит с сервера — ничего не делаем здесь
            imageProxy.close()
            return
        }

        // Режим KG — сканирование кода клиента
        if (mode == "kg") {
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val rawText = visionText.text.uppercase()
                    val pattern = Pattern.compile("KG\\s*([0-9OQDILZSBG]{4,6})")
                    val matcher = pattern.matcher(rawText)
                    if (matcher.find()) {
                        val digits = matcher.group(1)
                            ?.map { character ->
                                when (character) {
                                    'O', 'Q', 'D' -> '0'
                                    'I', 'L' -> '1'
                                    'Z' -> '2'
                                    'S' -> '5'
                                    'G' -> '6'
                                    'B' -> '8'
                                    else -> character
                                }
                            }
                            ?.joinToString("")
                            .orEmpty()

                        if (digits.length in 4..6 && digits.all(Char::isDigit)) {
                            resultStabilizer.add("KG$digits")?.let(onResult)
                        }
                    }
                }
                .addOnCompleteListener { imageProxy.close() }
        } else {
            imageProxy.close()
        }
    }
}
