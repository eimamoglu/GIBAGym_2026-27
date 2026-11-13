package at.htl.gibagym.core.engine.store

import at.htl.gibagym.core.model.EmgSourceKind
import at.htl.gibagym.core.model.TrainingSession
import at.htl.gibagym.core.model.TrainingSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Platzhalter-Speicher für die Trainingshistorie (/LF60/): hält alles im
 * Arbeitsspeicher und ist nach dem Beenden der App leer.
 *
 * TODO(Berkay): Ersetzen durch Room + SQLCipher. Die Schnittstelle
 *  [TrainingSessionRepository] bleibt dabei unverändert, die UI muss also nicht
 *  angefasst werden. Zu beachten:
 *  - Datenbankschlüssel im Android Keystore erzeugen und dort halten
 *  - Schreibzugriffe auf Dispatchers.IO
 *  - [sessions] als Room-Flow, damit die Liste sich von selbst aktualisiert
 *
 * @param seedDemoData Befüllt den Speicher mit Beispieleinheiten. Ohne sie wäre
 *   der Verlauf beim ersten Start leer und die Anzeige nicht vorführbar. Die
 *   Einträge sind als Simulationsdaten gekennzeichnet und damit klar erkennbar.
 */
class InMemoryTrainingSessionRepository(
    seedDemoData: Boolean = true,
    nowEpochMillis: Long = System.currentTimeMillis(),
) : TrainingSessionRepository {

    private val state = MutableStateFlow(if (seedDemoData) demoSessions(nowEpochMillis) else emptyList())

    override val sessions: Flow<List<TrainingSession>> = state.asStateFlow()

    override suspend fun save(session: TrainingSession) {
        state.update { current ->
            (current.filterNot { it.id == session.id } + session).sortedByDescending { it.startedAtEpochMillis }
        }
    }

    override suspend fun findById(id: String): TrainingSession? = state.value.firstOrNull { it.id == id }

    override suspend fun delete(id: String) {
        state.update { current -> current.filterNot { it.id == id } }
    }

    override suspend fun clear() {
        state.value = emptyList()
    }

    private companion object {

        const val DAY_MILLIS = 24L * 60 * 60 * 1000

        fun demoSessions(now: Long): List<TrainingSession> = listOf(
            TrainingSession(
                id = "demo-1",
                startedAtEpochMillis = now - DAY_MILLIS,
                durationSeconds = 42 * 60,
                exerciseId = "bankdruecken",
                exerciseName = "Bankdrücken",
                repetitions = 32,
                cleanRepetitions = 27,
                averageTargetPercent = 78.4f,
                averageRatio = 0.44f,
                sourceKind = EmgSourceKind.SIMULATION,
            ),
            TrainingSession(
                id = "demo-2",
                startedAtEpochMillis = now - 3 * DAY_MILLIS,
                durationSeconds = 51 * 60,
                exerciseId = "kniebeuge",
                exerciseName = "Kniebeuge",
                repetitions = 40,
                cleanRepetitions = 25,
                averageTargetPercent = 71.9f,
                averageRatio = 0.62f,
                sourceKind = EmgSourceKind.SIMULATION,
            ),
            TrainingSession(
                id = "demo-3",
                startedAtEpochMillis = now - 6 * DAY_MILLIS,
                durationSeconds = 28 * 60,
                exerciseId = "bizepscurl",
                exerciseName = "Bizepscurl",
                repetitions = 24,
                cleanRepetitions = 14,
                averageTargetPercent = 66.2f,
                averageRatio = 0.51f,
                sourceKind = EmgSourceKind.REPLAY,
            ),
            TrainingSession(
                id = "demo-4",
                startedAtEpochMillis = now - 8 * DAY_MILLIS,
                durationSeconds = 35 * 60,
                exerciseId = "schulterdruecken",
                exerciseName = "Schulterdrücken",
                repetitions = 28,
                cleanRepetitions = 22,
                averageTargetPercent = 74.1f,
                averageRatio = 0.47f,
                sourceKind = EmgSourceKind.SIMULATION,
            ),
            TrainingSession(
                id = "demo-5",
                startedAtEpochMillis = now - 10 * DAY_MILLIS,
                durationSeconds = 38 * 60,
                exerciseId = "rudern",
                exerciseName = "Rudern",
                repetitions = 30,
                cleanRepetitions = 24,
                averageTargetPercent = 69.8f,
                averageRatio = 0.53f,
                sourceKind = EmgSourceKind.SIMULATION,
            ),
            TrainingSession(
                id = "demo-6",
                startedAtEpochMillis = now - 12 * DAY_MILLIS,
                durationSeconds = 22 * 60,
                exerciseId = "wadenheben",
                exerciseName = "Wadenheben",
                repetitions = 36,
                cleanRepetitions = 31,
                averageTargetPercent = 81.0f,
                averageRatio = 0.38f,
                sourceKind = EmgSourceKind.SIMULATION,
            ),
        )
    }
}
