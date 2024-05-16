package com.cpe42020.Visimp

import NavGuidance
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
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
import com.cpe42020.Visimp.ml.SsdMobilenetV11Metadata1
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.min
import android.util.Size


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
    private var cameraCharacteristics: CameraCharacteristics? = null
    private var isSpeaking = false
    private lateinit var NavGuidance: NavGuidance
    private var frameCount = 0
    private var lastFrameTime: Long = 0
    private var fpsText = ""
    private var lastInferenceTime: Long = 0
    private var inferenceTimeText = ""
    private var performanceMetricsText = ""





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

            override fun onSurfaceTextureAvailable(p0: SurfaceTexture, width: Int, height: Int) {
                openCamera()

                val displayRotation = windowManager.defaultDisplay.rotation
                val previewSize = getOptimalPreviewSize(width, height, displayRotation)

                // Adjust the size of the TextureView to match the aspect ratio of the camera preview
                val layoutParams = textureView.layoutParams
                layoutParams.width = previewSize.width
                layoutParams.height = previewSize.height
                textureView.layoutParams = layoutParams
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
                    if (fl > 0.60) {
                        val label = labels[classes[index].toInt()]
                        if (label in listOf(
                                "chair",
                                "tv",
                                "refrigerator",
                                "potted plant",
                                "dining table",
                                "cup",
                                "person",
                                "laptop"
                            )
                        ) {
                            val boxArea =
                                (locations[index * 4 + 2] - locations[index * 4]) * (locations[index * 4 + 3] - locations[index * 4 + 1])
                            if (boxArea > largestBoxArea) {
                                largestBoxArea = boxArea.toDouble()
                                largestBoxIndex = index
                            }
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

                    val strokeWidth = 4f
                    val textSize = 200f // Set your desired text size here
                    paint.textSize = textSize
                    paint.strokeWidth = strokeWidth
                    val distance = calculateDistance(boxHeight, label)
                    calculateFps()
                    calculateAndDisplayInferenceTime()
                    drawFpsAndInferenceTimeOnCanvas(canvas)
                    if (distance<=1.5) {
                        paint.color = colors[index]
                        paint.style = Paint.Style.STROKE
                        canvas.drawRect(RectF(boxLeft, boxTop, boxRight, boxBottom), paint)
                        paint.style = Paint.Style.FILL
                        canvas.drawText(label, boxLeft, boxTop, paint)
                    }
                    val formattedDistance = String.format("%.2f", distance)

                    if (distance <= 1.8) {
                        canvas.drawText(formattedDistance, boxLeft, boxBottom, paint)
                    }
                    else {
                        canvas.drawText("", boxLeft, boxBottom, paint)
                    }

                    val obj_center_x = (boxLeft + boxRight) / 2
                    val obj_center_y = (boxTop + boxBottom) / 2

                    val camera_middle_x = resources.displayMetrics.widthPixels / 2.15
                    val camera_middle_y = resources.displayMetrics.heightPixels / 2

                    val vector_x = obj_center_x - camera_middle_x
                    val vector_y = obj_center_y - camera_middle_y

                    var angle_deg =
                        Math.toDegrees(atan2(vector_y.toDouble(), vector_x.toDouble()))

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

                    NavGuidance.speakObject("$label", distance, direction)
                }

                imageView.setImageBitmap(mutable)
            }
            private fun calculateFps() {
                val currentTime = System.currentTimeMillis()
                frameCount++
                if (lastFrameTime == 0L) {
                    lastFrameTime = currentTime
                }
                val elapsedTime = currentTime - lastFrameTime
                if (elapsedTime >= 1000) {
                    val fps = frameCount.toDouble() / (elapsedTime.toDouble() / 1000.0)
                    fpsText = "FPS: %.3f".format(fps)
                    frameCount = 0
                    lastFrameTime = currentTime
                }
            }

            private fun drawFpsAndInferenceTimeOnCanvas(canvas: Canvas) {
                // Draw FPS
                paint.textSize = 80f // Adjust text size as needed
                paint.color = Color.GREEN // Adjust color as needed
                paint.style = Paint.Style.FILL
                canvas.drawText(fpsText, 20f, 60f, paint) // Adjust position as needed

                // Draw inference time
                paint.textSize = 80f // Adjust text size as needed
                paint.color = Color.GREEN // Adjust color as needed
                paint.style = Paint.Style.FILL
                canvas.drawText(inferenceTimeText, 20f, 120f, paint) // Adjust position as needed

            }

            private fun calculateAndDisplayInferenceTime() {
                val currentTime = System.currentTimeMillis()
                if (lastInferenceTime != 0L) {
                    val inferenceTime = currentTime - lastInferenceTime
                    inferenceTimeText = "Inference Time: $inferenceTime ms"
                }
                lastInferenceTime = currentTime
            }


        }

        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        tts = TextToSpeech(this, this)
        NavGuidance = NavGuidance(this, tts)

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
        val cameraId = cameraManager.cameraIdList[0]
        cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
        val sensorOrientation = cameraCharacteristics?.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0

        cameraManager.openCamera(
            cameraId,
            object : CameraDevice.StateCallback() {
                override fun onOpened(p0: CameraDevice) {
                    cameraDevice = p0

                    val surfaceTexture = textureView.surfaceTexture
                    val surface = Surface(surfaceTexture)

                    val captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    captureRequest.addTarget(surface)

                    val displayRotation = windowManager.defaultDisplay.rotation
                    captureRequest.set(CaptureRequest.JPEG_ORIENTATION, (sensorOrientation + getOrientation(displayRotation) + 360) % 360)

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
            }, handler
        )
    }


    private fun getOrientation(rotation: Int): Int {
        return when (rotation) {
            Surface.ROTATION_0 -> 90
            Surface.ROTATION_90 -> 0
            Surface.ROTATION_180 -> 270
            Surface.ROTATION_270 -> 180
            else -> throw IllegalArgumentException("Invalid rotation value")
        }
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


    private fun calculateDistance(boxHeight: Float, label:String): Double {
        val knownObjectHeight = getKnownObjectHeight(label)
        val screenHeight = resources.displayMetrics.heightPixels
        val objectHeightInPixels = boxHeight * screenHeight

        // Calculate distance using the formula
        val distanceInMeters = (knownObjectHeight * screenHeight) / boxHeight
        return if (distanceInMeters <= 1.8) {
            distanceInMeters
        } else {
            Double.POSITIVE_INFINITY
        }
    }

    private fun getFocalLength(): Float {

        if (cameraCharacteristics == null) {

            val cameraId = cameraManager.cameraIdList[0]
            cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
        }


        return cameraCharacteristics?.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.get(0) ?: 0f
    }

    private fun getKnownObjectHeight(label: String): Double {
        return when (label) {
            "person" -> 0.70 // Average height of a person in meters
            "bicycle" -> 1.15 // Average height of a bicycle in meters
            "car" -> 1.0 // Average height of a car in meters
            "motorcycle" -> 0.80 // Average height of a motorcycle in meters
            "airplane" -> 4.0 // Average height of an airplane in meters
            "bus" -> 2.0 // Average height of a bus in meters
            "train" -> 2.0 // Average height of a train in meters
            "truck" -> 2.0 // Average height of a truck in meters
            "boat" -> 2.0 // Average height of a boat in meters
            "traffic light" -> 1.0 // Average height of a traffic light in meters
            "fire hydrant" -> 0.5 // Average height of a fire hydrant in meters
            "stop sign" -> 0.70 // Average height of a stop sign in meters
            "bench" -> 0.5 // Average height of a bench in meters
            "bird" -> 0.2 // Average height of a bird in meters
            "cat" -> 0.25 // Average height of a cat in meters
            "dog" -> 0.3 // Average height of a dog in meters
            "horse" -> 1.5 // Average height of a horse in meters
            "sheep" -> 0.8 // Average height of a sheep in meters
            "cow" -> 1.5 // Average height of a cow in meters
            "elephant" -> 3.0 // Average height of an elephant in meters
            "bear" -> 1.5 // Average height of a bear in meters
            "zebra" -> 1.3 // Average height of a zebra in meters
            "giraffe" -> 5.5 // Average height of a giraffe in meters
            "backpack" -> 0.5 // Average height of a backpack in meters
            "umbrella" -> 1.0 // Average height of an umbrella in meters
            "handbag" -> 0.3 // Average height of a handbag in meters
            "tie" -> 0.2 // Average height of a tie in meters
            "suitcase" -> 0.6 // Average height of a suitcase in meters
            "frisbee" -> 0.2 // Average height of a frisbee in meters
            "skis" -> 1.8 // Average height of skis in meters
            "snowboard" -> 1.5 // Average height of a snowboard in meters
            "sports ball" -> 0.3 // Average height of a sports ball in meters
            "kite" -> 1.0 // Average height of a kite in meters
            "baseball bat" -> 1.0 // Average height of a baseball bat in meters
            "baseball glove" -> 0.5 // Average height of a baseball glove in meters
            "skateboard" -> 0.2 // Average height of a skateboard in meters
            "surfboard" -> 2.0 // Average height of a surfboard in meters
            "tennis racket" -> 1.0 // Average height of a tennis racket in meters
            "bottle" -> 0.2 // Average height of a bottle in meters
            "wine glass" -> 0.2 // Average height of a wine glass in meters
            "cup" -> 0.1 // Average height of a cup in meters
            "fork" -> 0.2 // Average height of a fork in meters
            "knife" -> 0.2 // Average height of a knife in meters
            "spoon" -> 0.2 // Average height of a spoon in meters
            "bowl" -> 0.1 // Average height of a bowl in meters
            "banana" -> 0.2 // Average height of a banana in meters
            "apple" -> 0.1 // Average height of an apple in meters
            "sandwich" -> 0.1 // Average height of a sandwich in meters
            "orange" -> 0.1 // Average height of an orange in meters
            "broccoli" -> 0.2 // Average height of broccoli in meters
            "carrot" -> 0.2 // Average height of a carrot in meters
            "hot dog" -> 0.1 // Average height of a hot dog in meters
            "pizza" -> 0.2 // Average height of a pizza in meters
            "donut" -> 0.1 // Average height of a donut in meters
            "cake" -> 0.2 // Average height of a cake in meters
            "chair" -> 0.55 // Average height of a chair in meters
            "couch" -> 0.8 // Average height of a couch in meters
            "potted plant" -> 0.4 // Average height of a potted plant in meters
            "bed" -> 0.5 // Average height of a bed in meters
            "dining table" -> 0.65 // Average height of a dining table in meters
            "toilet" -> 0.4 // Average height of a toilet in meters
            "tv" -> 0.55 // Average height of a TV in meters
            "laptop" -> 0.3 // Average height of a laptop in meters
            "mouse" -> 0.05 // Average height of a computer mouse in meters
            "remote" -> 0.2 // Average height of a remote control in meters
            "keyboard" -> 0.05 // Average height of a keyboard in meters
            "cell phone" -> 0.15 // Average height of a cell phone in meters
            "microwave" -> 0.3 // Average height of a microwave in meters
            "oven" -> 0.8 // Average height of an oven in meters
            "toaster" -> 0.2 // Average height of a toaster in meters
            "sink" -> 0.7 // Average height of a sink in meters
            "refrigerator" -> 0.75 // Average height of a refrigerator in meters
            "book" -> 0.1 // Average height of a book in meters
            "clock" -> 0.3 // Average height of a clock in meters
            "vase" -> 0.3 // Average height of a vase in meters
            "scissors" -> 0.2 // Average height of scissors in meters
            "teddy bear" -> 0.3 // Average height of a teddy bear in meters
            "hair drier" -> 0.2 // Average height of a hair drier in meters
            "toothbrush" -> 0.2 // Average height of a toothbrush in meters
            "door" -> 1.0
            else -> 0.2 // Default value for other classes
        }
    }
    private fun calculateAspectRatio(width: Int, height: Int): Float {
        // Calculate the aspect ratio
        return height.toFloat() / width.toFloat()
    }

    private fun getOptimalPreviewSize(aspectRatio: Int, height: Int, displayRotation: Int): Size {
        // Get the available preview sizes from the camera characteristics
        val cameraId = cameraManager.cameraIdList[0]
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val streamConfigurationMap =
            characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?: throw RuntimeException("Cannot get available preview sizes")

        // Find the optimal preview size with the closest aspect ratio to the camera preview aspect ratio
        val previewSizes = streamConfigurationMap.getOutputSizes(SurfaceTexture::class.java)
        var optimalSize = Size(0, 0)
        var minAspectRatioDiff = Float.MAX_VALUE

        for (size in previewSizes) {
            val sizeAspectRatio = size.width.toFloat() / size.height.toFloat()
            val aspectRatioDiff = Math.abs(aspectRatio - sizeAspectRatio)
            if (aspectRatioDiff < minAspectRatioDiff) {
                minAspectRatioDiff = aspectRatioDiff
                optimalSize = size
            }
        }

        return optimalSize
    }
}


