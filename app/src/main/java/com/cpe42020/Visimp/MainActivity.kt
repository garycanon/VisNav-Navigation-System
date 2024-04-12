package com.cpe42020.Visimp

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import android.os.Build
import com.cpe42020.Visimp.ml.SsdMobilenetV11Metadata1
import java.util.*
import android.hardware.camera2.CameraCharacteristics
import kotlin.math.*
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint



class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var labels: List<String>
    private var colors = listOf<Int>(
        Color.BLUE, Color.GREEN, Color.RED, Color.CYAN, Color.GRAY, Color.BLACK,
        Color.DKGRAY, Color.MAGENTA, Color.YELLOW, Color.RED
    )
    private val paint = Paint()
    private lateinit var imageProcessor: ImageProcessor
    private lateinit var bitmap: Bitmap
    private lateinit var imageView: ImageView
    private lateinit var cameraDevice: CameraDevice
    private lateinit var handler: Handler
    private lateinit var cameraManager: CameraManager
    private lateinit var textureView: TextureView
    private lateinit var model: SsdMobilenetV11Metadata1
    private lateinit var tts: TextToSpeech
    private var lastSpokenObject: String? = null
    private var cameraCharacteristics: CameraCharacteristics? = null
    private var lastSpokenTimestamp: Long = 0



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        getPermission()

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val densityDpi = displayMetrics.densityDpi
        val mmwidth = screenWidth/(densityDpi/25.4)
        val mmheight = screenHeight/(densityDpi/25.4)
        val centerX = (mmwidth / 2)
        val centerY = (mmheight / 2)
        val radius = min(centerX, centerY) - 30 // Adjust as needed


        labels = FileUtil.loadLabels(this, "labels.txt")
        imageProcessor =
            ImageProcessor.Builder().add(ResizeOp(300, 300, ResizeOp.ResizeMethod.BILINEAR)).build()
        model = SsdMobilenetV11Metadata1.newInstance(this)
        val handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        imageView = findViewById(R.id.imageView)

        textureView = findViewById(R.id.textureView)
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(p0: SurfaceTexture, p1: Int, p2: Int) {
                openCamera()
            }

            override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {}

            override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
                return false
            }

            override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {
                bitmap = textureView.bitmap!!
                var image = TensorImage.fromBitmap(bitmap)
                image = imageProcessor.process(image)

                val outputs = model.process(image)
                val locations = outputs.locationsAsTensorBuffer.floatArray
                val classes = outputs.classesAsTensorBuffer.floatArray
                val scores = outputs.scoresAsTensorBuffer.floatArray

                val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(mutable)

                val h = mutable.height
                val w = mutable.width
                paint.textSize = h / 15f
                paint.strokeWidth = h / 85f

                var largestBoxIndex = -1
                var largestBoxArea = 0.0

                scores.forEachIndexed { index, fl ->
                    if (fl > 0.65) {
                        //val label = labels[classes[index].toInt()]
                        val boxArea =
                            (locations[index * 4 + 2] - locations[index * 4]) * (locations[index * 4 + 3] - locations[index * 4 + 1])
                        if (boxArea > largestBoxArea) {
                            largestBoxArea = boxArea.toDouble()
                            largestBoxIndex = index
                        }
                    }
                }

                if (largestBoxIndex != -1) {
                    val index = largestBoxIndex
                    val label = labels[classes[index].toInt()]

                    val boxLeft = locations[index * 4 + 1] * w
                    val boxTop = locations[index * 4] * h
                    val boxRight = locations[index * 4 + 3] * w
                    val boxBottom = locations[index * 4 + 2] * h
                    val boxHeight = (boxBottom - boxTop)
                    val boxWidth = (boxLeft - boxRight)
                    val sHeight = resources.displayMetrics.heightPixels

                    val distance = calculateDistance(boxHeight)
                    paint.color = colors[index]
                    paint.style = Paint.Style.STROKE
                    canvas.drawRect(RectF(boxLeft, boxTop, boxRight, boxBottom), paint)
                    paint.style = Paint.Style.FILL
                    canvas.drawText(label, boxLeft, boxTop, paint)

                    val obj_center_x = (boxLeft + boxRight) / 2
                    val obj_center_y = (boxTop + boxBottom) / 2

                    val camera_middle_x = resources.displayMetrics.widthPixels / 2.15
                    val camera_middle_y = resources.displayMetrics.heightPixels / 2

                    val vector_x = obj_center_x - camera_middle_x
                    val vector_y = obj_center_y - camera_middle_y

                    var angle_deg = Math.toDegrees(atan2(vector_y.toDouble(), vector_x.toDouble()))

                    if (angle_deg < 0) {
                        angle_deg += 360
                    }

                    val direction: String = when {
                        0.0 <= angle_deg && angle_deg < 30.0 -> "middle right"
                        30.0 <= angle_deg && angle_deg < 60.0 -> "bottom right"
                        60.0 <= angle_deg && angle_deg < 89.9 -> "bottom right"
                        90.0 <= angle_deg && angle_deg < 120.0 -> "bottom middle"
                        120.0 <= angle_deg && angle_deg < 150.0 -> "bottom left"
                        150.0 <= angle_deg && angle_deg < 179.9 -> "bottom left"
                        180.0 <= angle_deg && angle_deg < 210.0 -> "middle left"
                        210.0 <= angle_deg && angle_deg < 240.0 -> "top left"
                        240.0 <= angle_deg && angle_deg < 269.9 -> "top left"
                        270.0 <= angle_deg && angle_deg < 300.0 -> "top middle"
                        300.0 <= angle_deg && angle_deg < 330.0 -> "top right"
                        330.0 <= angle_deg && angle_deg < 360.0 -> "top right"
                        else -> "center"
                    }

                    speakObject("$label",distance, direction )
                }

                imageView.setImageBitmap(mutable)
            }
        }
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        tts = TextToSpeech(this, this)
    }

    override fun onDestroy() {
        super.onDestroy()
        model.close()
        if (tts.isSpeaking) {
            tts.stop()
        }
        tts.shutdown()
    }

    @SuppressLint("MissingPermission")
    private fun openCamera() {
        cameraManager.openCamera(
            cameraManager.cameraIdList[0],
            object : CameraDevice.StateCallback() {
                override fun onOpened(p0: CameraDevice) {
                    cameraDevice = p0

                    val surfaceTexture = textureView.surfaceTexture
                    val surface = Surface(surfaceTexture)

                    val captureRequest =
                        cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    captureRequest.addTarget(surface)

                    cameraDevice.createCaptureSession(listOf(surface),
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(p0: CameraCaptureSession) {
                                p0.setRepeatingRequest(captureRequest.build(), null, null)
                            }

                            override fun onConfigureFailed(p0: CameraCaptureSession) {}
                        }, handler
                    )
                }

                override fun onDisconnected(p0: CameraDevice) {}

                override fun onError(p0: CameraDevice, p1: Int) {}
            },
            handler
        )
    }

    private fun getPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 101)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            getPermission()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                Log.e("TTS", "Language not supported")
            }
        } else {
            Log.e("TTS", "Initialization failed")
        }
    }

    private fun speak(text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        } else {
            val params = HashMap<String, String>()
            params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = "stringId"
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params)
        }
    }

    private fun speakObject(objectName: String, distance: Double, direction: String) {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastSpoken = currentTime - lastSpokenTimestamp

        // Check if the detected object is different from the last spoken object or if enough time has passed since the last spoken object
        if (objectName != lastSpokenObject || timeSinceLastSpoken > 8000) { // Adjust the delay time (in milliseconds) as needed
            // Speak the detected object
            tts.speak("$objectName at ${String.format("%.2f", distance)} meters and at the $direction", TextToSpeech.QUEUE_FLUSH, null, null)

            // Update the last spoken object and timestamp
            lastSpokenObject = objectName
            lastSpokenTimestamp = currentTime
        }
    }
    private fun calculateDistance(boxHeight: Float): Double {

        val knownObjectHeight = 0.15
        val screenHeight = resources.displayMetrics.heightPixels
        val focalLength = getFocalLength()
        val distanceinMeters =  (knownObjectHeight * screenHeight) / boxHeight

        return distanceinMeters


    }
    private fun getFocalLength(): Float {

        if (cameraCharacteristics == null) {

            val cameraId = cameraManager.cameraIdList[0]
            cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
        }


        return cameraCharacteristics?.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.get(0) ?: 0f
    }


}

