package at.htl.gibagym.core.ui.text

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import at.htl.gibagym.core.model.ConnectionState
import at.htl.gibagym.core.model.EmgSourceKind
import at.htl.gibagym.core.model.FormCue
import at.htl.gibagym.core.model.MuscleGroup
import at.htl.gibagym.core.model.MuscleRole
import at.htl.gibagym.core.model.RatioVerdict
import at.htl.gibagym.core.model.RepetitionVerdict
import at.htl.gibagym.core.model.SignalQuality
import at.htl.gibagym.core.model.UnavailableReason
import at.htl.gibagym.core.model.ZonePosition
import at.htl.gibagym.core.ui.R

/**
 * Übersetzt die Aufzählungstypen des Datenmodells in deutsche Anzeigetexte.
 *
 * Die Texte liegen als String-Ressourcen vor und nicht als Konstanten im Modell.
 * Das ist kein Selbstzweck: Nur so greifen Systemschriftgröße, `TalkBack` und
 * eine spätere Übersetzung, und nur so bleibt `:core:model` frei von Android.
 */

@StringRes
fun MuscleGroup.labelRes(): Int = when (this) {
    MuscleGroup.BRUST -> R.string.muskel_brust
    MuscleGroup.TRIZEPS -> R.string.muskel_trizeps
    MuscleGroup.BIZEPS -> R.string.muskel_bizeps
    MuscleGroup.SCHULTER_VORNE -> R.string.muskel_schulter_vorne
    MuscleGroup.LATISSIMUS -> R.string.muskel_latissimus
    MuscleGroup.RUMPF -> R.string.muskel_rumpf
    MuscleGroup.GESAESS -> R.string.muskel_gesaess
    MuscleGroup.OBERSCHENKEL_VORNE -> R.string.muskel_oberschenkel_vorne
    MuscleGroup.OBERSCHENKEL_HINTEN -> R.string.muskel_oberschenkel_hinten
    MuscleGroup.WADE -> R.string.muskel_wade
}

@Composable
fun MuscleGroup.label(): String = stringResource(labelRes())

@Composable
fun MuscleRole.label(): String = stringResource(
    when (this) {
        MuscleRole.ZIELMUSKEL -> R.string.rolle_zielmuskel
        MuscleRole.HILFSMUSKEL -> R.string.rolle_hilfsmuskel
        MuscleRole.BEOBACHTUNG -> R.string.rolle_beobachtung
    },
)

@Composable
fun SignalQuality.label(): String = stringResource(
    when (this) {
        SignalQuality.UNKNOWN -> R.string.guete_unbekannt
        SignalQuality.GOOD -> R.string.guete_gut
        SignalQuality.NOISY -> R.string.guete_verrauscht
        SignalQuality.NO_CONTACT -> R.string.guete_kein_kontakt
    },
)

/** Handlungsanweisung bei schlechtem Signal, oder null wenn alles in Ordnung ist. */
@Composable
fun SignalQuality.hint(): String? = when (this) {
    SignalQuality.NO_CONTACT -> stringResource(R.string.guete_hinweis_kein_kontakt)
    SignalQuality.NOISY -> stringResource(R.string.guete_hinweis_verrauscht)
    else -> null
}

@Composable
fun RepetitionVerdict.label(): String = stringResource(
    when (this) {
        RepetitionVerdict.SAUBER -> R.string.urteil_sauber
        RepetitionVerdict.ABGEFAELSCHT -> R.string.urteil_abgefaelscht
        RepetitionVerdict.UNSICHER -> R.string.urteil_unsicher
    },
)

@Composable
fun FormCue.label(): String = stringResource(
    when (this) {
        FormCue.SCHWUNG -> R.string.hinweis_schwung
        FormCue.HILFSMUSKEL_UEBERNIMMT -> R.string.hinweis_hilfsmuskel
        FormCue.ASYMMETRIE -> R.string.hinweis_asymmetrie
        FormCue.ZU_SCHNELL -> R.string.hinweis_zu_schnell
        FormCue.UNVOLLSTAENDIGE_SPANNUNG -> R.string.hinweis_unvollstaendige_spannung
    },
)

@Composable
fun RatioVerdict.label(): String = stringResource(
    when (this) {
        RatioVerdict.GUT -> R.string.ratio_gut
        RatioVerdict.GRENZWERTIG -> R.string.ratio_grenzwertig
        RatioVerdict.HILFSMUSKEL_DOMINIERT -> R.string.ratio_hilfsmuskel_dominiert
        RatioVerdict.ZU_WENIG_LAST -> R.string.ratio_zu_wenig_last
    },
)

@Composable
fun ZonePosition.label(): String = stringResource(
    when (this) {
        ZonePosition.ZU_NIEDRIG -> R.string.zone_zu_niedrig
        ZonePosition.IM_ZIEL -> R.string.zone_im_ziel
        ZonePosition.ZU_HOCH -> R.string.zone_zu_hoch
    },
)

@Composable
fun EmgSourceKind.label(): String = stringResource(
    when (this) {
        EmgSourceKind.SIMULATION -> R.string.quelle_simulation
        EmgSourceKind.REPLAY -> R.string.quelle_aufzeichnung
        EmgSourceKind.BLE -> R.string.quelle_wearable
    },
)

@Composable
fun ConnectionState.label(): String = when (this) {
    ConnectionState.Disconnected -> stringResource(R.string.verbindung_getrennt)
    ConnectionState.Connecting -> stringResource(R.string.verbindung_verbindet)
    is ConnectionState.Connected -> stringResource(R.string.verbindung_verbunden, deviceName)
    is ConnectionState.Unavailable -> stringResource(
        when (reason) {
            UnavailableReason.NOT_IMPLEMENTED -> R.string.verbindung_nicht_implementiert
            UnavailableReason.BLUETOOTH_OFF -> R.string.verbindung_bluetooth_aus
            UnavailableReason.PERMISSION_MISSING -> R.string.verbindung_berechtigung_fehlt
            UnavailableReason.DEVICE_NOT_FOUND -> R.string.verbindung_kein_geraet
            UnavailableReason.RECORDING_UNREADABLE -> R.string.verbindung_aufzeichnung_unlesbar
        },
    )
}
