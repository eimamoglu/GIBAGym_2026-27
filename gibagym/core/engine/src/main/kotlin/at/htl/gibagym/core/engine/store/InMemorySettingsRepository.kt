package at.htl.gibagym.core.engine.store

import at.htl.gibagym.core.model.AppSettings
import at.htl.gibagym.core.model.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Einstellungen im Arbeitsspeicher.
 *
 * TODO(Berkay): Auf Jetpack DataStore (Preferences) umstellen, damit die Wahl von
 *  Farbskala, Glättung und Demo-Modus einen Neustart überlebt. Die Schnittstelle
 *  [SettingsRepository] gibt bereits einen StateFlow zurück und passt damit
 *  unverändert auf DataStore.
 */
class InMemorySettingsRepository(
    initial: AppSettings = AppSettings(),
) : SettingsRepository {

    private val state = MutableStateFlow(initial)

    override val settings: StateFlow<AppSettings> = state.asStateFlow()

    override fun update(transform: (AppSettings) -> AppSettings) {
        state.update(transform)
    }
}
