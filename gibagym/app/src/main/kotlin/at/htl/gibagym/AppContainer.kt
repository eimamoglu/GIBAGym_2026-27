package at.htl.gibagym

import android.content.Context
import at.htl.gibagym.core.ai.HeuristicRepetitionClassifier
import at.htl.gibagym.core.ai.RepetitionClassifier
import at.htl.gibagym.core.ble.StubBleEmgSource
import at.htl.gibagym.core.engine.replay.RecordingReader
import at.htl.gibagym.core.engine.replay.ReplayEmgSource
import at.htl.gibagym.core.engine.simulation.SimulatedEmgSource
import at.htl.gibagym.core.engine.simulation.SimulationProfile
import at.htl.gibagym.core.engine.store.InMemoryMvcReferenceRepository
import at.htl.gibagym.core.engine.store.InMemoryTrainingSessionRepository
import at.htl.gibagym.core.model.EmgSource
import at.htl.gibagym.core.model.EmgSourceFactory
import at.htl.gibagym.core.model.EmgSourceKind
import at.htl.gibagym.core.model.MvcReferenceRepository
import at.htl.gibagym.core.model.SettingsRepository
import at.htl.gibagym.core.model.TrainingSessionRepository

/**
 * Abhängigkeiten der App, von Hand verdrahtet.
 *
 * Bewusst kein Dagger/Hilt: Der Prototyp hat eine Handvoll Objekte mit klarer
 * Lebensdauer. Ein Abhängigkeitsrahmenwerk würde hier mehr erklären müssen, als
 * es einspart — und diese Datei ist gleichzeitig die beste Übersicht darüber,
 * was wo durch eine echte Implementierung ersetzt werden muss.
 */
class AppContainer(private val context: Context) {

    val settingsRepository: SettingsRepository = PersistentSettingsRepository(context)

    val mvcRepository: MvcReferenceRepository = InMemoryMvcReferenceRepository()

    val sessionRepository: TrainingSessionRepository = InMemoryTrainingSessionRepository()

    /** TODO(Samoel): Durch die Implementierung mit dem trainierten Modell ersetzen. */
    val classifier: RepetitionClassifier = HeuristicRepetitionClassifier()

    /**
     * Erzeugt die Messquelle zur gewählten Betriebsart.
     *
     * Jeder Aufruf liefert eine neue Instanz: [EmgSource.frames] ist cold, und
     * eine wiederverwendete Instanz würde beim Quellenwechsel den Zustand der
     * vorherigen Messung mitschleppen.
     */
    val sourceFactory: EmgSourceFactory = EmgSourceFactory { kind ->
        when (kind) {
            EmgSourceKind.SIMULATION -> SimulatedEmgSource(SimulationProfile())
            EmgSourceKind.REPLAY -> ReplayEmgSource(reader = assetReader(AUFZEICHNUNG))
            // TODO(Elyesa): Hier die echte BLE-Quelle einsetzen.
            EmgSourceKind.BLE -> StubBleEmgSource()
        }
    }

    /**
     * Liest eine Aufzeichnung aus dem Asset-Ordner.
     *
     * `useLines` würde den Datenstrom schließen, bevor die Sequenz ausgewertet
     * ist; deshalb wird hier bewusst vollständig gelesen. Die Demo-Datei ist
     * rund 1,3 MB groß — vertretbar. Eine längere Aufzeichnung müsste
     * stückweise gelesen werden.
     */
    private fun assetReader(pfad: String) = RecordingReader {
        runCatching {
            context.assets.open(pfad).bufferedReader().use { it.readLines() }.asSequence()
        }.getOrDefault(emptySequence())
    }

    private companion object {
        const val AUFZEICHNUNG = "aufzeichnungen/bankdruecken-demo.csv"
    }
}
