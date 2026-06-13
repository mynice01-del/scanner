package com.example.ocr

import android.media.Image
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class AssetOcrAnalyzer(
    private val onAssetTagDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val barcodeScanner = BarcodeScanning.getClient()
    
    // Pattern to match 9 or 10 digit asset numbers
    private val assetTagPattern = Regex("(?i)(CM|CZ|OM|OD|OP|ON|OE)\\d{9,10}")

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage: Image? = imageProxy.image
        if (mediaImage != null) {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)
            
            val width = inputImage.width
            val height = inputImage.height

            // 1. Try Barcode Scanning first because it's 100% precise and prevents OCR character errors
            barcodeScanner.process(inputImage)
                .addOnSuccessListener { barcodes ->
                    var barcodeFound = false
                    for (barcode in barcodes) {
                        val rawValue = barcode.rawValue?.trim() ?: continue
                        val cleanedCode = rawValue.replace(" ", "").replace("-", "").uppercase()
                        
                        val match = assetTagPattern.find(cleanedCode)
                        if (match != null) {
                            val tag = match.value.uppercase()
                            
                            val rect = barcode.boundingBox
                            if (rect != null) {
                                val centerX = rect.centerX()
                                val centerY = rect.centerY()
                                // Central 70% bounds of horizontal/vertical viewport
                                val isNearCenter = centerX in (width * 0.15).toInt()..(width * 0.85).toInt() &&
                                                   centerY in (height * 0.15).toInt()..(height * 0.85).toInt()
                                if (isNearCenter) {
                                    onAssetTagDetected(tag)
                                    barcodeFound = true
                                    break
                                }
                            } else {
                                onAssetTagDetected(tag)
                                barcodeFound = true
                                break
                            }
                        }
                    }

                    // 2. If no valid barcode matching our spec was found near the center, fall back to OCR text recognition
                    if (!barcodeFound) {
                        textRecognizer.process(inputImage)
                            .addOnSuccessListener { visionText ->
                                for (block in visionText.textBlocks) {
                                    for (line in block.lines) {
                                        val text = line.text.trim().replace(" ", "").replace("-", "")
                                        
                                        val match = assetTagPattern.find(text)
                                        if (match != null) {
                                            val tag = match.value.uppercase()
                                            
                                            // Check bounding box to verify text is inside the centered viewfinder.
                                            // This drastically reduces false positives from surroundings or background numbers.
                                            val rect = line.boundingBox
                                            if (rect != null) {
                                                val centerX = rect.centerX()
                                                val centerY = rect.centerY()
                                                // Viewfinder boundaries: middle 50% width & 50% height
                                                val isNearCenter = centerX in (width * 0.25).toInt()..(width * 0.75).toInt() &&
                                                                   centerY in (height * 0.25).toInt()..(height * 0.75).toInt()
                                                if (isNearCenter) {
                                                    onAssetTagDetected(tag)
                                                }
                                            } else {
                                                onAssetTagDetected(tag)
                                            }
                                        }
                                    }
                                }
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    } else {
                        imageProxy.close()
                    }
                }
                .addOnFailureListener {
                    // Fall back to text recognition in case barcode scanner initialization fails
                    textRecognizer.process(inputImage)
                        .addOnSuccessListener { visionText ->
                            for (block in visionText.textBlocks) {
                                    for (line in block.lines) {
                                        val text = line.text.trim().replace(" ", "").replace("-", "")
                                        val match = assetTagPattern.find(text)
                                        if (match != null) {
                                            val tag = match.value.uppercase()
                                            val rect = line.boundingBox
                                            if (rect != null) {
                                                val centerX = rect.centerX()
                                                val centerY = rect.centerY()
                                                val isNearCenter = centerX in (width * 0.25).toInt()..(width * 0.75).toInt() &&
                                                                   centerY in (height * 0.25).toInt()..(height * 0.75).toInt()
                                                if (isNearCenter) {
                                                    onAssetTagDetected(tag)
                                                }
                                            } else {
                                                onAssetTagDetected(tag)
                                            }
                                        }
                                    }
                            }
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                }
        } else {
            imageProxy.close()
        }
    }
}
