package com.genjitsu.qrreader

import android.annotation.SuppressLint
import android.media.Image
import androidx.camera.core.CameraControl
import androidx.camera.core.ImageProxy
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timber.log.Timber

class QRReaderViewModel : ViewModel() {
    var cameraControl: CameraControl? = null
    var accuracyCounter = 0
    var tempBarcodeResult = "0"
    var _continueToRead = true
    fun onReadStateChange() {
        _continueToRead = true
    }

    private val _flashStatus = MutableLiveData(FlashStatus.DISABLED)
    var flashStatus: LiveData<FlashStatus> = _flashStatus

    fun onFlashStatusChanged() {
        when (_flashStatus.value) {
            FlashStatus.ENABLED -> {
                _flashStatus.value = FlashStatus.DISABLED
                cameraControl?.enableTorch(false)
            }
            FlashStatus.DISABLED -> {
                _flashStatus.value = FlashStatus.ENABLED
                cameraControl?.enableTorch(true)
            }
            else -> {}
        }
    }

    private val _barcodeResult = Channel<BarcodeResultEvent>()
    val barcodeResult = _barcodeResult.receiveAsFlow()


    @SuppressLint("UnsafeOptInUsageError")
    fun processImageProxy(imageProxy: ImageProxy) {
        if (imageProxy.image != null) {
            val image: Image? = imageProxy.image
            val barcodeScannerOptions = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_QR_CODE
                ).build()
            val barcodeScanner: BarcodeScanner = BarcodeScanning.getClient(barcodeScannerOptions)

            if (image == null) return
            val inputImage = InputImage.fromMediaImage(image, imageProxy.imageInfo.rotationDegrees)
            barcodeScanner.process(inputImage)
                .addOnSuccessListener { barcodes ->
                    if (!barcodes.isEmpty()) {
                        val barcode = barcodes.get(0)
                        val barcodeResult = barcode.getRawValue()
                            ?: return@addOnSuccessListener
                        if (_continueToRead) {
                            setAccurateBarcodeResult(barcodeResult)
                        }
                    }
                }.addOnFailureListener(Timber::e)
                .addOnCompleteListener { task ->
                    imageProxy.image!!.close()
                    imageProxy.close()
                }
        }
    }

    private fun setAccurateBarcodeResult(
        barcodeResult: String
    ) = viewModelScope.launch(Dispatchers.Main) {
        if (accuracyCounter >= Constants.BARCODE_ACCURACY_COUNT) {
            Timber.tag("Barcode").d(
                "consecutive reading SUCCESS...Barcode result: %s", barcodeResult
            )
            accuracyCounter = 0
            tempBarcodeResult = "0"
            _continueToRead = false
            _barcodeResult.send(BarcodeResultEvent.SuccessEvent(barcodeResult))
        } else {
            if (barcodeResult == tempBarcodeResult) {
                Timber.tag("Barcode").d(
                    "times matched: %s", accuracyCounter + 1
                )
                accuracyCounter++
            } else {
                Timber.tag("Barcode").d(
                    """
                    different data received, temp data updated ...
                    Old Temp: $tempBarcodeResult
                    """.trimIndent() + "\n"
                            + "New Temp: " + barcodeResult
                )
                tempBarcodeResult = barcodeResult
                accuracyCounter = 0
            }
        }
    }
}