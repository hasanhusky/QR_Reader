package com.genjitsu.qrreader

sealed class BarcodeResultEvent {
    data class SuccessEvent(val barcodeResult: String) : BarcodeResultEvent()
}