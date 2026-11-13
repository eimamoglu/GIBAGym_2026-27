package at.htl.gibagym.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import at.htl.gibagym.core.model.EmgSourceKind
import at.htl.gibagym.core.ui.components.HinweisStreifen
import at.htl.gibagym.core.ui.motion.sanft
import at.htl.gibagym.core.ui.text.label

/** Die vier Schritte von „Erste Schritte“. */
enum class OnboardingSchritt { WILLKOMMEN, ELEKTRODEN, VERBINDEN, KALIBRIEREN }

/**
 * Geführter Einstieg.
 *
 * Er existiert, weil der schwierigste Teil dieses Systems nicht die App ist,
 * sondern das Anlegen der Elektroden. Sitzen sie falsch, misst das beste
 * Verfahren nur Rauschen — und der Nutzer hält das Produkt für kaputt.
 *
 * Aufbau: farbiger Kopfbereich mit Marke und Schrittanzeige, darunter der
 * Inhalt des Schritts in Karten, am unteren Rand zwei große Schaltflächen.
 */
@Composable
fun OnboardingScreen(
    schritt: OnboardingSchritt,
    gewaehlteQuelle: EmgSourceKind,
    modifier: Modifier = Modifier,
    anzeigename: String = "",
    onWeiter: () -> Unit = {},
    onZurueck: () -> Unit = {},
    onUeberspringen: () -> Unit = {},
    onQuelleWechseln: (EmgSourceKind) -> Unit = {},
    onFertig: () -> Unit = {},
) {
    val schritte = OnboardingSchritt.entries
    val nummer = schritte.indexOf(schritt) + 1
    val letzter = schritt == OnboardingSchritt.KALIBRIEREN

    Scaffold(
        modifier = modifier,
        topBar = { Kopfbereich(schritt, nummer, schritte.size) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            TextButton(
                onClick = onUeberspringen,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 8.dp),
            ) {
                Text(stringResource(R.string.onboarding_ueberspringen))
            }
            AnimatedContent(
                targetState = schritt,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                transitionSpec = {
                    val vorwaerts = targetState.ordinal > initialState.ordinal
                    val richtung = if (vorwaerts) 1 else -1
                    (
                        fadeIn(tween(280)) +
                            slideInHorizontally(sanft()) { voll -> voll / 5 * richtung }
                        ).togetherWith(
                        fadeOut(tween(180)) +
                            slideOutHorizontally(tween(180)) { voll -> -voll / 8 * richtung },
                    )
                },
                label = "onboarding",
            ) { aktueller ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (aktueller) {
                        OnboardingSchritt.WILLKOMMEN -> Willkommen(anzeigename)
                        OnboardingSchritt.ELEKTRODEN -> Elektroden()
                        OnboardingSchritt.VERBINDEN -> Verbinden(gewaehlteQuelle, onQuelleWechseln)
                        OnboardingSchritt.KALIBRIEREN -> Kalibrieren()
                    }
                }
            }

            val weiterGewicht by animateFloatAsState(
                targetValue = if (schritt == OnboardingSchritt.WILLKOMMEN) 1f else 1.4f,
                animationSpec = sanft(),
                label = "weiter",
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AnimatedVisibility(
                    visible = schritt != OnboardingSchritt.WILLKOMMEN,
                    enter = fadeIn(tween(220)) + expandHorizontally(animationSpec = sanft()),
                    exit = fadeOut(tween(160)) + shrinkHorizontally(animationSpec = sanft()),
                ) {
                    OutlinedButton(
                        onClick = onZurueck,
                        modifier = Modifier.heightIn(min = 60.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        Text(
                            text = stringResource(R.string.onboarding_zurueck),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                Button(
                    onClick = if (letzter) onFertig else onWeiter,
                    modifier = Modifier
                        .weight(weiterGewicht)
                        .heightIn(min = 60.dp),
                ) {
                    Text(
                        text = stringResource(if (letzter) R.string.onboarding_fertig else R.string.onboarding_weiter),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Icon(
                        imageVector = if (letzter) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Kopfbereich
// ---------------------------------------------------------------------------

@Composable
private fun Kopfbereich(
    schritt: OnboardingSchritt,
    nummer: Int,
    gesamt: Int,
) {
    val farben = MaterialTheme.colorScheme
    val verlauf = Brush.verticalGradient(
        listOf(farben.primary, lerp(farben.primary, farben.primaryContainer, 0.35f)),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(verlauf)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Markenlogo(modifier = Modifier.width(240.dp))

        Text(
            text = stringResource(R.string.onboarding_untertitel),
            style = MaterialTheme.typography.titleMedium,
            color = farben.onPrimary,
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SchrittAnzeige(nummer = nummer, gesamt = gesamt, farbe = farben.onPrimary)
            Text(
                text = stringResource(R.string.onboarding_schritt, nummer, gesamt) + " · " + schritt.titel(),
                style = MaterialTheme.typography.labelLarge,
                color = farben.onPrimary,
            )
        }
    }
}

/** Vier Segmente statt eines Balkens: Man sieht, wie viele Schritte noch kommen. */
@Composable
private fun SchrittAnzeige(nummer: Int, gesamt: Int, farbe: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(gesamt) { index ->
            val staerke by animateFloatAsState(
                targetValue = if (index < nummer) 1f else 0.3f,
                animationSpec = sanft(),
                label = "schritt-segment",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(farbe.copy(alpha = staerke)),
            )
        }
    }
}

/** Das Markenzeichen. Liegt auf Weiß, damit Strich, Herz und Schrift lesbar bleiben. */
@Composable
internal fun Markenlogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.logo_gibagym),
        contentDescription = stringResource(R.string.onboarding_logo),
        contentScale = ContentScale.Fit,
        modifier = modifier
            .heightIn(min = 96.dp, max = 160.dp)
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun OnboardingSchritt.titel(): String = stringResource(
    when (this) {
        OnboardingSchritt.WILLKOMMEN -> R.string.onboarding_schritt_willkommen
        OnboardingSchritt.ELEKTRODEN -> R.string.onboarding_schritt_elektroden
        OnboardingSchritt.VERBINDEN -> R.string.onboarding_schritt_verbinden
        OnboardingSchritt.KALIBRIEREN -> R.string.onboarding_schritt_kalibrieren
    },
)

// ---------------------------------------------------------------------------
// Schritt 1: Willkommen
// ---------------------------------------------------------------------------

@Composable
private fun Willkommen(anzeigename: String) {
    Text(
        text = if (anzeigename.isBlank()) {
            stringResource(R.string.onboarding_willkommen_titel)
        } else {
            stringResource(R.string.onboarding_willkommen_titel_name, anzeigename)
        },
        style = MaterialTheme.typography.headlineSmall,
    )
    Text(stringResource(R.string.onboarding_willkommen_text), style = MaterialTheme.typography.bodyLarge)

    MerkmalKarte(
        icon = Icons.Default.Sensors,
        titel = stringResource(R.string.onboarding_willkommen_merkmal1_titel),
        text = stringResource(R.string.onboarding_willkommen_merkmal1_text),
    )
    MerkmalKarte(
        icon = Icons.Default.AccessibilityNew,
        titel = stringResource(R.string.onboarding_willkommen_merkmal2_titel),
        text = stringResource(R.string.onboarding_willkommen_merkmal2_text),
    )
    MerkmalKarte(
        icon = Icons.Default.Bolt,
        titel = stringResource(R.string.onboarding_willkommen_merkmal3_titel),
        text = stringResource(R.string.onboarding_willkommen_merkmal3_text),
    )

    HinweisZeile(icon = Icons.Default.Lock, text = stringResource(R.string.onboarding_willkommen_datenschutz))
}

@Composable
private fun MerkmalKarte(icon: ImageVector, titel: String, text: String) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SymbolKreis(icon)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(titel, style = MaterialTheme.typography.titleMedium)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Schritt 2: Elektroden
// ---------------------------------------------------------------------------

@Composable
private fun Elektroden() {
    Text(stringResource(R.string.onboarding_elektroden_titel), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.onboarding_elektroden_text), style = MaterialTheme.typography.bodyLarge)

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ElektrodenSkizze(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                LegendenPunkt(MaterialTheme.colorScheme.primary, stringResource(R.string.onboarding_elektroden_legende_mess))
                LegendenPunkt(MaterialTheme.colorScheme.tertiary, stringResource(R.string.onboarding_elektroden_legende_bezug))
            }
        }
    }

    NummerSchritt(1, stringResource(R.string.onboarding_elektroden_schritt1_titel), stringResource(R.string.onboarding_elektroden_schritt1))
    NummerSchritt(2, stringResource(R.string.onboarding_elektroden_schritt2_titel), stringResource(R.string.onboarding_elektroden_schritt2))
    NummerSchritt(3, stringResource(R.string.onboarding_elektroden_schritt3_titel), stringResource(R.string.onboarding_elektroden_schritt3))
}

@Composable
private fun LegendenPunkt(farbe: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(farbe),
        )
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NummerSchritt(nummer: Int, titel: String, text: String) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = nummer.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(titel, style = MaterialTheme.typography.titleMedium)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Schematische Skizze der Elektrodenlage.
 *
 * Bewusst eine gezeichnete Skizze statt eines Fotos: Eine Skizze zeigt Abstand
 * und Ausrichtung eindeutig, ein Foto zeigt zusätzlich einen fremden Körper und
 * lenkt davon ab.
 */
@Composable
private fun ElektrodenSkizze(modifier: Modifier = Modifier) {
    val muskel = MaterialTheme.colorScheme.surfaceVariant
    val faser = MaterialTheme.colorScheme.outline
    val elektrode = MaterialTheme.colorScheme.primary
    val bezug = MaterialTheme.colorScheme.tertiary
    val beschreibung = stringResource(R.string.onboarding_elektroden_bild)

    Canvas(modifier = modifier.semantics { contentDescription = beschreibung }) {
        val mitteY = size.height / 2f
        val muskelBreite = size.width * 0.64f
        val muskelHoehe = size.height * 0.46f
        val links = size.width * 0.06f
        val oben = mitteY - muskelHoehe / 2f

        drawOval(color = muskel, topLeft = Offset(links, oben), size = Size(muskelBreite, muskelHoehe))
        // Faserrichtung: drei feine Linien entlang des Muskels.
        for (i in 1..3) {
            val y = oben + muskelHoehe * i / 4f
            val einzug = muskelBreite * 0.08f * (if (i == 2) 1f else 2.2f)
            drawLine(
                color = faser.copy(alpha = 0.35f),
                start = Offset(links + einzug, y),
                end = Offset(links + muskelBreite - einzug, y),
                strokeWidth = 2f,
            )
        }
        drawOval(
            color = faser,
            topLeft = Offset(links, oben),
            size = Size(muskelBreite, muskelHoehe),
            style = Stroke(width = 3f),
        )

        // Zwei Messelektroden auf dem Muskelbauch, in Faserrichtung.
        val radius = size.height * 0.09f
        val ersteX = links + muskelBreite * 0.34f
        val zweiteX = links + muskelBreite * 0.62f
        listOf(ersteX, zweiteX).forEach { x ->
            drawCircle(color = elektrode.copy(alpha = 0.25f), radius = radius * 1.35f, center = Offset(x, mitteY))
            drawCircle(color = elektrode, radius = radius, center = Offset(x, mitteY))
            drawCircle(color = Color.White, radius = radius * 0.38f, center = Offset(x, mitteY))
        }
        // Abstandsmarke zwischen den Elektroden.
        val markeY = mitteY + radius * 2.1f
        drawLine(color = faser, start = Offset(ersteX, markeY), end = Offset(zweiteX, markeY), strokeWidth = 3f)
        listOf(ersteX, zweiteX).forEach { x ->
            drawLine(color = faser, start = Offset(x, markeY - 6f), end = Offset(x, markeY + 6f), strokeWidth = 3f)
        }

        // Bezugselektrode am Knochen, deutlich abgesetzt.
        val bezugMitte = Offset(size.width * 0.86f, oben - muskelHoehe * 0.12f)
        drawLine(
            color = faser.copy(alpha = 0.5f),
            start = Offset(links + muskelBreite * 0.9f, oben + muskelHoehe * 0.2f),
            end = bezugMitte,
            strokeWidth = 2f,
        )
        drawCircle(color = bezug.copy(alpha = 0.25f), radius = radius * 1.35f, center = bezugMitte)
        drawCircle(color = bezug, radius = radius, center = bezugMitte)
        drawCircle(color = Color.White, radius = radius * 0.38f, center = bezugMitte)
    }
}

// ---------------------------------------------------------------------------
// Schritt 3: Sensor verbinden
// ---------------------------------------------------------------------------

@Composable
private fun Verbinden(gewaehlt: EmgSourceKind, onWechseln: (EmgSourceKind) -> Unit) {
    Text(stringResource(R.string.onboarding_verbinden_titel), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.onboarding_verbinden_text), style = MaterialTheme.typography.bodyLarge)

    HardwareKarte()

    Text(
        text = stringResource(R.string.onboarding_verbinden_auswahl),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 4.dp),
    )
    EmgSourceKind.entries.forEach { kind ->
        QuellenKarte(kind = kind, gewaehlt = kind == gewaehlt, onClick = { onWechseln(kind) })
    }
}

/** Die Zielhardware laut Hardwareliste — damit klar ist, womit die App spricht. */
@Composable
private fun HardwareKarte() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_hardware_titel),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            HardwareZeile(Icons.Default.Memory, R.string.onboarding_hardware_mcu, R.string.onboarding_hardware_mcu_detail)
            HardwareZeile(Icons.Default.Sensors, R.string.onboarding_hardware_sensor, R.string.onboarding_hardware_sensor_detail)
            HardwareZeile(Icons.Default.Bluetooth, R.string.onboarding_hardware_funk, R.string.onboarding_hardware_funk_detail)
        }
    }
}

@Composable
private fun HardwareZeile(icon: ImageVector, titel: Int, detail: Int) {
    val farbe = MaterialTheme.colorScheme.onSecondaryContainer
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = farbe, modifier = Modifier.size(26.dp))
        Column {
            Text(stringResource(titel), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = farbe)
            Text(stringResource(detail), style = MaterialTheme.typography.bodyMedium, color = farbe.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun QuellenKarte(kind: EmgSourceKind, gewaehlt: Boolean, onClick: () -> Unit) {
    val farben = MaterialTheme.colorScheme
    val icon = when (kind) {
        EmgSourceKind.SIMULATION -> Icons.Default.Timeline
        EmgSourceKind.REPLAY -> Icons.Default.PlayCircle
        EmgSourceKind.BLE -> Icons.Default.Bluetooth
    }
    val detail = when (kind) {
        EmgSourceKind.SIMULATION -> R.string.onboarding_quelle_simulation_detail
        EmgSourceKind.REPLAY -> R.string.onboarding_quelle_aufzeichnung_detail
        EmgSourceKind.BLE -> R.string.onboarding_quelle_wearable_detail
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = if (gewaehlt) farben.primaryContainer else farben.surface),
        border = if (gewaehlt) BorderStroke(2.dp, farben.primary) else BorderStroke(1.dp, farben.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = gewaehlt, role = Role.RadioButton, onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SymbolKreis(
                icon = icon,
                hintergrund = if (gewaehlt) farben.primary else farben.surfaceVariant,
                vordergrund = if (gewaehlt) farben.onPrimary else farben.onSurfaceVariant,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(kind.label(), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(detail), style = MaterialTheme.typography.bodyMedium, color = farben.onSurfaceVariant)
            }
            if (gewaehlt) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.onboarding_quelle_gewaehlt),
                    tint = farben.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Schritt 4: Kraft messen
// ---------------------------------------------------------------------------

@Composable
private fun Kalibrieren() {
    Text(stringResource(R.string.onboarding_kalibrieren_titel), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.onboarding_kalibrieren_text), style = MaterialTheme.typography.bodyLarge)

    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            AblaufZeile(Icons.Default.FitnessCenter, stringResource(R.string.onboarding_kalibrieren_ablauf1))
            AblaufZeile(Icons.Default.Timer, stringResource(R.string.onboarding_kalibrieren_ablauf2))
            AblaufZeile(Icons.Default.CheckCircle, stringResource(R.string.onboarding_kalibrieren_ablauf3))
        }
    }

    HinweisStreifen(text = stringResource(R.string.onboarding_kalibrieren_hinweis))
}

@Composable
private fun AblaufZeile(icon: ImageVector, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SymbolKreis(icon)
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

// ---------------------------------------------------------------------------
// Gemeinsame Bausteine
// ---------------------------------------------------------------------------

@Composable
private fun SymbolKreis(
    icon: ImageVector,
    hintergrund: Color = MaterialTheme.colorScheme.primaryContainer,
    vordergrund: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(hintergrund),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = vordergrund, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun HinweisZeile(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}
