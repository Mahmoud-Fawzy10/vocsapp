package com.example.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

data class TtsVoiceOption(
    val name: String,
    val displayLabel: String,
    val localeTag: String,
    val isNetwork: Boolean
)

class PronunciationManager(context: Context) {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var rawVoices: List<Voice> = emptyList()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<TtsVoiceOption>>(emptyList())
    val availableVoices: StateFlow<List<TtsVoiceOption>> = _availableVoices.asStateFlow()

    private val _hasUsVoice = MutableStateFlow(false)
    val hasUsVoice: StateFlow<Boolean> = _hasUsVoice.asStateFlow()

    private val _hasUkVoice = MutableStateFlow(false)
    val hasUkVoice: StateFlow<Boolean> = _hasUkVoice.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        try {
            tts = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val engine = tts
                    if (engine != null) {
                        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {
                                _isSpeaking.value = true
                            }

                            override fun onDone(utteranceId: String?) {
                                _isSpeaking.value = false
                            }

                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) {
                                _isSpeaking.value = false
                            }

                            override fun onError(utteranceId: String?, errorCode: Int) {
                                _isSpeaking.value = false
                                _statusMessage.value = "تعذر تشغيل النطق الصوتي (رمز الخطأ: $errorCode)"
                            }
                        })

                        val voices = runCatching {
                            engine.voices?.filter { it.locale?.language == "en" }.orEmpty()
                        }.getOrDefault(emptyList())
                        rawVoices = voices
                        _hasUsVoice.value = voices.any { it.locale.country.equals("US", ignoreCase = true) } ||
                            engine.isLanguageAvailable(Locale.US) >= TextToSpeech.LANG_AVAILABLE
                        _hasUkVoice.value = voices.any { it.locale.country.equals("GB", ignoreCase = true) } ||
                            engine.isLanguageAvailable(Locale.UK) >= TextToSpeech.LANG_AVAILABLE

                        _availableVoices.value = voices
                            .sortedWith(compareBy({ it.locale.country }, { it.name }))
                            .map { voice ->
                                val regionLabel = when (voice.locale.country.uppercase(Locale.ROOT)) {
                                    "US" -> "أمريكي (US)"
                                    "GB" -> "بريطاني (UK)"
                                    else -> voice.locale.toLanguageTag()
                                }
                                TtsVoiceOption(
                                    name = voice.name,
                                    displayLabel = "$regionLabel • ${voice.name.takeLast(18)}",
                                    localeTag = voice.locale.toLanguageTag(),
                                    isNetwork = voice.isNetworkConnectionRequired
                                )
                            }
                        _isReady.value = true
                        _statusMessage.value = null
                    }
                } else {
                    _isReady.value = false
                    _statusMessage.value = "محرك النطق الصوتي غير متاح على هذا الجهاز — يتم عرض النطق النصي البديل."
                }
            }
        } catch (e: Exception) {
            _isReady.value = false
            _statusMessage.value = "تعذر تهيئة محرك النطق: ${e.localizedMessage ?: "خطأ غير معروف"}"
        }
    }

    fun speak(
        text: String,
        preferredAccent: String = "US",
        preferredVoiceName: String = "",
        speed: Float = 1.0f
    ): Boolean {
        val engine = tts
        if (engine == null || !_isReady.value) {
            _statusMessage.value = "محرك النطق الصوتي غير متاح حالياً."
            return false
        }
        val cleanText = text.trim()
        if (cleanText.isBlank()) return false

        engine.setSpeechRate(speed.coerceIn(0.5f, 1.75f))

        val selectedVoice = rawVoices.firstOrNull { it.name == preferredVoiceName }
            ?: when (preferredAccent.uppercase(Locale.ROOT)) {
                "UK", "GB" -> rawVoices.firstOrNull { it.locale.country.equals("GB", ignoreCase = true) }
                else -> rawVoices.firstOrNull { it.locale.country.equals("US", ignoreCase = true) }
            }

        if (selectedVoice != null) {
            engine.voice = selectedVoice
        } else {
            val targetLocale = if (preferredAccent.uppercase(Locale.ROOT) in listOf("UK", "GB")) {
                Locale.UK
            } else {
                Locale.US
            }
            val availability = engine.isLanguageAvailable(targetLocale)
            if (availability >= TextToSpeech.LANG_AVAILABLE) {
                engine.language = targetLocale
            } else if (engine.isLanguageAvailable(Locale.ENGLISH) >= TextToSpeech.LANG_AVAILABLE) {
                engine.language = Locale.ENGLISH
            } else {
                _statusMessage.value = "لا يوجد صوت إنجليزي مثبت على الجهاز."
                return false
            }
        }

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UUID.randomUUID().toString())
        }
        val res = engine.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, UUID.randomUUID().toString())
        return res == TextToSpeech.SUCCESS
    }

    fun stop() {
        runCatching { tts?.stop() }
        _isSpeaking.value = false
    }

    fun shutdown() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        _isReady.value = false
    }
}
