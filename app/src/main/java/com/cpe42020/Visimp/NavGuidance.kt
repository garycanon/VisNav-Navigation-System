import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import com.cpe42020.Visimp.R

class NavGuidance(private val context: Context, private val tts: TextToSpeech) {

    private var lastSpokenObject: String? = null
    private var lastSpokenTimestamp: Long = 0
    private var lastSpokenDistance: Double = Double.MAX_VALUE
    private var lastSpokenInstruction: String? = null
    private var lastSpokenInstructionTimestamp: Long = 0
    private val instructionCooldown = 3000 // Cooldown period in milliseconds
    private val alarmDistance = 0.4 // Distance threshold to trigger alarm and vibration (in meters)
    private var vibrator: Vibrator? = null
    private var mediaPlayer: MediaPlayer? = null

    private var left: String? = null
    private var right: String? = null

    init {
        vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
        mediaPlayer = MediaPlayer.create(context, R.raw.alarm_sound) // Assuming you have an alarm sound file named alarm_sound in the raw folder
    }

    fun speakObject(objectName: String, distance: Double, direction: String) {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastSpoken = currentTime - lastSpokenTimestamp

        // Check if the current object is closer than the previously spoken object
        val closerObject = lastSpokenObject == null || distance < lastSpokenDistance

        if (closerObject || timeSinceLastSpoken > instructionCooldown) {
            val directionLower = direction.lowercase()
            val instruction: String

            if (distance <= 1.00) {
                instruction = when {
                    "top right" in directionLower || "middle right" in directionLower || "bottom right" in directionLower -> {
                        "move left"
                    }
                    "top left" in directionLower || "middle left" in directionLower || "bottom left" in directionLower -> {
                        "move right"
                    }
                    else -> {
                        "check your sides"
                    }
                }
            } else {
                instruction = "move forward"
            }

            val instructionCooldownElapsed = currentTime - lastSpokenInstructionTimestamp > instructionCooldown
            if ((closerObject || timeSinceLastSpoken > instructionCooldown) && instruction != lastSpokenInstruction && instructionCooldownElapsed) {
                if (distance <= alarmDistance) {
                    // Trigger alarm and vibration
                    triggerAlarmAndVibration()
                }

                when (instruction) {
                    "move left" -> {
                        speak("$objectName is near, move left")
                    }
                    "move right" -> {
                        speak("$objectName is near, move right")
                    }
                    "check your sides" -> {
                        if (left != null && right != null) {
                            speak("Objects on both sides, move backward")
                            left = null
                            right = null
                        } else {
                            speak("$objectName is in the middle, check your sides")
                        }
                    }
                    "move forward" -> {
                        speak("Move forward")
                    }
                }

                // Update the last spoken object, distance, and timestamp
                lastSpokenObject = objectName
                lastSpokenDistance = distance
                lastSpokenTimestamp = currentTime
                lastSpokenInstruction = instruction
                lastSpokenInstructionTimestamp = currentTime
            }
        }
    }

    private fun triggerAlarmAndVibration() {
        // Play alarm sound
        mediaPlayer?.start()

        // Vibrate device
        vibrator?.let { vibrator ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(1000)
            }
        }
    }

    fun speak(text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        } else {
            val params = HashMap<String, String>()
            params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = "stringId"
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params)
        }
    }
}
