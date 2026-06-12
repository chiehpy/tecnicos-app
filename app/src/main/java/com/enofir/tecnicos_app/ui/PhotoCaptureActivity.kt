package com.enofir.tecnicos_app.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.enofir.tecnicos_app.R
import com.enofir.tecnicos_app.core.ApiClient
import com.enofir.tecnicos_app.model.PhotoUploadResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class PhotoCaptureActivity : AppCompatActivity(), TextureView.SurfaceTextureListener {

    companion object {
        const val EXTRA_SERIAL = "photo_serial"
        private const val REQ_CAMERA = 801
    }

    private enum class FlashMode { OFF, AUTO, ON }

    private lateinit var texturePreview: TextureView
    private lateinit var captureGroup: View
    private lateinit var reviewGroup: View
    private lateinit var ivCapture: ImageView
    private lateinit var pbUploading: ProgressBar
    private lateinit var tvUploadStatus: TextView
    private lateinit var btnFlash: Button
    private lateinit var btnShutter: Button
    private lateinit var btnSwitchCamera: Button
    private lateinit var btnRetake: Button
    private lateinit var btnConfirm: Button

    private var cameraManager: CameraManager? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var cameraId: String = ""
    private var lensFacing = CameraCharacteristics.LENS_FACING_BACK
    private var flashMode = FlashMode.OFF
    private var capturedBytes: ByteArray? = null
    private var serial = ""
    private var captureSize = Size(1280, 960)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_capture)
        serial = intent.getStringExtra(EXTRA_SERIAL) ?: ""
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        bindViews()
        checkCameraPermission()
    }

    private fun bindViews() {
        texturePreview = findViewById(R.id.texturePreview)
        captureGroup = findViewById(R.id.captureGroup)
        reviewGroup = findViewById(R.id.reviewGroup)
        ivCapture = findViewById(R.id.ivCapture)
        pbUploading = findViewById(R.id.pbUploading)
        tvUploadStatus = findViewById(R.id.tvUploadStatus)
        btnFlash = findViewById(R.id.btnFlash)
        btnShutter = findViewById(R.id.btnShutter)
        btnSwitchCamera = findViewById(R.id.btnSwitchCamera)
        btnRetake = findViewById(R.id.btnRetake)
        btnConfirm = findViewById(R.id.btnConfirm)

        btnFlash.setOnClickListener { toggleFlash() }
        btnSwitchCamera.setOnClickListener { switchCamera() }
        btnShutter.setOnClickListener { capturePhoto() }
        btnRetake.setOnClickListener { retakePhoto() }
        btnConfirm.setOnClickListener { confirmAndUpload() }
        updateFlashButton()
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            initTextureView()
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CAMERA), REQ_CAMERA
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CAMERA && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            initTextureView()
        } else {
            Toast.makeText(this, "Se requiere permiso de cámara", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun initTextureView() {
        if (texturePreview.isAvailable) openCamera()
        else texturePreview.surfaceTextureListener = this
    }

    // ── TextureView.SurfaceTextureListener ──────────────────────────────────

    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) { openCamera() }
    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = true
    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}

    // ── Camera2 ─────────────────────────────────────────────────────────────

    private fun openCamera() {
        val mgr = cameraManager ?: return
        try {
            cameraId = mgr.cameraIdList.firstOrNull { id ->
                mgr.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING) == lensFacing
            } ?: mgr.cameraIdList.firstOrNull() ?: return

            val characteristics = mgr.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!

            val jpegSizes = map.getOutputSizes(ImageFormat.JPEG)
            captureSize = jpegSizes
                .filter { it.width * it.height <= 2_000_000 }
                .maxByOrNull { it.width * it.height }
                ?: jpegSizes.minByOrNull { it.width * it.height }
                ?: Size(1280, 960)

            imageReader = ImageReader
                .newInstance(captureSize.width, captureSize.height, ImageFormat.JPEG, 2)
                .also { it.setOnImageAvailableListener(onImageAvailable, backgroundHandler) }

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED
            ) return

            mgr.openCamera(cameraId, cameraStateCallback, backgroundHandler)

        } catch (e: CameraAccessException) {
            toast("Error al abrir cámara: ${e.message}")
            finish()
        }
    }

    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            createPreviewSession()
        }
        override fun onDisconnected(camera: CameraDevice) {
            camera.close(); cameraDevice = null
        }
        override fun onError(camera: CameraDevice, error: Int) {
            camera.close(); cameraDevice = null
            runOnUiThread { toast("Error de cámara ($error)") }
        }
    }

    private fun createPreviewSession() {
        val device = cameraDevice ?: return
        val reader = imageReader ?: return
        val st = texturePreview.surfaceTexture ?: return
        st.setDefaultBufferSize(captureSize.width, captureSize.height)
        val previewSurface = Surface(st)

        try {
            val previewRequest = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(previewSurface)
                applyFlash(this)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            }

            @Suppress("DEPRECATION")
            device.createCaptureSession(
                listOf(previewSurface, reader.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        try {
                            session.setRepeatingRequest(previewRequest.build(), null, backgroundHandler)
                        } catch (_: CameraAccessException) {}
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        runOnUiThread { toast("Error al configurar la cámara") }
                    }
                },
                backgroundHandler
            )
        } catch (e: CameraAccessException) {
            runOnUiThread { toast("Error de cámara: ${e.message}") }
        }
    }

    private fun capturePhoto() {
        val device = cameraDevice ?: return
        val reader = imageReader ?: return
        val session = captureSession ?: return
        btnShutter.isEnabled = false

        try {
            val captureRequest = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(reader.surface)
                applyFlash(this)
                set(CaptureRequest.JPEG_QUALITY, 80.toByte())
                set(CaptureRequest.JPEG_ORIENTATION, getJpegOrientation())
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            }
            session.stopRepeating()
            session.capture(captureRequest.build(), null, backgroundHandler)
        } catch (e: CameraAccessException) {
            runOnUiThread { btnShutter.isEnabled = true; toast("Error al capturar: ${e.message}") }
        }
    }

    private val onImageAvailable = ImageReader.OnImageAvailableListener { reader ->
        val image = reader.acquireLatestImage() ?: return@OnImageAvailableListener
        try {
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            capturedBytes = bytes
            runOnUiThread { showReview(bytes) }
        } finally {
            image.close()
        }
    }

    private fun showReview(bytes: ByteArray) {
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ivCapture.setImageBitmap(bmp)
        captureGroup.visibility = View.GONE
        reviewGroup.visibility = View.VISIBLE
        tvUploadStatus.text = ""
    }

    private fun retakePhoto() {
        capturedBytes = null
        reviewGroup.visibility = View.GONE
        captureGroup.visibility = View.VISIBLE
        btnShutter.isEnabled = true
        createPreviewSession()
    }

    private fun confirmAndUpload() {
        val bytes = capturedBytes ?: return
        btnConfirm.isEnabled = false
        btnRetake.isEnabled = false
        pbUploading.visibility = View.VISIBLE
        tvUploadStatus.text = "Subiendo foto..."

        val base64Str = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val filename = "foto_${serial}_${System.currentTimeMillis()}.jpg"

        ApiClient.uploadPhoto(serial, base64Str, filename)
            .enqueue(object : Callback<PhotoUploadResponse> {
                override fun onResponse(
                    call: Call<PhotoUploadResponse>,
                    response: Response<PhotoUploadResponse>
                ) {
                    runOnUiThread {
                        pbUploading.visibility = View.GONE
                        val body = response.body()
                        if (response.isSuccessful && body?.ok == true) {
                            toast("Foto subida correctamente")
                            setResult(Activity.RESULT_OK)
                            finish()
                        } else {
                            val msg = body?.message ?: "Error al subir (${response.code()})"
                            tvUploadStatus.text = msg
                            btnConfirm.isEnabled = true
                            btnRetake.isEnabled = true
                        }
                    }
                }
                override fun onFailure(call: Call<PhotoUploadResponse>, t: Throwable) {
                    runOnUiThread {
                        pbUploading.visibility = View.GONE
                        tvUploadStatus.text = "Error de red: ${t.message}"
                        btnConfirm.isEnabled = true
                        btnRetake.isEnabled = true
                    }
                }
            })
    }

    // ── Camera helpers ───────────────────────────────────────────────────────

    private fun switchCamera() {
        lensFacing = if (lensFacing == CameraCharacteristics.LENS_FACING_BACK)
            CameraCharacteristics.LENS_FACING_FRONT
        else
            CameraCharacteristics.LENS_FACING_BACK
        closeCamera()
        openCamera()
    }

    private fun toggleFlash() {
        flashMode = when (flashMode) {
            FlashMode.OFF -> FlashMode.AUTO
            FlashMode.AUTO -> FlashMode.ON
            FlashMode.ON -> FlashMode.OFF
        }
        updateFlashButton()
        restartPreview()
    }

    private fun updateFlashButton() {
        btnFlash.text = when (flashMode) {
            FlashMode.OFF -> "Flash: OFF"
            FlashMode.AUTO -> "Flash: AUTO"
            FlashMode.ON -> "Flash: ON"
        }
    }

    private fun restartPreview() {
        val session = captureSession ?: return
        val device = cameraDevice ?: return
        val st = texturePreview.surfaceTexture ?: return
        try {
            val previewRequest = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(Surface(st))
                applyFlash(this)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            }
            session.setRepeatingRequest(previewRequest.build(), null, backgroundHandler)
        } catch (_: CameraAccessException) {}
    }

    private fun applyFlash(builder: CaptureRequest.Builder) {
        when (flashMode) {
            FlashMode.OFF -> {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            }
            FlashMode.AUTO ->
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
            FlashMode.ON ->
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH)
        }
    }

    private fun getJpegOrientation(): Int {
        val mgr = cameraManager ?: return 0
        return try {
            val sensorOrientation =
                mgr.getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            @Suppress("DEPRECATION")
            val deviceDegrees = when (windowManager.defaultDisplay.rotation) {
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                else -> 0
            }
            if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT)
                (sensorOrientation + deviceDegrees) % 360
            else
                (sensorOrientation - deviceDegrees + 360) % 360
        } catch (_: CameraAccessException) { 0 }
    }

    private fun closeCamera() {
        captureSession?.close(); captureSession = null
        cameraDevice?.close(); cameraDevice = null
        imageReader?.close(); imageReader = null
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            if (texturePreview.isAvailable) openCamera()
            else texturePreview.surfaceTextureListener = this
        }
    }

    override fun onPause() {
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBg").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try { backgroundThread?.join() } catch (_: InterruptedException) {}
        backgroundThread = null
        backgroundHandler = null
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
