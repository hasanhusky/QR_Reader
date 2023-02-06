package com.genjitsu.qrreader

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Size
import android.view.*
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.android.synthetic.main.dialog_fragment_qr_reader.*
import kotlinx.android.synthetic.main.dialog_fragment_qr_reader.view.*
import timber.log.Timber
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit


class QRReaderActivity : DialogFragment(R.layout.dialog_fragment_qr_reader) {

    private val viewModel: QRReaderViewModel by viewModels()

    private var cameraSelector: CameraSelector? = null
    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null

    private var qrReaderDialogDismissListener: ((Boolean) -> Unit)? = null
    fun setQrReaderDialogDismissListener(listener: (Boolean) -> Unit) {
        qrReaderDialogDismissListener = listener
    }

    private var qrReadSuccessListener: ((String) -> Unit)? = null
    fun setQrReadSuccessListener(listener: (String) -> Unit) {
        qrReadSuccessListener = listener
    }

    private var isAnimationFinished = true
    var focusAnimation: ObjectAnimator? = null

    private lateinit var rootView: View


    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
//        setContentView(R.layout.dialog_fragment_qr_reader)
        rootView = inflater.inflate(R.layout.dialog_fragment_qr_reader, container, false)
        initAnimation()

        viewModel.onReadStateChange()

        subscribeToObservers()
        cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture?.addListener(Runnable {
            try {
                val cameraProvider =
                    cameraProviderFuture?.get()
                cameraProvider?.let {
                    bindImageAnalysis(it)
                }
            } catch (e: ExecutionException) {
                e.printStackTrace()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(requireContext()))


        rootView.btnBarcodeReaderBackButton.setOnClickListener {
            dismiss()
            sendQrReaderDialogDismissedBroadcast()
        }

        rootView.btnFlash.setOnClickListener {
            viewModel.onFlashStatusChanged()
        }

        rootView.previewView.setOnTouchListener { v: View?, event: MotionEvent ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> return@setOnTouchListener true
                MotionEvent.ACTION_UP -> {
                    setViewLocation(event.x, event.y)
                    fadeOutFocusAnimation()
                    isAnimationFinished = false

                    if (cameraSelector == null) return@setOnTouchListener true
                    val factory: MeteringPointFactory =
                        rootView.previewView.createMeteringPointFactory(cameraSelector!!)
                    val point = factory.createPoint(event.x, event.y)
                    val action =
                        FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                            .setAutoCancelDuration(5, TimeUnit.SECONDS)
                            .build()
                    viewModel.cameraControl?.startFocusAndMetering(action)
                    return@setOnTouchListener true
                }
                else -> {
                    Timber.d("other action")
                    return@setOnTouchListener false
                }
            }
        }

        return rootView
    }


    fun subscribeToObservers() {
        viewModel.flashStatus.observe(viewLifecycleOwner) { result ->
            result?.let {
                when (result) {
                    FlashStatus.ENABLED -> {
                        rootView.btnFlash.setBackgroundResource(R.drawable.baseline_flash_on_24);
                    }
                    FlashStatus.DISABLED -> {
                        rootView.btnFlash.setBackgroundResource(R.drawable.baseline_flash_off_24);
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.barcodeResult.collect { event ->
                when (event) {
                    is BarcodeResultEvent.SuccessEvent -> {
                        viewModel.onReadStateChange()
                        sendQrReaderSuccessBroadcast(event.barcodeResult)
                    }
                }
            }
        }
    }


    private fun bindImageAnalysis(cameraProvider: ProcessCameraProvider) {
        val imageAnalysisUseCase = ImageAnalysis.Builder()
            .setTargetResolution(
                Size(
                    Constants.CAMERA_RESOLUTION_WIDTH,
                    Constants.CAMERA_RESOLUTION_HEIGHT
                )
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        imageAnalysisUseCase.setAnalyzer(
            ContextCompat.getMainExecutor(requireContext())
        ) { imageProxy: ImageProxy? ->
            imageProxy?.let {
                viewModel.processImageProxy(
                    it
                )
            }
        }
        val orientationEventListener: OrientationEventListener =
            object : OrientationEventListener(activity) {
                override fun onOrientationChanged(orientation: Int) {
                    //textView.setText(Integer.toString(orientation));
                }
            }
        orientationEventListener.enable()
        val preview = Preview.Builder().build()
        cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()
        preview.setSurfaceProvider(rootView.previewView.createSurfaceProvider())
        val camera: Camera = cameraProvider.bindToLifecycle(
            (this as LifecycleOwner), cameraSelector!!,
            imageAnalysisUseCase, preview
        )
        viewModel.cameraControl = camera.getCameraControl()
    }


    private fun sendQrReaderDialogDismissedBroadcast() {
        qrReaderDialogDismissListener?.let { dialogDismiss ->
            dialogDismiss(true)
        }
    }

    private fun sendQrReaderSuccessBroadcast(result: String) {
        qrReadSuccessListener?.let { qrReaderSuccess ->
            qrReaderSuccess(result)
        }
    }

    fun setViewLocation(x: Float, y: Float) {
        val width = focusRing.getWidth()
        val height = focusRing.getHeight()
        focusRing.setX(x - width / 2)
        focusRing.setY(y - height / 2)
    }

    fun initAnimation() {
        focusAnimation = ObjectAnimator.ofPropertyValuesHolder(
            focusRing,
            PropertyValuesHolder.ofFloat("scaleX", 0.7f),
            PropertyValuesHolder.ofFloat("scaleY", 0.7f)
        )
        focusAnimation?.setDuration(500)
        focusAnimation?.setRepeatCount(ValueAnimator.RESTART)
        focusAnimation?.setRepeatMode(ValueAnimator.REVERSE)
    }

    fun fadeOutFocusAnimation() {
        focusRing.setVisibility(View.VISIBLE)
        focusRing.setAlpha(1f)
        if (!isAnimationFinished) {
            return
        }
        focusAnimation?.start()
        focusRing.animate()
            .setStartDelay(1000)
            .setDuration(500)
            .alpha(0f)
            .setListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {
                }

                override fun onAnimationEnd(animator: Animator) {
                    focusRing.setVisibility(View.INVISIBLE)
                    isAnimationFinished = true
                }

                override fun onAnimationCancel(animation: Animator) {}
                override fun onAnimationRepeat(animation: Animator) {}
            })
    }

}