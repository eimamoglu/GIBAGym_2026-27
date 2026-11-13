package at.htl.gibagym.feature.calibration

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import at.htl.gibagym.core.engine.EmgProcessingPipeline
import at.htl.gibagym.core.engine.PeakMvcCalibrator
import at.htl.gibagym.core.engine.processedWith
import at.htl.gibagym.core.engine.render.EmgRenderBuffer
import at.htl.gibagym.core.model.EmgSourceFactory
import at.htl.gibagym.core.model.Exercise
import at.htl.gibagym.core.model.ExerciseCatalog
import at.htl.gibagym.core.model.MvcReference
import at.htl.gibagym.core.model.MvcReferenceRepository
import at.htl.gibagym.core.model.SettingsRepository
import at.htl.gibagym.core.model.SignalQuality
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Ablaufschritte der Kalibrierung (/LF20/). */
enum class KalibrierSchritt { ERKLAERUNG, COUNTDOWN, AUFNAHME, ERGEBNIS }

@Immutable
data class CalibrationUiState(
    val schritt: KalibrierSchritt = KalibrierSchritt.ERKLAERUNG,
    val exercise: Exercise = ExerciseCatalog.bankdruecken,
    val countdownSekunden: Int = 3,
    val restsekunden: Int = 5,
    val aktuelleMillivolt: List<Float> = List(4) { 0f },
    val spitzenMillivolt: List<Float> = List(4) { 0f },
    val signalQuality: SignalQuality = SignalQuality.UNKNOWN,
    val gespeichert: Boolean = false,
) {
    /**
     * True, wenn die Messung des **Zielmuskels** so klein ist, dass sie nicht
     * plausibel ist.
     *
     * Nur der Zielmuskel zählt: Ein Beobachtungskanal wie der Rumpf darf beim
     * Bankdrücken ruhig nahe null bleiben. Würde jeder Kanal geprüft, erschiene
     * die Warnung bei praktisch jeder korrekten Kalibrierung.
     */
    val verdaechtigNiedrig: Boolean
        get() = schritt == KalibrierSchritt.ERGEBNIS && exercise.targetChannels.any { mapping ->
            spitzenMillivolt.getOrElse(mapping.channelIndex) { 0f } < MINDESTWERT_MILLIVOLT
        }

    private companion object {
        const val MINDESTWERT_MILLIVOLT = 0.2f
    }
}

/**
 * Steuert die MVC-Aufnahme nach /LF20/.
 *
 * Der Ablauf ist bewusst geführt und nicht frei: Countdown, feste Aufnahmedauer,
 * Ergebnis. Eine Kalibrierung, bei der der Nutzer selbst entscheidet, wann er
 * "fertig" ist, liefert keine vergleichbaren Referenzwerte.
 */
class CalibrationViewModel(
    private val sourceFactory: EmgSourceFactory,
    private val settingsRepository: SettingsRepository,
    private val mvcRepository: MvcReferenceRepository,
    private val channelCount: Int = 4,
) : ViewModel() {

    private val _state = MutableStateFlow(CalibrationUiState())
    val state: StateFlow<CalibrationUiState> = _state.asStateFlow()

    /** Für den Live-Balken während der Aufnahme. */
    val renderBuffer = EmgRenderBuffer(channelCount = channelCount, capacity = 2000)

    private val calibrator = PeakMvcCalibrator()
    private var ablauf: Job? = null

    init {
        settingsRepository.settings
            .onEach { einstellungen ->
                uebungWaehlen(
                    ExerciseCatalog.aufloesen(
                        einstellungen.selectedExerciseId,
                        einstellungen.customExercises,
                    ),
                )
            }
            .launchIn(viewModelScope)
    }

    fun uebungWaehlen(exercise: Exercise) {
        _state.update { it.copy(exercise = exercise) }
    }

    fun starten() {
        if (ablauf?.isActive == true) return
        calibrator.reset(channelCount)
        renderBuffer.clear()
        _state.update {
            it.copy(
                schritt = KalibrierSchritt.COUNTDOWN,
                countdownSekunden = COUNTDOWN_SEKUNDEN,
                restsekunden = AUFNAHME_SEKUNDEN,
                spitzenMillivolt = List(channelCount) { 0f },
                gespeichert = false,
            )
        }

        ablauf = viewModelScope.launch {
            for (sekunde in COUNTDOWN_SEKUNDEN downTo 1) {
                _state.update { it.copy(countdownSekunden = sekunde) }
                delay(1000)
            }

            _state.update { it.copy(schritt = KalibrierSchritt.AUFNAHME) }

            // Die Aufnahme läuft parallel zum Herunterzählen: Der Messstrom wird
            // beendet, sobald die Zeit abgelaufen ist.
            val messung = launch { nimmAuf() }
            val anzeige = launch { aktualisiereAnzeigeLaufend() }
            for (sekunde in AUFNAHME_SEKUNDEN downTo 1) {
                _state.update { it.copy(restsekunden = sekunde) }
                delay(1000)
            }
            messung.cancel()
            anzeige.cancel()

            _state.update {
                it.copy(
                    schritt = KalibrierSchritt.ERGEBNIS,
                    spitzenMillivolt = calibrator.currentPeaks().toList(),
                )
            }
        }
    }

    fun abbrechen() {
        ablauf?.cancel()
        ablauf = null
        _state.update { it.copy(schritt = KalibrierSchritt.ERKLAERUNG) }
    }

    fun uebernehmen() {
        val referenz = calibrator.finish(
            exerciseId = _state.value.exercise.id,
            nowEpochMillis = System.currentTimeMillis(),
        )
        mvcRepository.save(referenz)
        _state.update { it.copy(gespeichert = true) }
    }

    /** Für Vorschauen und Tests: Zustand direkt setzen. */
    fun setzeZustandFuerVorschau(state: CalibrationUiState) {
        _state.value = state
    }

    override fun onCleared() {
        ablauf?.cancel()
        super.onCleared()
    }

    private suspend fun nimmAuf() {
        val einstellungen = settingsRepository.settings.value
        // Während der Kalibrierung wird dieselbe Filter- und Glättungskette
        // verwendet wie im Training. Anders gemessen als später angezeigt wird,
        // wäre die Referenz systematisch falsch.
        val pipeline = EmgProcessingPipeline(
            channelCount = channelCount,
            smoothing = einstellungen.smoothing,
            reference = MvcReference.uncalibrated(channelCount, _state.value.exercise.id),
        )
        val quelle = sourceFactory.create(einstellungen.sourceKind).processedWith(pipeline)

        quelle.frames.collect { frame ->
            renderBuffer.push(frame)
            calibrator.accept(frame)
        }
    }

    /**
     * Schreibt die Zahlenwerte zehnmal pro Sekunde fort.
     *
     * Aus demselben Grund wie in der Trainingsansicht: Würde jeder der 1000
     * Messwerte pro Sekunde in den Compose-Zustand geschrieben, entstünden 1000
     * Recompositions pro Sekunde für eine Zahl, die ohnehin niemand so schnell
     * lesen kann.
     */
    private suspend fun aktualisiereAnzeigeLaufend() {
        while (true) {
            _state.update {
                it.copy(
                    aktuelleMillivolt = List(channelCount) { kanal -> renderBuffer.latestMillivolts(kanal) },
                    spitzenMillivolt = calibrator.currentPeaks().toList(),
                    signalQuality = renderBuffer.latestQuality,
                )
            }
            delay(ANZEIGE_INTERVALL_MILLIS)
        }
    }

    companion object {
        const val COUNTDOWN_SEKUNDEN = 3
        const val AUFNAHME_SEKUNDEN = 5
        private const val ANZEIGE_INTERVALL_MILLIS = 100L

        fun factory(
            sourceFactory: EmgSourceFactory,
            settingsRepository: SettingsRepository,
            mvcRepository: MvcReferenceRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CalibrationViewModel(sourceFactory, settingsRepository, mvcRepository)
            }
        }
    }
}
