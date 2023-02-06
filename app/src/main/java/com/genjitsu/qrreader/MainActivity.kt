package com.genjitsu.qrreader

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber

class MainActivity : AppCompatActivity() {
    var qrReaderDialog: QRReaderActivity? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Timber.plant(Timber.DebugTree())

        val tvQRResult = findViewById<TextView>(R.id.tvQRResult)

        btnStartQRReader.setOnClickListener {
            qrReaderDialog = QRReaderActivity()
            qrReaderDialog?.apply {
                setQrReaderDialogDismissListener { dialogDismiss ->
                    if (dialogDismiss) {
                        println("qr reading aborted")
                    }
                }
                setQrReadSuccessListener { result ->
                    qrReaderDialog?.dismiss()
                    tvQRResult.text = result
                    println("QR RESULT ---------- " + result)
                }
            }?.show(supportFragmentManager, "TAG")
        }

    }
}