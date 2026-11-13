package at.htl.gibagym.core.model

import kotlinx.coroutines.flow.Flow

/**
 * Eine abgeschlossene Trainingseinheit (/LF60/).
 *
 * @property id Stabile ID (UUID-String).
 * @property startedAtEpochMillis Beginn der Einheit.
 * @property durationSeconds Dauer in Sekunden.
 * @property exerciseId Übung.
 * @property exerciseName Anzeigename zum Zeitpunkt der Aufzeichnung.
 * @property repetitions Wiederholungen gesamt.
 * @property cleanRepetitions Davon als sauber bewertet.
 * @property averageTargetPercent Mittlere Zielmuskelaktivierung in %MVC.
 * @property averageRatio Mittleres Verhältnis Hilfs-/Zielmuskel.
 * @property sourceKind Womit aufgezeichnet wurde — im Verlauf sichtbar, damit
 *   Simulationsdaten nicht mit echten Messungen verwechselt werden.
 */
data class TrainingSession(
    val id: String,
    val startedAtEpochMillis: Long,
    val durationSeconds: Int,
    val exerciseId: String,
    val exerciseName: String,
    val repetitions: Int,
    val cleanRepetitions: Int,
    val averageTargetPercent: Float,
    val averageRatio: Float,
    val sourceKind: EmgSourceKind,
) {
    /** Anteil sauberer Wiederholungen in Prozent. */
    val cleanShare: Float
        get() = if (repetitions == 0) 0f else cleanRepetitions * 100f / repetitions
}

/**
 * Lokaler, verschlüsselter Speicher der Trainingshistorie.
 *
 * Datenschutz ist ein Kernversprechen des Projekts: Die Daten verlassen das Gerät
 * nicht. Es gibt deshalb bewusst keinen Sync- und keinen Upload-Pfad in dieser
 * Schnittstelle.
 *
 * TODO(Berkay): Gegen Room + SQLCipher implementieren. Schlüssel im Android Keystore
 *  ablegen (nicht in den SharedPreferences), Migrationen über Room-Migrations.
 *  Der Prototyp hält alles im Arbeitsspeicher — nach dem App-Neustart ist alles weg.
 */
interface TrainingSessionRepository {

    /** Alle Einheiten, neueste zuerst. Aktualisiert sich bei Änderungen. */
    val sessions: Flow<List<TrainingSession>>

    suspend fun save(session: TrainingSession)

    suspend fun findById(id: String): TrainingSession?

    /** Löscht eine Einheit (DSGVO: Recht auf Löschung). */
    suspend fun delete(id: String)

    /** Löscht alle lokalen Daten. */
    suspend fun clear()
}
