package at.htl.gibagym

import android.content.Context
import at.htl.gibagym.core.engine.store.InMemorySettingsRepository
import at.htl.gibagym.core.model.AppSettings
import at.htl.gibagym.core.model.CustomExercise
import at.htl.gibagym.core.model.PaletteChoice
import at.htl.gibagym.core.model.SettingsRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * Hält Name, Farbwahl und Übungen über einen Neustart, alles andere nur in der Sitzung.
 *
 * Onboarding und Messdaten bleiben bewusst flüchtig: Der Prototyp soll sich nach
 * einem Prozessende wieder von vorn führen lassen. Name, Farben und gewählte
 * Übungen sollen dagegen erhalten bleiben.
 */
class PersistentSettingsRepository(context: Context) : SettingsRepository {

    private val prefs = context.applicationContext.getSharedPreferences(DATEI, Context.MODE_PRIVATE)
    private val inner = InMemorySettingsRepository(laden())

    override val settings: StateFlow<AppSettings> = inner.settings

    override fun update(transform: (AppSettings) -> AppSettings) {
        inner.update { aktuell ->
            val neu = transform(aktuell)
            speichern(neu)
            neu
        }
    }

    private fun laden(): AppSettings {
        val paletteName = prefs.getString(KEY_PALETTE, PaletteChoice.STANDARD.name)
        val palette = PaletteChoice.entries.firstOrNull { it.name == paletteName } ?: PaletteChoice.STANDARD
        return AppSettings(
            displayName = prefs.getString(KEY_NAME, "").orEmpty(),
            palette = palette,
            eigenWenigArgb = prefs.getInt(KEY_WENIG, AppSettings.DEFAULT_EIGEN_WENIG),
            eigenZielArgb = prefs.getInt(KEY_ZIEL, AppSettings.DEFAULT_EIGEN_ZIEL),
            eigenHochArgb = prefs.getInt(KEY_HOCH, AppSettings.DEFAULT_EIGEN_HOCH),
            selectedExerciseId = prefs.getString(KEY_UEBUNG, AppSettings().selectedExerciseId).orEmpty(),
            customExercises = eigeneLesen(prefs.getString(KEY_EIGENE, "").orEmpty()),
        )
    }

    private fun speichern(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_NAME, settings.displayName)
            .putString(KEY_PALETTE, settings.palette.name)
            .putInt(KEY_WENIG, settings.eigenWenigArgb)
            .putInt(KEY_ZIEL, settings.eigenZielArgb)
            .putInt(KEY_HOCH, settings.eigenHochArgb)
            .putString(KEY_UEBUNG, settings.selectedExerciseId)
            .putString(KEY_EIGENE, eigeneSchreiben(settings.customExercises))
            .apply()
    }

    private fun eigeneLesen(roh: String): List<CustomExercise> =
        roh.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { zeile ->
                val teile = zeile.split('\t')
                if (teile.size == 3) CustomExercise(teile[0], teile[1], teile[2]) else null
            }
            .toList()

    private fun eigeneSchreiben(eigene: List<CustomExercise>): String =
        eigene.joinToString("\n") { "${it.id}\t${it.name}\t${it.templateId}" }

    private companion object {
        const val DATEI = "gibagym_profil"
        const val KEY_NAME = "displayName"
        const val KEY_PALETTE = "palette"
        const val KEY_WENIG = "eigenWenig"
        const val KEY_ZIEL = "eigenZiel"
        const val KEY_HOCH = "eigenHoch"
        const val KEY_UEBUNG = "selectedExercise"
        const val KEY_EIGENE = "customExercises"
    }
}
