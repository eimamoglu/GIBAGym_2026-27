package at.htl.gibagym.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import at.htl.gibagym.core.model.AppSettings
import at.htl.gibagym.core.model.CustomExercise
import at.htl.gibagym.core.model.Exercise
import at.htl.gibagym.core.model.ExerciseCatalog
import at.htl.gibagym.core.ui.R

/**
 * Große Schaltflächen zum Wählen der Übung, plus ein Feld für einen eigenen Namen.
 *
 * Die App erkennt die Übung nicht aus dem Signal. Du sagst ihr, was du machst.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UebungAuswahl(
    aktuell: Exercise,
    eigene: List<CustomExercise>,
    onWaehlen: (Exercise) -> Unit,
    onEigenAnlegen: (name: String, templateId: String) -> Unit,
    modifier: Modifier = Modifier,
    onEigenLoeschen: (String) -> Unit = {},
    aktiv: Boolean = true,
) {
    var neuerName by rememberSaveable { mutableStateOf("") }
    var vorlageId by rememberSaveable { mutableStateOf(ExerciseCatalog.bankdruecken.id) }
    val name = AppSettings.nameBereinigen(neuerName)
    val uebungen = ExerciseCatalog.alleMitEigenen(eigene)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.uebung_frage), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.uebung_hinweis),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        uebungen.forEach { uebung ->
            val gewaehlt = aktuell.id == uebung.id
            val knopf = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
            if (gewaehlt) {
                Button(
                    onClick = { if (aktiv) onWaehlen(uebung) },
                    enabled = aktiv,
                    modifier = knopf,
                ) {
                    Text(uebung.displayName, style = MaterialTheme.typography.titleMedium)
                }
            } else {
                OutlinedButton(
                    onClick = { if (aktiv) onWaehlen(uebung) },
                    enabled = aktiv,
                    modifier = knopf,
                ) {
                    Text(uebung.displayName, style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        val gewaehlteEigene = eigene.firstOrNull { it.id == aktuell.id }
        if (aktiv && gewaehlteEigene != null) {
            TextButton(
                onClick = { onEigenLoeschen(gewaehlteEigene.id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.uebung_loeschen), style = MaterialTheme.typography.titleMedium)
            }
        }

        if (aktiv) {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.uebung_eigene), style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = neuerName,
                        onValueChange = { if (it.length <= AppSettings.NAME_MAX_LAENGE) neuerName = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp),
                        textStyle = MaterialTheme.typography.titleLarge,
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.uebung_eigene_hint)) },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (name.isNotEmpty()) {
                                    onEigenAnlegen(name, vorlageId)
                                    neuerName = ""
                                }
                            },
                        ),
                    )
                    Text(stringResource(R.string.uebung_vorlage), style = MaterialTheme.typography.bodyLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ExerciseCatalog.all.forEach { vorlage ->
                            FilterChip(
                                selected = vorlageId == vorlage.id,
                                onClick = { vorlageId = vorlage.id },
                                modifier = Modifier.heightIn(min = 48.dp),
                                label = { Text(vorlage.displayName) },
                            )
                        }
                    }
                    Button(
                        onClick = {
                            onEigenAnlegen(name, vorlageId)
                            neuerName = ""
                        },
                        enabled = name.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp),
                    ) {
                        Text(stringResource(R.string.uebung_hinzufuegen), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

/**
 * Zeigt die aktuelle Übung als großen Knopf. Die Liste öffnet sich erst beim Tippen,
 * damit Training nur Avatar und Skala im Blick behält.
 */
@Composable
fun UebungWahlKnopf(
    aktuell: Exercise,
    eigene: List<CustomExercise>,
    onWaehlen: (Exercise) -> Unit,
    onEigenAnlegen: (name: String, templateId: String) -> Unit,
    modifier: Modifier = Modifier,
    onEigenLoeschen: (String) -> Unit = {},
    aktiv: Boolean = true,
) {
    var offen by rememberSaveable { mutableStateOf(false) }

    OutlinedButton(
        onClick = { if (aktiv) offen = true },
        enabled = aktiv,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
    ) {
        Text(
            text = aktuell.displayName,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Start,
        )
        Icon(
            imageVector = Icons.Default.ArrowDropDown,
            contentDescription = stringResource(R.string.uebung_wechseln),
        )
    }

    if (offen) {
        Dialog(
            onDismissRequest = { offen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f)
                    .padding(16.dp),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        UebungAuswahl(
                            aktuell = aktuell,
                            eigene = eigene,
                            onWaehlen = { uebung ->
                                onWaehlen(uebung)
                                offen = false
                            },
                            onEigenAnlegen = { name, vorlage ->
                                onEigenAnlegen(name, vorlage)
                                offen = false
                            },
                            onEigenLoeschen = onEigenLoeschen,
                        )
                    }
                    Button(
                        onClick = { offen = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .padding(top = 12.dp),
                    ) {
                        Text(stringResource(R.string.uebung_fertig), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
