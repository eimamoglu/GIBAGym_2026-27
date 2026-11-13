package at.htl.gibagym.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import at.htl.gibagym.core.model.EmgSourceKind
import at.htl.gibagym.core.model.LatencySnapshot
import at.htl.gibagym.core.model.SignalQuality
import at.htl.gibagym.core.ui.R
import at.htl.gibagym.core.ui.text.label
import kotlin.math.roundToInt

/** Kleiner Statusbaustein mit Symbol und Text. Symbol nie allein — immer mit Text. */
@Composable
fun StatusChip(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    hintergrund: Color = MaterialTheme.colorScheme.surfaceVariant,
    vordergrund: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    beschreibung: String = text,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(hintergrund)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clearAndSetSemantics { contentDescription = beschreibung },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = vordergrund, modifier = Modifier.size(20.dp))
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = vordergrund)
    }
}

/** Zeigt die Signalgüte. Bei Problemen wechselt die Farbe **und** das Symbol. */
@Composable
fun SignalgueteChip(quality: SignalQuality, modifier: Modifier = Modifier) {
    val (hintergrund, vordergrund) = when (quality) {
        SignalQuality.GOOD -> MaterialTheme.colorScheme.secondaryContainer to
            MaterialTheme.colorScheme.onSecondaryContainer
        SignalQuality.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant to
            MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    StatusChip(
        text = quality.label(),
        icon = if (quality == SignalQuality.GOOD) Icons.Default.CheckCircle else Icons.Default.Warning,
        modifier = modifier,
        hintergrund = hintergrund,
        vordergrund = vordergrund,
    )
}

/** Zeigt, woher die Daten kommen — im Demo-Modus die wichtigste Statusangabe. */
@Composable
fun QuellenChip(kind: EmgSourceKind, modifier: Modifier = Modifier) {
    val icon = when (kind) {
        EmgSourceKind.SIMULATION -> Icons.Default.Timeline
        EmgSourceKind.REPLAY -> Icons.Default.PlayCircle
        EmgSourceKind.BLE -> Icons.Default.BluetoothDisabled
    }
    StatusChip(
        text = kind.label(),
        icon = icon,
        modifier = modifier,
        hintergrund = MaterialTheme.colorScheme.primaryContainer,
        vordergrund = MaterialTheme.colorScheme.onPrimaryContainer,
    )
}

/**
 * Die gemessene Ende-zu-Ende-Latenz.
 *
 * Steht bewusst sichtbar in der Trainingsansicht und nicht in einem versteckten
 * Entwicklermenü: Die Aussage "Feedback in unter 100 ms" ist Teil des Lastenhefts,
 * und sie ist nur dann belastbar, wenn sie während der Vorführung mitläuft.
 */
@Composable
fun LatenzAnzeige(snapshot: LatencySnapshot, modifier: Modifier = Modifier) {
    val imBudget = snapshot.withinBudget
    val beschreibung = stringResource(R.string.latenz_titel) + ": " +
        stringResource(
            R.string.latenz_detail,
            snapshot.lastMillis.roundToInt(),
            snapshot.averageMillis.roundToInt(),
            snapshot.maxMillis.roundToInt(),
        ) + ", " +
        if (imBudget) {
            stringResource(R.string.latenz_im_budget, snapshot.budgetMillis.roundToInt())
        } else {
            stringResource(R.string.latenz_ueber_budget, snapshot.budgetMillis.roundToInt())
        }

    StatusChip(
        text = stringResource(
            R.string.latenz_detail,
            snapshot.lastMillis.roundToInt(),
            snapshot.averageMillis.roundToInt(),
            snapshot.maxMillis.roundToInt(),
        ),
        icon = Icons.Default.Speed,
        modifier = modifier,
        hintergrund = if (imBudget) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.errorContainer
        },
        vordergrund = if (imBudget) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onErrorContainer
        },
        beschreibung = beschreibung,
    )
}
