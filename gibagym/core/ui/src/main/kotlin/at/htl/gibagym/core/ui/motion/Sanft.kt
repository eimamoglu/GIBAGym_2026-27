package at.htl.gibagym.core.ui.motion

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * Gemeinsame Bewegung für Seitenwechsel und Zahlen.
 *
 * Feder statt fester Dauer: Ändert sich das Ziel unterwegs, läuft die Bewegung
 * weiter, statt abzureißen. Ohne Nachschwingen, damit eine Stufe lesbar bleibt.
 */
fun <T> sanft(): FiniteAnimationSpec<T> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

/** Dieselbe Feder, mit einem kurzen Auslauf — für die hervorgehobene Skalenstufe. */
fun <T> sanftMitFeder(): FiniteAnimationSpec<T> = spring(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow,
)
