package at.htl.gibagym.feature.calibration

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import at.htl.gibagym.core.engine.render.EmgRenderBuffer
import at.htl.gibagym.core.model.ActivationZone
import at.htl.gibagym.core.model.AppSettings
import at.htl.gibagym.core.model.CustomExercise
import at.htl.gibagym.core.model.Exercise
import at.htl.gibagym.core.ui.components.HinweisStreifen
import at.htl.gibagym.core.ui.components.LiveEmgDiagramm
import at.htl.gibagym.core.ui.components.SignalgueteChip
import at.htl.gibagym.core.ui.components.StatischerBalken
import at.htl.gibagym.core.ui.components.UebungAuswahl
import at.htl.gibagym.core.ui.feedback.Aktivierungsstufe
import at.htl.gibagym.core.ui.feedback.paletteFor
import at.htl.gibagym.core.ui.motion.sanft
import at.htl.gibagym.core.ui.motion.sanftMitFeder
import at.htl.gibagym.core.ui.render.rememberFrameTicker
import at.htl.gibagym.core.ui.text.label

/**
 * Geführte MVC-Kalibrierung (/LF20/).
 *
 * Die vier Schritte sind bewusst einzelne Vollbilder statt eines Formulars:
 * Wer gerade mit voller Kraft anspannt, kann keine Bedienoberfläche lesen. In
 * der Aufnahmephase steht deshalb genau eine große Aussage auf dem Bildschirm.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(
    state: CalibrationUiState,
    renderBuffer: EmgRenderBuffer,
    modifier: Modifier = Modifier,
    settings: AppSettings = AppSettings(),
    onStarten: () -> Unit = {},
    onAbbrechen: () -> Unit = {},
    onUebernehmen: () -> Unit = {},
    onFertig: () -> Unit = {},
    onUebungWaehlen: (Exercise) -> Unit = {},
    onEigenAnlegen: (String, String) -> Unit = { _, _ -> },
    onEigenLoeschen: (String) -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (settings.displayName.isBlank()) {
                                stringResource(R.string.kalibrierung_titel)
                            } else {
                                stringResource(R.string.kalibrierung_hallo, settings.displayName)
                            },
                        )
                        Text(
                            text = stringResource(R.string.kalibrierung_untertitel),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = state.schritt,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            transitionSpec = {
                val vorwaerts = targetState.ordinal >= initialState.ordinal
                val richtung = if (vorwaerts) 1 else -1
                (
                    fadeIn(tween(280)) +
                        slideInVertically(sanft()) { voll -> voll / 8 * richtung }
                    ).togetherWith(
                    fadeOut(tween(180)) +
                        slideOutVertically(tween(180)) { voll -> -voll / 10 * richtung },
                )
            },
            label = "kalibrierung",
        ) { schritt ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                when (schritt) {
                    KalibrierSchritt.ERKLAERUNG -> Erklaerung(
                        state = state,
                        eigene = settings.customExercises,
                        onStarten = onStarten,
                        onUebungWaehlen = onUebungWaehlen,
                        onEigenAnlegen = onEigenAnlegen,
                        onEigenLoeschen = onEigenLoeschen,
                    )
                    KalibrierSchritt.COUNTDOWN -> Countdown(state, onAbbrechen)
                    KalibrierSchritt.AUFNAHME -> Aufnahme(state, renderBuffer, settings, onAbbrechen)
                    KalibrierSchritt.ERGEBNIS -> Ergebnis(state, onStarten, onUebernehmen, onFertig)
                }
            }
        }
    }
}

@Composable
private fun Erklaerung(
    state: CalibrationUiState,
    eigene: List<CustomExercise>,
    onStarten: () -> Unit,
    onUebungWaehlen: (Exercise) -> Unit,
    onEigenAnlegen: (String, String) -> Unit,
    onEigenLoeschen: (String) -> Unit,
) {
    UebungAuswahl(
        aktuell = state.exercise,
        eigene = eigene,
        onWaehlen = onUebungWaehlen,
        onEigenAnlegen = onEigenAnlegen,
        onEigenLoeschen = onEigenLoeschen,
    )
    Text(
        text = stringResource(R.string.kalibrierung_erklaerung),
        style = MaterialTheme.typography.bodyLarge,
    )
    HinweisStreifen(text = stringResource(R.string.kalibrierung_hinweis_sicherheit))

    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.exercise.channels.forEach { mapping ->
                Text(
                    text = mapping.muscle.label(),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }

    Button(
        onClick = onStarten,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
    ) {
        Text(stringResource(R.string.kalibrierung_starten), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Countdown(state: CalibrationUiState, onAbbrechen: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AnimatedContent(
            targetState = state.countdownSekunden,
            transitionSpec = {
                (fadeIn(tween(180)) + scaleIn(initialScale = 0.82f, animationSpec = sanftMitFeder()))
                    .togetherWith(fadeOut(tween(120)) + scaleOut(targetScale = 1.08f, animationSpec = tween(120)))
            },
            label = "countdown",
        ) { sekunden ->
            Text(
                text = sekunden.toString(),
                style = MaterialTheme.typography.displayLarge,
            )
        }
        Text(
            text = stringResource(R.string.kalibrierung_countdown, state.countdownSekunden),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.kalibrierung_countdown_hinweis),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        AbbrechenSchaltflaeche(onAbbrechen)
    }
}

@Composable
private fun Aufnahme(
    state: CalibrationUiState,
    renderBuffer: EmgRenderBuffer,
    settings: AppSettings,
    onAbbrechen: () -> Unit,
) {
    val ticker = rememberFrameTicker()
    val farben = paletteFor(settings)
    val zone = ActivationZone(60f, 95f)
    val gesamt = CalibrationViewModel.AUFNAHME_SEKUNDEN
    val aufgenommen = (gesamt - state.restsekunden).coerceIn(0, gesamt)

    Text(
        text = stringResource(R.string.kalibrierung_aufnahme),
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    AnimatedContent(
        targetState = state.restsekunden,
        transitionSpec = {
            (fadeIn(tween(180)) + scaleIn(initialScale = 0.9f, animationSpec = sanft()))
                .togetherWith(fadeOut(tween(120)))
        },
        label = "restzeit",
    ) { rest ->
        Text(
            text = rest.toString(),
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
    Text(
        text = stringResource(R.string.kalibrierung_restzeit, state.restsekunden),
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = stringResource(R.string.kalibrierung_fortschritt, aufgenommen, gesamt),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    StatischerBalken(
        anteil = aufgenommen / gesamt.toFloat(),
        farbe = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .height(16.dp),
    )
    SignalgueteChip(state.signalQuality)

    LiveEmgDiagramm(
        ticker = ticker,
        buffer = renderBuffer,
        channelIndex = state.exercise.targetChannels.firstOrNull()?.channelIndex ?: 0,
        zone = zone,
        palette = farben,
        maxPercent = 150f,
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
    )

    state.exercise.channels.forEach { mapping ->
        val aktuell = state.aktuelleMillivolt.getOrElse(mapping.channelIndex) { 0f }
        val spitze = state.spitzenMillivolt.getOrElse(mapping.channelIndex) { 0f }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(mapping.muscle.label(), style = MaterialTheme.typography.titleMedium)
            StatischerBalken(
                anteil = if (spitze > 0f) aktuell / spitze else 0f,
                farbe = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp),
            )
        }
    }

    AbbrechenSchaltflaeche(onAbbrechen)
}

@Composable
private fun Ergebnis(
    state: CalibrationUiState,
    onWiederholen: () -> Unit,
    onUebernehmen: () -> Unit,
    onFertig: () -> Unit,
) {
    Text(stringResource(R.string.kalibrierung_ergebnis), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.kalibrierung_ergebnis_hinweis), style = MaterialTheme.typography.bodyLarge)

    if (state.verdaechtigNiedrig) {
        HinweisStreifen(
            text = stringResource(R.string.kalibrierung_zu_schwach),
            hintergrund = MaterialTheme.colorScheme.errorContainer,
            vordergrund = MaterialTheme.colorScheme.onErrorContainer,
        )
    }

    val staerkste = state.spitzenMillivolt.maxOrNull()?.takeIf { it > 0f } ?: 1f
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.exercise.channels.forEach { mapping ->
                val spitze = state.spitzenMillivolt.getOrElse(mapping.channelIndex) { 0f }
                val stufe = Aktivierungsstufe.von((spitze / staerkste) * 100f)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = mapping.muscle.label(),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(R.string.kalibrierung_stufe, stufe),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }

    if (state.gespeichert) {
        HinweisStreifen(text = stringResource(R.string.kalibrierung_gespeichert))
        Button(
            onClick = onFertig,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp),
        ) {
            Text(stringResource(R.string.kalibrierung_weiter), style = MaterialTheme.typography.titleMedium)
        }
    } else {
        Button(
            onClick = onUebernehmen,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp),
        ) {
            Text(stringResource(R.string.kalibrierung_uebernehmen), style = MaterialTheme.typography.titleMedium)
        }
    }

    OutlinedButton(
        onClick = onWiederholen,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
    ) {
        Text(stringResource(R.string.kalibrierung_wiederholen))
    }
}

@Composable
private fun AbbrechenSchaltflaeche(onAbbrechen: () -> Unit) {
    OutlinedButton(
        onClick = onAbbrechen,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
    ) {
        Text(stringResource(R.string.kalibrierung_abbrechen))
    }
}

