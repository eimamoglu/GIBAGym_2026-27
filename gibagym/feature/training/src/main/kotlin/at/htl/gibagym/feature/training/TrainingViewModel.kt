package at.htl.gibagym.feature.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import at.htl.gibagym.core.ai.RepetitionClassifier
import at.htl.gibagym.core.engine.EmgProcessingPipeline
import at.htl.gibagym.core.engine.MeanRatioAnalyzer
import at.htl.gibagym.core.engine.ThresholdRepetitionSegmenter
import at.htl.gibagym.core.engine.processedWith
import at.htl.gibagym.core.engine.render.EmgRenderBuffer
import at.htl.gibagym.core.engine.render.LatencyTracker
import at.htl.gibagym.core.model.AppSettings
import at.htl.gibagym.core.model.EmgSourceFactory
import at.htl.gibagym.core.model.EmgSourceKind
import at.htl.gibagym.core.model.Exercise
import at.htl.gibagym.core.model.ExerciseCatalog
import at.htl.gibagym.core.model.MvcReference
import at.htl.gibagym.core.model.MvcReferenceRepository
import at.htl.gibagym.core.model.RepetitionVerdict
import at.htl.gibagym.core.model.SettingsRepository
import at.htl.gibagym.core.model.TrainingSession
import at.htl.gibagym.core.model.TrainingSessionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Hält den Messdatenstrom der Trainingsansicht (/LF30/, /LF40/, /LF50/).
 *
 * Aufgabenteilung, die das Leistungsverhalten bestimmt:
 * - Der **Ringpuffer** [renderBuffer] nimmt jeden einzelnen Messwert auf und wird
 *   von der Zeichenphase gelesen. Er ist absichtlich kein Compose-Zustand.
 * - Der **UI-Zustand** [state] wird nur rund zehnmal pro Sekunde fortgeschrieben
 *   und enthält Zahlenwerte, Verhältnis, Latenz und Bewertung.
 *
 * Die Trennung ist der Grund, warum die Anzeige bei 1000 Messwerten pro Sekunde
 * ruhig bleibt: Pro Sekunde entstehen etwa zehn Recompositions statt tausend.
 */
class TrainingViewModel(
    private val sourceFactory: EmgSourceFactory,
    private val settingsRepository: SettingsRepository,
    private val mvcRepository: MvcReferenceRepository,
    private val sessionRepository: TrainingSessionRepository,
    private val classifier: RepetitionClassifier,
    private val channelCount: Int = 4,
) : ViewModel() {

    private val _state = MutableStateFlow(TrainingUiState(klassifiziererName = classifier.displayName))
    val state: StateFlow<TrainingUiState> = _state.asStateFlow()

    /** Zielpuffer des Messdatenstroms. Wird in der Zeichenphase gelesen. */
    val renderBuffer = EmgRenderBuffer(channelCount = channelCount, capacity = 4000)

    /** Wird in der Zeichenphase gefüttert, siehe `LiveEmgDiagramm`. */
    val latencyTracker = LatencyTracker()

    private val ratioAnalyzer = MeanRatioAnalyzer()
    private val segmenter = ThresholdRepetitionSegmenter()

    private var pipeline = EmgProcessingPipeline(channelCount = channelCount)
    private var streamJob: Job? = null
    private var uiJob: Job? = null
    private var startedAtMillis = 0L

    init {
        settingsRepository.settings
            .onEach { einstellungen -> uebernehmeEinstellungen(einstellungen) }
            .launchIn(viewModelScope)

        mvcRepository.current
            .onEach { referenz -> uebernehmeKalibrierung(referenz) }
            .launchIn(viewModelScope)
    }

    fun uebungWaehlen(exercise: Exercise) {
        _state.update { it.copy(exercise = exercise) }
        segmenter.reset()
        uebernehmeKalibrierung(mvcRepository.forExercise(exercise.id))
    }

    fun start() {
        if (streamJob?.isActive == true) return
        startedAtMillis = System.currentTimeMillis()
        segmenter.reset()
        renderBuffer.clear()
        latencyTracker.reset()
        pipeline.reset()
        _state.update { it.copy(running = true, repetitions = 0, cleanRepetitions = 0, lastAssessment = null) }

        val quelle = sourceFactory.create(_state.value.sourceKind).processedWith(pipeline)

        quelle.connection
            .onEach { zustand -> _state.update { it.copy(connection = zustand) } }
            .launchIn(viewModelScope)

        streamJob = viewModelScope.launch {
            quelle.frames.collect { frame ->
                renderBuffer.push(frame)

                val fenster = segmenter.accept(frame, _state.value.exercise)
                if (fenster != null) {
                    // Bewertung läuft nebenher: Sie darf den Messdatenstrom nicht bremsen.
                    launch {
                        val bewertung = classifier.classify(fenster, _state.value.exercise)
                        _state.update { aktuell ->
                            aktuell.copy(
                                lastAssessment = bewertung,
                                repetitions = aktuell.repetitions + 1,
                                cleanRepetitions = aktuell.cleanRepetitions +
                                    if (bewertung.verdict == RepetitionVerdict.SAUBER) 1 else 0,
                            )
                        }
                    }
                }
            }
        }

        uiJob = viewModelScope.launch {
            while (true) {
                aktualisiereAnzeigewerte()
                delay(ANZEIGE_INTERVALL_MILLIS)
            }
        }
    }

    fun stop() {
        streamJob?.cancel()
        uiJob?.cancel()
        streamJob = null
        uiJob = null
        _state.update { it.copy(running = false) }
        speichereEinheit()
    }

    override fun onCleared() {
        streamJob?.cancel()
        uiJob?.cancel()
        super.onCleared()
    }

    /**
     * Liest den aktuellen Stand aus dem Ringpuffer und schreibt ihn in den
     * UI-Zustand. Zehnmal pro Sekunde: schnell genug, dass die Zahl "lebt",
     * langsam genug, dass sie lesbar bleibt.
     */
    private fun aktualisiereAnzeigewerte() {
        val exercise = _state.value.exercise
        val prozente = List(channelCount) { kanal -> renderBuffer.latestPercent(kanal).roundToInt() }
        val zielProzent = exercise.targetChannels.firstOrNull()
            ?.let { renderBuffer.latestPercent(it.channelIndex) } ?: 0f

        val frame = at.htl.gibagym.core.model.EmgFrame(
            timestampNanos = renderBuffer.latestTimestampNanos,
            channels = FloatArray(channelCount),
            mvcPercent = FloatArray(channelCount) { renderBuffer.latestPercent(it) },
            signalQuality = renderBuffer.latestQuality,
        )

        _state.update { aktuell ->
            aktuell.copy(
                percentPerChannel = prozente,
                signalQuality = renderBuffer.latestQuality,
                zonePosition = exercise.targetZone.classify(zielProzent),
                ratio = ratioAnalyzer.analyze(frame, exercise),
                latency = latencyTracker.snapshot(),
            )
        }
    }

    private fun uebernehmeEinstellungen(einstellungen: AppSettings) {
        pipeline.configure(einstellungen.smoothing)
        _state.update {
            it.copy(
                sourceKind = einstellungen.sourceKind,
                palette = einstellungen.palette,
                eigenWenigArgb = einstellungen.eigenWenigArgb,
                eigenZielArgb = einstellungen.eigenZielArgb,
                eigenHochArgb = einstellungen.eigenHochArgb,
                displayName = einstellungen.displayName,
                latenzAnzeigen = einstellungen.showLatencyOverlay,
                glaettungsLatenzMillis = einstellungen.smoothing.expectedDelayMillis(ABTASTRATE_HZ),
            )
        }
        val uebung = ExerciseCatalog.aufloesen(
            einstellungen.selectedExerciseId,
            einstellungen.customExercises,
        )
        if (_state.value.exercise.id != uebung.id) {
            uebungWaehlen(uebung)
        }
        // Ein Wechsel der Quelle muss den laufenden Strom neu aufbauen, sonst
        // liefert die alte Quelle weiter Daten.
        if (_state.value.running && streamJob?.isActive == true) {
            neustartMitAktuellerQuelle()
        }
    }

    private fun uebernehmeKalibrierung(referenz: MvcReference?) {
        val exerciseId = _state.value.exercise.id
        val passend = referenz?.takeIf { it.exerciseId == exerciseId } ?: mvcRepository.forExercise(exerciseId)
        pipeline.reference = passend ?: MvcReference.uncalibrated(channelCount, exerciseId)
        _state.update { it.copy(kalibriert = passend != null) }
    }

    private fun neustartMitAktuellerQuelle() {
        streamJob?.cancel()
        uiJob?.cancel()
        streamJob = null
        uiJob = null
        start()
    }

    private fun speichereEinheit() {
        val aktuell = _state.value
        if (aktuell.repetitions == 0) return

        viewModelScope.launch {
            sessionRepository.save(
                TrainingSession(
                    id = UUID.randomUUID().toString(),
                    startedAtEpochMillis = startedAtMillis,
                    durationSeconds = ((System.currentTimeMillis() - startedAtMillis) / 1000).toInt(),
                    exerciseId = aktuell.exercise.id,
                    exerciseName = aktuell.exercise.displayName,
                    repetitions = aktuell.repetitions,
                    cleanRepetitions = aktuell.cleanRepetitions,
                    averageTargetPercent = aktuell.ratio.targetPercent,
                    averageRatio = aktuell.ratio.ratio,
                    sourceKind = aktuell.sourceKind,
                ),
            )
        }
    }

    companion object {
        private const val ANZEIGE_INTERVALL_MILLIS = 100L
        private const val ABTASTRATE_HZ = 1000

        fun factory(
            sourceFactory: EmgSourceFactory,
            settingsRepository: SettingsRepository,
            mvcRepository: MvcReferenceRepository,
            sessionRepository: TrainingSessionRepository,
            classifier: RepetitionClassifier,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                TrainingViewModel(
                    sourceFactory = sourceFactory,
                    settingsRepository = settingsRepository,
                    mvcRepository = mvcRepository,
                    sessionRepository = sessionRepository,
                    classifier = classifier,
                )
            }
        }
    }
}
