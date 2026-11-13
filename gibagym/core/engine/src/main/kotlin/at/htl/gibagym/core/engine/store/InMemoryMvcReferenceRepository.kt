package at.htl.gibagym.core.engine.store

import at.htl.gibagym.core.model.MvcReference
import at.htl.gibagym.core.model.MvcReferenceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Kalibrierungsergebnisse im Arbeitsspeicher.
 *
 * TODO(Berkay): In die verschlüsselte Datenbank verlagern und beim App-Start
 *  laden. Zusätzlich sinnvoll: eine Gültigkeitsdauer (z. B. 14 Tage), nach der
 *  die App zur erneuten Kalibrierung auffordert, weil sich Elektrodenposition
 *  und Tagesform ändern.
 */
class InMemoryMvcReferenceRepository : MvcReferenceRepository {

    private val proUebung = mutableMapOf<String, MvcReference>()
    private val state = MutableStateFlow<MvcReference?>(null)

    override val current: StateFlow<MvcReference?> = state.asStateFlow()

    override fun save(reference: MvcReference) {
        proUebung[reference.exerciseId] = reference
        state.value = reference
    }

    override fun forExercise(exerciseId: String): MvcReference? = proUebung[exerciseId]

    override fun clear() {
        proUebung.clear()
        state.value = null
    }
}
