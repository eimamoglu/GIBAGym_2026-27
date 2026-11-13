package at.htl.gibagym.core.model

import kotlinx.coroutines.flow.StateFlow

/**
 * Alle zur Laufzeit umschaltbaren Einstellungen der App.
 *
 * @property sourceKind Demo-Modus: Simulation, CSV-Wiedergabe oder echtes Wearable.
 * @property smoothing Glättungsverfahren und Fensterlänge.
 * @property palette Farbskala des Avatars — fest oder selbst gewählt.
 * @property eigenWenigArgb Eigene Farbe für geringe Aktivierung (ARGB).
 * @property eigenZielArgb Eigene Farbe für den Zielbereich (ARGB).
 * @property eigenHochArgb Eigene Farbe für Überlastung (ARGB).
 * @property hapticsEnabled Vibrationsfeedback beim Verlassen des Zielkorridors.
 * @property showLatencyOverlay Entwickleranzeige der Ende-zu-Ende-Latenz.
 * @property onboardingCompleted Steuert, ob "Erste Schritte" beim Start erscheint.
 * @property displayName Angezeigter Vorname. Leer, solange noch nicht gefragt.
 * @property selectedExerciseId Gewählte Übung. Die App erkennt Übungen nicht selbst.
 * @property customExercises Vom Nutzer benannte Übungen.
 */
data class AppSettings(
    val sourceKind: EmgSourceKind = EmgSourceKind.SIMULATION,
    val smoothing: SmoothingConfig = SmoothingConfig.DEFAULT,
    val palette: PaletteChoice = PaletteChoice.STANDARD,
    val eigenWenigArgb: Int = DEFAULT_EIGEN_WENIG,
    val eigenZielArgb: Int = DEFAULT_EIGEN_ZIEL,
    val eigenHochArgb: Int = DEFAULT_EIGEN_HOCH,
    val hapticsEnabled: Boolean = true,
    val showLatencyOverlay: Boolean = true,
    val onboardingCompleted: Boolean = false,
    val displayName: String = "",
    val selectedExerciseId: String = ExerciseCatalog.bankdruecken.id,
    val customExercises: List<CustomExercise> = emptyList(),
) {
    companion object {
        val DEFAULT_EIGEN_WENIG: Int = 0xFFB9C4CC.toInt()
        val DEFAULT_EIGEN_ZIEL: Int = 0xFF2E7D32.toInt()
        val DEFAULT_EIGEN_HOCH: Int = 0xFFC62828.toInt()

        const val NAME_MAX_LAENGE = 24

        fun nameBereinigen(roh: String): String = roh.trim().take(NAME_MAX_LAENGE)
    }
}

/**
 * Farbskala für die Aktivierungsanzeige.
 *
 * Farbe ist in dieser App **nie** der einzige Informationsträger: Jeder farbige
 * Balken trägt zusätzlich eine Füllhöhe und einen Zahlenwert. Die Alternativpalette
 * ist trotzdem nötig, weil rund 8 % der Männer eine Rot-Grün-Schwäche haben und
 * Rot/Grün damit die schlechtestmögliche Wahl für ein Ampelschema ist.
 */
enum class PaletteChoice {
    /** Grün → Gelb → Rot. Vertraut, aber bei Rot-Grün-Schwäche schwer zu unterscheiden. */
    STANDARD,

    /** Blau → Hellgrau → Orange. Für Deuteranopie/Protanopie unterscheidbar. */
    ROT_GRUEN_SICHER,

    /** Helligkeitsverlauf ohne Farbinformation — funktioniert auch bei Achromatopsie. */
    MONOCHROM,

    /** Drei selbst gewählte Farben für Wenig, Im Ziel und Zu hoch. */
    EIGEN,
}

/** Speicher der Einstellungen. Der Prototyp hält sie im Arbeitsspeicher. */
interface SettingsRepository {

    val settings: StateFlow<AppSettings>

    fun update(transform: (AppSettings) -> AppSettings)
}
