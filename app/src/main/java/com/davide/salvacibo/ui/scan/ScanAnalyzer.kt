package com.davide.salvacibo.ui.scan

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer
import java.util.concurrent.atomic.AtomicReference

enum class ScanMode { BARCODE, DATE, IDLE }

/**
 * Analizza i fotogrammi della fotocamera in tempo reale (tutto sul telefono, offline):
 *  - in modalità BARCODE cerca un codice a barre EAN/UPC
 *  - in modalità DATE legge il testo dell'etichetta (OCR)
 */
class ScanAnalyzer(
    private val mode: AtomicReference<ScanMode>,
    private val barcodeScanner: BarcodeScanner,
    private val textRecognizer: TextRecognizer,
    private val onBarcode: (code: String, frame: Bitmap?) -> Unit,
    private val onText: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        val current = mode.get()
        if (mediaImage == null || current == ScanMode.IDLE) {
            imageProxy.close()
            return
        }
        val rotation = imageProxy.imageInfo.rotationDegrees
        val input = InputImage.fromMediaImage(mediaImage, rotation)

        try {
            process(current, input, imageProxy, rotation)
        } catch (e: Exception) {
            // es. riconoscitore già chiuso mentre si esce dalla schermata
            imageProxy.close()
        }
    }

    private fun process(current: ScanMode, input: InputImage, imageProxy: ImageProxy, rotation: Int) {
        if (current == ScanMode.BARCODE) {
            barcodeScanner.process(input).addOnCompleteListener { task ->
                try {
                    val code = if (task.isSuccessful) {
                        task.result.firstNotNullOfOrNull { b -> b.rawValue?.takeIf { it.length >= 8 } }
                    } else null
                    if (code != null && mode.get() == ScanMode.BARCODE) {
                        // tengo il fotogramma come foto del prodotto
                        val frame = runCatching { imageProxy.toBitmap().rotated(rotation) }.getOrNull()
                        onBarcode(code, frame)
                    }
                } finally {
                    imageProxy.close()
                }
            }
        } else {
            textRecognizer.process(input).addOnCompleteListener { task ->
                try {
                    if (task.isSuccessful && mode.get() == ScanMode.DATE) onText(task.result.text)
                } finally {
                    imageProxy.close()
                }
            }
        }
    }

    private fun Bitmap.rotated(degrees: Int): Bitmap =
        if (degrees == 0) this
        else Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
}
