package at.htl.gibagym.core.model

/**
 * Muskelgruppen, die der 2D-Körper-Avatar darstellen kann.
 *
 * Die deutschen Anzeigenamen liegen bewusst **nicht** hier, sondern als
 * String-Ressourcen in `:core:ui` (`muscleLabel()`), damit Schriftskalierung,
 * Übersetzung und TalkBack sauber funktionieren.
 */
enum class MuscleGroup {
    BRUST,
    TRIZEPS,
    BIZEPS,
    SCHULTER_VORNE,
    LATISSIMUS,
    RUMPF,
    GESAESS,
    OBERSCHENKEL_VORNE,
    OBERSCHENKEL_HINTEN,
    WADE,
}

/** Rolle eines Muskels in einer konkreten Übung. */
enum class MuscleRole {
    /** Der Muskel, der die Übung leisten soll (Agonist). */
    ZIELMUSKEL,

    /** Unterstützender Muskel (Synergist) — übernimmt er zu viel, wird abgefälscht. */
    HILFSMUSKEL,

    /** Nur zur Beobachtung, geht nicht in die Ratio-Bewertung ein. */
    BEOBACHTUNG,
}

/**
 * Zuordnung eines Sensorkanals zu einer Muskelgruppe für eine Übung.
 *
 * @property channelIndex Index im [EmgFrame.channels]-Array.
 * @property muscle Welcher Muskel an diesem Kanal klebt.
 * @property role Rolle des Muskels in der Übung.
 * @property side Körperseite — relevant für den Avatar und die Symmetrieprüfung.
 */
data class ChannelMapping(
    val channelIndex: Int,
    val muscle: MuscleGroup,
    val role: MuscleRole,
    val side: BodySide = BodySide.BEIDE,
)

/** Körperseite eines Sensors. */
enum class BodySide { LINKS, RECHTS, BEIDE }

/**
 * Eine Übung mit ihrer Sensorbelegung und ihrem Zielkorridor.
 *
 * @property id Stabiler Schlüssel, wird in der Datenbank gespeichert.
 * @property displayName Deutscher Anzeigename. Kommt später aus der lokalen DB,
 *   deshalb hier als Text und nicht als String-Ressource.
 * @property channels Sensorbelegung — bestimmt, was der Avatar einfärbt.
 * @property targetZone Aktivierungskorridor des Zielmuskels in %MVC.
 * @property maxSupportRatio Ab welchem Verhältnis Hilfsmuskel/Zielmuskel die
 *   Ausführung als abgefälscht gilt (0,6 = Hilfsmuskel darf höchstens 60 % des
 *   Zielmuskels beitragen).
 */
data class Exercise(
    val id: String,
    val displayName: String,
    val channels: List<ChannelMapping>,
    val targetZone: ActivationZone = ActivationZone.DEFAULT,
    val maxSupportRatio: Float = 0.6f,
) {
    val targetChannels: List<ChannelMapping> get() = channels.filter { it.role == MuscleRole.ZIELMUSKEL }
    val supportChannels: List<ChannelMapping> get() = channels.filter { it.role == MuscleRole.HILFSMUSKEL }

    fun mappingFor(channelIndex: Int): ChannelMapping? = channels.firstOrNull { it.channelIndex == channelIndex }
}

/**
 * Zielkorridor der Aktivierung in %MVC.
 *
 * Unterhalb von [lowerPercent] ist der Reiz zu klein, oberhalb von [upperPercent]
 * wird meist mit Schwung gearbeitet. Verlässt der Wert den Korridor, löst die UI
 * das Vibrationsmuster aus (zweiter Feedback-Kanal beim Bankdrücken).
 */
data class ActivationZone(
    val lowerPercent: Float,
    val upperPercent: Float,
) {
    init {
        require(lowerPercent < upperPercent) { "Untergrenze muss kleiner als Obergrenze sein" }
    }

    fun classify(percent: Float): ZonePosition = when {
        percent < lowerPercent -> ZonePosition.ZU_NIEDRIG
        percent > upperPercent -> ZonePosition.ZU_HOCH
        else -> ZonePosition.IM_ZIEL
    }

    companion object {
        /** Erfahrungswert aus der Literatur für hypertrophieorientiertes Krafttraining. */
        val DEFAULT = ActivationZone(lowerPercent = 60f, upperPercent = 95f)
    }
}

/** Lage eines Messwerts relativ zum Zielkorridor. */
enum class ZonePosition { ZU_NIEDRIG, IM_ZIEL, ZU_HOCH }

/** Vom Nutzer benannte Übung. Die Sensorbelegung kommt von einer Vorlage. */
data class CustomExercise(
    val id: String,
    val name: String,
    val templateId: String,
)

/** Übungskatalog des Prototyps. Später aus der lokalen Datenbank. TODO(Berkay) */
object ExerciseCatalog {

    val bankdruecken = Exercise(
        id = "bankdruecken",
        displayName = "Bankdrücken",
        channels = listOf(
            ChannelMapping(0, MuscleGroup.BRUST, MuscleRole.ZIELMUSKEL),
            ChannelMapping(1, MuscleGroup.TRIZEPS, MuscleRole.HILFSMUSKEL),
            ChannelMapping(2, MuscleGroup.SCHULTER_VORNE, MuscleRole.HILFSMUSKEL),
            ChannelMapping(3, MuscleGroup.RUMPF, MuscleRole.BEOBACHTUNG),
        ),
    )

    val bizepscurl = Exercise(
        id = "bizepscurl",
        displayName = "Bizepscurl",
        channels = listOf(
            ChannelMapping(0, MuscleGroup.BIZEPS, MuscleRole.ZIELMUSKEL),
            ChannelMapping(1, MuscleGroup.SCHULTER_VORNE, MuscleRole.HILFSMUSKEL),
            ChannelMapping(2, MuscleGroup.RUMPF, MuscleRole.HILFSMUSKEL),
            ChannelMapping(3, MuscleGroup.LATISSIMUS, MuscleRole.BEOBACHTUNG),
        ),
        maxSupportRatio = 0.4f,
    )

    val kniebeuge = Exercise(
        id = "kniebeuge",
        displayName = "Kniebeuge",
        channels = listOf(
            ChannelMapping(0, MuscleGroup.OBERSCHENKEL_VORNE, MuscleRole.ZIELMUSKEL),
            ChannelMapping(1, MuscleGroup.GESAESS, MuscleRole.ZIELMUSKEL),
            ChannelMapping(2, MuscleGroup.OBERSCHENKEL_HINTEN, MuscleRole.HILFSMUSKEL),
            ChannelMapping(3, MuscleGroup.RUMPF, MuscleRole.BEOBACHTUNG),
        ),
        targetZone = ActivationZone(55f, 90f),
    )

    val schulterdruecken = Exercise(
        id = "schulterdruecken",
        displayName = "Schulterdrücken",
        channels = listOf(
            ChannelMapping(0, MuscleGroup.SCHULTER_VORNE, MuscleRole.ZIELMUSKEL),
            ChannelMapping(1, MuscleGroup.TRIZEPS, MuscleRole.HILFSMUSKEL),
            ChannelMapping(2, MuscleGroup.RUMPF, MuscleRole.HILFSMUSKEL),
            ChannelMapping(3, MuscleGroup.BRUST, MuscleRole.BEOBACHTUNG),
        ),
    )

    val rudern = Exercise(
        id = "rudern",
        displayName = "Rudern",
        channels = listOf(
            ChannelMapping(0, MuscleGroup.LATISSIMUS, MuscleRole.ZIELMUSKEL),
            ChannelMapping(1, MuscleGroup.BIZEPS, MuscleRole.HILFSMUSKEL),
            ChannelMapping(2, MuscleGroup.RUMPF, MuscleRole.HILFSMUSKEL),
            ChannelMapping(3, MuscleGroup.TRIZEPS, MuscleRole.BEOBACHTUNG),
        ),
    )

    val wadenheben = Exercise(
        id = "wadenheben",
        displayName = "Wadenheben",
        channels = listOf(
            ChannelMapping(0, MuscleGroup.WADE, MuscleRole.ZIELMUSKEL),
            ChannelMapping(1, MuscleGroup.OBERSCHENKEL_HINTEN, MuscleRole.HILFSMUSKEL),
            ChannelMapping(2, MuscleGroup.GESAESS, MuscleRole.BEOBACHTUNG),
            ChannelMapping(3, MuscleGroup.RUMPF, MuscleRole.BEOBACHTUNG),
        ),
        targetZone = ActivationZone(50f, 90f),
    )

    val all = listOf(bankdruecken, bizepscurl, kniebeuge, schulterdruecken, rudern, wadenheben)

    fun byId(id: String): Exercise = all.firstOrNull { it.id == id } ?: bankdruecken

    fun aufloesen(id: String, eigene: List<CustomExercise>): Exercise {
        val eigen = eigene.firstOrNull { it.id == id }
        return if (eigen != null) {
            byId(eigen.templateId).copy(id = eigen.id, displayName = eigen.name)
        } else {
            byId(id)
        }
    }

    fun alleMitEigenen(eigene: List<CustomExercise>): List<Exercise> =
        all + eigene.map { byId(it.templateId).copy(id = it.id, displayName = it.name) }
}
