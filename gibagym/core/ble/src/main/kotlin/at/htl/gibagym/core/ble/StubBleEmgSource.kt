package at.htl.gibagym.core.ble

import at.htl.gibagym.core.model.ConnectionState
import at.htl.gibagym.core.model.EmgFrame
import at.htl.gibagym.core.model.EmgSource
import at.htl.gibagym.core.model.EmgSourceKind
import at.htl.gibagym.core.model.UnavailableReason
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Platzhalter für die echte Wearable-Anbindung.
 *
 * Er verbindet sich nicht und liefert keine Frames — er meldet ehrlich
 * [UnavailableReason.NOT_IMPLEMENTED]. Die UI zeigt daraufhin einen Hinweis und
 * bietet den Wechsel in den Simulations- oder Wiedergabemodus an. Genau so soll
 * sich die App auch später verhalten, wenn kein Wearable in Reichweite ist; der
 * Fehlerpfad ist damit schon jetzt gebaut und sichtbar.
 *
 * TODO(Elyesa): Durch die echte Implementierung ersetzen. Grobe Schritte:
 *  1. Scannen über [BleScanner], Gerät nach [GibaGymGatt.SERVICE_UUID] filtern
 *  2. `BluetoothGatt` verbinden, MTU auf 247 anheben (mehr Abtastwerte je Paket),
 *     Verbindungsintervall über `requestConnectionPriority(CONNECTION_PRIORITY_HIGH)`
 *  3. Notifications auf [GibaGymGatt.EMG_NOTIFY_UUID] aktivieren
 *  4. Jedes Paket durch [ReplayGuard] und [SecureChannel] schicken
 *  5. Die [GibaGymGatt.SAMPLES_PER_PACKET] Abtastwerte eines Pakets auf das
 *     Abtastraster zurückrechnen: Der Empfangszeitpunkt gilt für den **letzten**
 *     Abtastwert im Paket, die übrigen liegen entsprechend früher. Wer allen
 *     Abtastwerten eines Pakets denselben Zeitstempel gibt, macht die
 *     Latenzmessung der UI wertlos.
 *  6. Verbindungsabbruch: [ConnectionState.Disconnected] melden, die UI friert
 *     den Avatar ein und blendet einen Hinweis ein
 */
class StubBleEmgSource(
    override val channelCount: Int = 4,
    override val samplingRateHz: Int = 1000,
) : EmgSource {

    private val connectionState = MutableStateFlow<ConnectionState>(
        ConnectionState.Unavailable(UnavailableReason.NOT_IMPLEMENTED),
    )

    override val kind: EmgSourceKind = EmgSourceKind.BLE
    override val connection: StateFlow<ConnectionState> = connectionState.asStateFlow()
    override val frames: Flow<EmgFrame> = emptyFlow()
}

/**
 * Platzhalter-Scanner: liefert ein einzelnes erfundenes Gerät, damit die
 * Geräteauswahl in der UI gebaut und bebildert werden kann.
 *
 * TODO(Elyesa): Durch echten `BluetoothLeScanner` ersetzen.
 */
class StubBleScanner : BleScanner {
    override fun scan(): Flow<DiscoveredDevice> = kotlinx.coroutines.flow.flowOf(
        DiscoveredDevice(
            address = "C4:7C:8D:6A:00:01",
            name = "GIBAGym-Wearable (Demo)",
            rssi = -58,
            supportsSecureConnections = true,
        ),
    )
}

/**
 * Bewusst naiver Replay-Schutz: akzeptiert nur streng steigende Zähler je Gerät.
 *
 * Das erkennt eine einfache Wiedereinspielung, hat aber zwei bekannte Lücken:
 * Nach einem Neustart des Wearables beginnt der Zähler wieder bei 0 und alles
 * wird verworfen, und ein verlorenes Paket kann nicht von einem unterdrückten
 * unterschieden werden.
 *
 * TODO(Elyesa): Ersetzen durch ein Schiebefenster (wie in IPsec/DTLS): Zähler
 *  innerhalb eines Fensters von z. B. 64 akzeptieren, bereits gesehene über eine
 *  Bitmaske ausschließen. Zählerneustart nur nach erneutem Schlüsselaustausch
 *  zulassen, sonst ist der Replay-Schutz wirkungslos.
 */
class MonotonicReplayGuard : ReplayGuard {

    private val lastCounters = mutableMapOf<String, Long>()

    override fun accept(deviceAddress: String, counter: Long): Boolean {
        val last = lastCounters[deviceAddress]
        if (last != null && counter <= last) return false
        lastCounters[deviceAddress] = counter
        return true
    }

    override fun reset() = lastCounters.clear()
}

/**
 * Platzhalter-Kanal ohne Kryptografie: reicht die Nutzdaten unverändert durch.
 *
 * TODO(Elyesa): Ersetzen. Bis dahin gilt: Diese Klasse darf niemals in einem
 *  Release-Build landen. Ein Build-Check, der genau das sicherstellt, ist Teil
 *  der Aufgabe.
 */
class PassThroughSecureChannel : SecureChannel {

    override var rejectedPacketCount: Long = 0L
        private set

    override fun open(packet: BlePacket): ByteArray = packet.payload
}
