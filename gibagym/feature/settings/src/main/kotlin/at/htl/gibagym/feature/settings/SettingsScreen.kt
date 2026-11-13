package at.htl.gibagym.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import at.htl.gibagym.core.model.ActivationZone
import at.htl.gibagym.core.model.AppSettings
import at.htl.gibagym.core.model.EmgSourceKind
import at.htl.gibagym.core.model.LatencySnapshot
import at.htl.gibagym.core.model.PaletteChoice
import at.htl.gibagym.core.model.SmoothingStrategy
import at.htl.gibagym.core.ui.components.HinweisStreifen
import at.htl.gibagym.core.ui.feedback.ActivationPalette
import at.htl.gibagym.core.ui.feedback.farbenZuAehnlich
import at.htl.gibagym.core.ui.feedback.paletteFor
import at.htl.gibagym.core.ui.text.label
import kotlin.math.roundToInt

/**
 * Einstellungen: Demo-Modus, Barrierefreiheit und Glättung.
 *
 * Die Glättungsparameter stehen bewusst in der normalen Oberfläche und nicht in
 * einem versteckten Entwicklermenü. Sie sind das Messinstrument der Diplomarbeit:
 * Verfahren und Fensterlänge lassen sich während einer laufenden Messung
 * umschalten, und die rechnerische Verzögerung steht direkt daneben.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    modifier: Modifier = Modifier,
    onSettingsChange: (AppSettings) -> Unit = {},
    onZuruecksetzen: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.einstellungen_titel)) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NameAbschnitt(settings, onSettingsChange)
            QuellenAbschnitt(settings) { onSettingsChange(settings.copy(sourceKind = it)) }
            DarstellungAbschnitt(settings, onSettingsChange)
            HaptikAbschnitt(settings, onSettingsChange)
            GlaettungAbschnitt(settings, onSettingsChange)
            DatenAbschnitt(onZuruecksetzen)
        }
    }
}

@Composable
private fun DatenAbschnitt(onZuruecksetzen: () -> Unit) {
    var nachfragen by rememberSaveable { mutableStateOf(false) }

    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.einstellungen_datenschutz_titel),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.einstellungen_datenschutz_text),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                stringResource(R.string.einstellungen_zuruecksetzen_text),
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(
                onClick = { nachfragen = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(stringResource(R.string.einstellungen_zuruecksetzen), style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    if (nachfragen) {
        AlertDialog(
            onDismissRequest = { nachfragen = false },
            title = { Text(stringResource(R.string.einstellungen_zuruecksetzen_frage)) },
            text = { Text(stringResource(R.string.einstellungen_zuruecksetzen_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        nachfragen = false
                        onZuruecksetzen()
                    },
                ) {
                    Text(stringResource(R.string.einstellungen_zuruecksetzen))
                }
            },
            dismissButton = {
                TextButton(onClick = { nachfragen = false }) {
                    Text(stringResource(R.string.einstellungen_zuruecksetzen_abbrechen))
                }
            },
        )
    }
}

@Composable
private fun NameAbschnitt(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    var text by rememberSaveable(settings.displayName) { mutableStateOf(settings.displayName) }

    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.einstellungen_name_titel), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = text,
                onValueChange = { neu ->
                    if (neu.length <= AppSettings.NAME_MAX_LAENGE) {
                        text = neu
                        val bereinigt = AppSettings.nameBereinigen(neu)
                        if (bereinigt.isNotEmpty()) onChange(settings.copy(displayName = bereinigt))
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp),
                textStyle = MaterialTheme.typography.titleLarge,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
            )
        }
    }
}

@Composable
private fun QuellenAbschnitt(settings: AppSettings, onWechsel: (EmgSourceKind) -> Unit) {
    Card {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.einstellungen_quelle_titel), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.einstellungen_quelle_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            EmgSourceKind.entries.forEach { kind ->
                AuswahlZeile(
                    titel = kind.label(),
                    beschreibung = stringResource(
                        when (kind) {
                            EmgSourceKind.SIMULATION -> R.string.einstellungen_quelle_simulation_detail
                            EmgSourceKind.REPLAY -> R.string.einstellungen_quelle_replay_detail
                            EmgSourceKind.BLE -> R.string.einstellungen_quelle_ble_detail
                        },
                    ),
                    ausgewaehlt = settings.sourceKind == kind,
                    onKlick = { onWechsel(kind) },
                )
            }
        }
    }
}

@Composable
private fun DarstellungAbschnitt(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    val wenig = Color(settings.eigenWenigArgb)
    val ziel = Color(settings.eigenZielArgb)
    val hoch = Color(settings.eigenHochArgb)
    val aehnlich = farbenZuAehnlich(wenig, ziel) || farbenZuAehnlich(ziel, hoch)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.einstellungen_darstellung_titel), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.einstellungen_palette), style = MaterialTheme.typography.bodyLarge)

        PaletteVorgabe(
            titel = stringResource(R.string.einstellungen_palette_standard),
            choice = PaletteChoice.STANDARD,
            ausgewaehlt = settings.palette == PaletteChoice.STANDARD,
            onKlick = { onChange(settings.copy(palette = PaletteChoice.STANDARD)) },
        )
        PaletteVorgabe(
            titel = stringResource(R.string.einstellungen_palette_sicher),
            choice = PaletteChoice.ROT_GRUEN_SICHER,
            ausgewaehlt = settings.palette == PaletteChoice.ROT_GRUEN_SICHER,
            onKlick = { onChange(settings.copy(palette = PaletteChoice.ROT_GRUEN_SICHER)) },
        )
        PaletteVorgabe(
            titel = stringResource(R.string.einstellungen_palette_monochrom),
            choice = PaletteChoice.MONOCHROM,
            ausgewaehlt = settings.palette == PaletteChoice.MONOCHROM,
            onKlick = { onChange(settings.copy(palette = PaletteChoice.MONOCHROM)) },
        )

        Card(
            border = if (settings.palette == PaletteChoice.EIGEN) {
                BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
            } else {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            },
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.einstellungen_palette_eigen), style = MaterialTheme.typography.titleMedium)
                PalettenVorschau(paletteFor(settings.copy(palette = PaletteChoice.EIGEN)))
                Farbzeile(
                    titel = stringResource(R.string.einstellungen_farbe_wenig),
                    farben = FARBEN_WENIG,
                    gewaehlt = settings.eigenWenigArgb,
                    onWahl = { farbe ->
                        onChange(settings.copy(palette = PaletteChoice.EIGEN, eigenWenigArgb = farbe))
                    },
                )
                Farbzeile(
                    titel = stringResource(R.string.einstellungen_farbe_ziel),
                    farben = FARBEN_ZIEL,
                    gewaehlt = settings.eigenZielArgb,
                    onWahl = { farbe ->
                        onChange(settings.copy(palette = PaletteChoice.EIGEN, eigenZielArgb = farbe))
                    },
                )
                Farbzeile(
                    titel = stringResource(R.string.einstellungen_farbe_hoch),
                    farben = FARBEN_HOCH,
                    gewaehlt = settings.eigenHochArgb,
                    onWahl = { farbe ->
                        onChange(settings.copy(palette = PaletteChoice.EIGEN, eigenHochArgb = farbe))
                    },
                )
                if (settings.palette == PaletteChoice.EIGEN && aehnlich) {
                    HinweisStreifen(text = stringResource(R.string.einstellungen_farben_aehnlich))
                }
            }
        }

        HinweisStreifen(text = stringResource(R.string.einstellungen_palette_hinweis))
    }
}

@Composable
private fun PaletteVorgabe(
    titel: String,
    choice: PaletteChoice,
    ausgewaehlt: Boolean,
    onKlick: () -> Unit,
) {
    Card(
        onClick = onKlick,
        border = if (ausgewaehlt) {
            BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        },
        colors = CardDefaults.cardColors(
            containerColor = if (ausgewaehlt) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(titel, style = MaterialTheme.typography.titleMedium)
            PalettenVorschau(paletteFor(choice))
        }
    }
}

/**
 * Vier Farbfelder von Ruhe bis Überlastung.
 *
 * Die Vorschau ist nötig, weil der Name einer Palette nichts darüber aussagt,
 * ob jemand die Farben unterscheiden kann. Wer sie nicht unterscheiden kann,
 * sieht das hier sofort — vor dem Training statt mitten darin.
 */
@Composable
private fun Farbzeile(
    titel: String,
    farben: List<Int>,
    gewaehlt: Int,
    onWahl: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(titel, style = MaterialTheme.typography.titleMedium)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            farben.chunked(4).forEach { reihe ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    reihe.forEach { argb ->
                        val ausgewaehlt = argb == gewaehlt
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .border(
                                    width = if (ausgewaehlt) 4.dp else 1.dp,
                                    color = if (ausgewaehlt) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    },
                                    shape = CircleShape,
                                )
                                .clickable { onWahl(argb) }
                                .semantics { contentDescription = titel },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PalettenVorschau(palette: ActivationPalette) {
    val zone = ActivationZone.DEFAULT
    val beschreibung = stringResource(R.string.einstellungen_palette_vorschau)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .semantics { contentDescription = beschreibung },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(5f, 45f, 80f, 115f).forEach { wert ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(palette.colorFor(wert, zone)),
            )
        }
    }
}

@Composable
private fun HaptikAbschnitt(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SchalterZeile(
                titel = stringResource(R.string.einstellungen_haptik_titel),
                beschreibung = stringResource(R.string.einstellungen_haptik_text),
                aktiv = settings.hapticsEnabled,
                onChange = { onChange(settings.copy(hapticsEnabled = it)) },
            )
            SchalterZeile(
                titel = stringResource(R.string.einstellungen_latenz_titel),
                beschreibung = stringResource(R.string.einstellungen_latenz_text),
                aktiv = settings.showLatencyOverlay,
                onChange = { onChange(settings.copy(showLatencyOverlay = it)) },
            )
        }
    }
}

@Composable
private fun GlaettungAbschnitt(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    val verzoegerung = settings.smoothing.expectedDelayMillis(ABTASTRATE_HZ)

    Card {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.einstellungen_glaettung_titel), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.einstellungen_glaettung_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SmoothingStrategy.entries.forEach { strategy ->
                AuswahlZeile(
                    titel = stringResource(
                        when (strategy) {
                            SmoothingStrategy.KEINE -> R.string.einstellungen_glaettung_keine
                            SmoothingStrategy.GLEITENDER_RMS -> R.string.einstellungen_glaettung_rms
                            SmoothingStrategy.EXPONENTIELL -> R.string.einstellungen_glaettung_ema
                            SmoothingStrategy.MEDIAN -> R.string.einstellungen_glaettung_median
                        },
                    ),
                    beschreibung = null,
                    ausgewaehlt = settings.smoothing.strategy == strategy,
                    onKlick = { onChange(settings.copy(smoothing = settings.smoothing.copy(strategy = strategy))) },
                )
            }

            Text(
                text = stringResource(R.string.einstellungen_glaettung_fenster, settings.smoothing.windowSamples),
                style = MaterialTheme.typography.bodyLarge,
            )
            Slider(
                value = settings.smoothing.windowSamples.toFloat(),
                onValueChange = { wert ->
                    onChange(settings.copy(smoothing = settings.smoothing.copy(windowSamples = wert.roundToInt().coerceAtLeast(1))))
                },
                valueRange = 5f..300f,
                steps = 58,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            )
            Text(
                text = stringResource(
                    R.string.einstellungen_glaettung_verzoegerung,
                    verzoegerung.roundToInt(),
                    LatencySnapshot.BUDGET_MILLIS.roundToInt(),
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = if (verzoegerung > LatencySnapshot.BUDGET_MILLIS / 2f) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

@Composable
private fun AuswahlZeile(
    titel: String,
    beschreibung: String?,
    ausgewaehlt: Boolean,
    onKlick: () -> Unit,
    nachInhalt: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = ausgewaehlt, role = Role.RadioButton, onClick = onKlick)
            .padding(vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = ausgewaehlt, onClick = null)
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(titel, style = MaterialTheme.typography.bodyLarge)
                if (beschreibung != null) {
                    Text(
                        text = beschreibung,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        nachInhalt?.invoke()
    }
}

@Composable
private fun SchalterZeile(
    titel: String,
    beschreibung: String,
    aktiv: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(titel, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = beschreibung,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = aktiv, onCheckedChange = onChange)
    }
}

private const val ABTASTRATE_HZ = 1000

private val FARBEN_WENIG = listOf(
    0xFFB9C4CC.toInt(),
    0xFF90CAF9.toInt(),
    0xFFA5D6A7.toInt(),
    0xFFFFE082.toInt(),
    0xFFCE93D8.toInt(),
    0xFF80CBC4.toInt(),
    0xFFFFCC80.toInt(),
    0xFFB0BEC5.toInt(),
)

private val FARBEN_ZIEL = listOf(
    0xFF2E7D32.toInt(),
    0xFF0D47A1.toInt(),
    0xFF00695C.toInt(),
    0xFF6A1B9A.toInt(),
    0xFFEF6C00.toInt(),
    0xFF1565C0.toInt(),
    0xFF00838F.toInt(),
    0xFF4527A0.toInt(),
)

private val FARBEN_HOCH = listOf(
    0xFFC62828.toInt(),
    0xFFBF360C.toInt(),
    0xFFF9A825.toInt(),
    0xFFAD1457.toInt(),
    0xFF6D4C41.toInt(),
    0xFFE65100.toInt(),
    0xFFD81B60.toInt(),
    0xFF4A148C.toInt(),
)
