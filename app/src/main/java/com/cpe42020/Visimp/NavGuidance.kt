package com.cpe42020.Visimp

import android.speech.tts.TextToSpeech
import android.util.Log
import android.os.Build
import java.util.Locale

class NavGuidance(private val tts: TextToSpeech) {

    private var lastSpokenObject: String? = null
    private var lastSpokenTimestamp: Long = 0
    private var lastSpokenDistance: Double = Double.MAX_VALUE
    private var lastSpokenInstruction: String? = null
    private var lastSpokenInstructionTimestamp: Long = 0
    private val instructionCooldown = 3000 // Cooldown period in milliseconds
    var left: String? = null
    var right: String? = null

    fun speakObject(objectName: String, distance: Double, direction: String) {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastSpoken = currentTime - lastSpokenTimestamp

        // Check if the current object is closer than the previously spoken object
        val closerObject = lastSpokenObject == null || distance < lastSpokenDistance

        if (closerObject || timeSinceLastSpoken > 3000) {
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
            if ((closerObject || timeSinceLastSpoken > 3000) && instruction != lastSpokenInstruction && instructionCooldownElapsed) {
                when (instruction) {
                    "move left" -> {
                        left = objectName
                        speak("$objectName is near, move left")
                    }
                    "move right" -> {
                        right = objectName
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