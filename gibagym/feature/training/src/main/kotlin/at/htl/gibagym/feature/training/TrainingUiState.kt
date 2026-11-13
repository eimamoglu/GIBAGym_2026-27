package at.htl.gibagym.feature.training

import androidx.compose.runtime.Immutable
import at.htl.gibagym.core.model.ConnectionState
import at.htl.gibagym.core.model.EmgSourceKind
import at.htl.gibagym.core.model.Exercise
import at.htl.gibagym.core.model.ExerciseCatalog
import at.htl.gibagym.core.model.AppSettings
import at.htl.gibagym.core.model.LatencySnapshot
import at.htl.gibagym.core.model.PaletteChoice
import at.htl.gibagym.core.model.RatioResult
import at.htl.gibagym.core.model.RepetitionAssessment
import at.htl.gibagym.core.model.SignalQuality
import at.htl.gibagym.core.model.ZonePosition

/**
 * Zustand der Trainingsansicht — **ohne** die hochfrequenten Messwerte.
 *
 * Das ist der entscheidende Punkt: Hier steht kein einziger Wert, der sich
 * 1000-mal pro Sekunde ändert. Die Messwerte liegen im Ringpuffer des
 * ViewModels und werden von der Zeichenphase gelesen. In diesen Zustand kommen
 * nur Angaben, die sich selten ändern oder deren Anzeige bewusst gedrosselt ist
 * (Zahlenwerte, Ratio, Latenz: rund zehnmal pro Sekunde).
 *
 * @property percentPerChannel Gedrosselte Zahlenwerte je Kanal in %MVC.
 * @property kalibriert False, solange keine MVC-Referenz vorliegt — die Anzeige
 *   muss dann deutlich machen, dass die Prozentwerte nicht personalisiert sind.
 * @property latenzAnzeigen Ob die Latenzmessung eingeblendet wird. Abschaltbar,
 *   weil sie beim Vorführen der App für Laien mehr verwirrt als hilft.
 */
@Immutable
data class TrainingUiState(
    val exercise: Exercise = ExerciseCatalog.bankdruecken,
    val running: Boolean = false,
    val sourceKind: EmgSourceKind = EmgSourceKind.SIMULATION,
    val connection: ConnectionState = ConnectionState.Disconnected,
    val signalQuality: SignalQuality = SignalQuality.UNKNOWN,
    val percentPerChannel: List<Int> = List(4) { 0 },
    val zonePosition: ZonePosition = ZonePosition.ZU_NIEDRIG,
    val ratio: RatioResult = RatioResult.IDLE,
    val latency: LatencySnapshot = LatencySnapshot.EMPTY,
    val lastAssessment: RepetitionAssessment? = null,
    val repetitions: Int = 0,
    val cleanRepetitions: Int = 0,
    val palette: PaletteChoice = PaletteChoice.STANDARD,
    val eigenWenigArgb: Int = AppSettings.DEFAULT_EIGEN_WENIG,
    val eigenZielArgb: Int = AppSettings.DEFAULT_EIGEN_ZIEL,
    val eigenHochArgb: Int = AppSettings.DEFAULT_EIGEN_HOCH,
    val displayName: String = "",
    val kalibriert: Boolean = false,
    val latenzAnzeigen: Boolean = true,
    val klassifiziererName: String = "",
    val glaettungsLatenzMillis: Float = 0f,
) {
    val zielProzent: Int
        get() = exercise.targetChannels.firstOrNull()
            ?.let { percentPerChannel.getOrNull(it.channelIndex) } ?: 0
}
