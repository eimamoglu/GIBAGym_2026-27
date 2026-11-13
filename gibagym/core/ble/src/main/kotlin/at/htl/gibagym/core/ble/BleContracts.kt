package at.htl.gibagym.core.ble

import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Ein gefundenes Wearable.
 *
 * @property address MAC-Adresse bzw. auf neueren Android-Versionen die
 *   zufällige, rotierende Adresse. Nicht als dauerhafte Geräte-ID verwenden.
 * @property name Werbename aus dem Advertising-Paket.
 * @property rssi Empfangsfeldstärke in dBm — die UI zeigt daraus "in Reichweite".
 * @property supportsSecureConnections Ob das Gerät LE Secure Connections anbietet.
 *   Geräte ohne diese Unterstützung dürfen nicht gekoppelt werden, weil der
 *   Legacy-Pairing-Schlüsselaustausch passiv mitlesbar ist.
 */
data class DiscoveredDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    val supportsSecureConnections: Boolean,
)

/**
 * Sucht nach GIBAGym-Wearables.
 *
 * TODO(Elyesa): Über `BluetoothLeScanner` implementieren. Zu beachten:
 *  - Scan auf die Service-UUID [GibaGymGatt.SERVICE_UUID] filtern, sonst
 *    verbraucht der Scan im Fitnessstudio spürbar Akku
 *  - Berechtigungen zur Laufzeit prüfen (BLUETOOTH_SCAN ab Android 12,
 *    ACCESS_FINE_LOCATION darunter)
 *  - Scan nach spätestens 30 Sekunden beenden, Android drosselt sonst
 */
interface BleScanner {

    /** Cold Flow: Der Scan startet beim Collect und endet mit dem Abbruch. */
    fun scan(): Flow<DiscoveredDevice>
}

/**
 * GATT-Profil des Wearables. Die UUIDs sind die Schnittstelle zwischen der
 * ESP32-Firmware und der App — sie müssen auf beiden Seiten identisch sein.
 *
 * TODO(Elyesa): Mit der Firmware abgleichen und hier als verbindlich festschreiben.
 */
object GibaGymGatt {

    /** Herstellerspezifischer Dienst des Wearables. */
    val SERVICE_UUID: UUID = UUID.fromString("0000a101-0000-1000-8000-00805f9b34fb")

    /** Notify-Charakteristik mit den gebündelten EMG-Abtastwerten. */
    val EMG_NOTIFY_UUID: UUID = UUID.fromString("0000a102-0000-1000-8000-00805f9b34fb")

    /** Read/Write-Charakteristik für Abtastrate, Kanalzahl und Firmwarestand. */
    val CONFIG_UUID: UUID = UUID.fromString("0000a103-0000-1000-8000-00805f9b34fb")

    /**
     * Anzahl der Abtastwerte je BLE-Paket. Das Wearable bündelt, weil ein
     * Verbindungsintervall von 7,5 ms keine 1000 Einzelpakete pro Sekunde zulässt.
     * Die Bündelgröße bestimmt direkt die untere Schranke der Übertragungslatenz.
     */
    const val SAMPLES_PER_PACKET = 8
}

/**
 * Ein rohes, noch verschlüsseltes Paket vom Wearable.
 *
 * @property counter Monoton steigender Paketzähler der Firmware. Grundlage des
 *   Replay-Schutzes und gleichzeitig Teil des Nonce.
 * @property payload Verschlüsselte Nutzdaten.
 * @property mac Authentisierungs-Tag.
 * @property receivedAtNanos Empfangszeitpunkt in der Zeitbasis von [System.nanoTime].
 */
data class BlePacket(
    val counter: Long,
    val payload: ByteArray,
    val mac: ByteArray,
    val receivedAtNanos: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BlePacket) return false
        return counter == other.counter &&
            receivedAtNanos == other.receivedAtNanos &&
            payload.contentEquals(other.payload) &&
            mac.contentEquals(other.mac)
    }

    override fun hashCode(): Int {
        var result = counter.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + mac.contentHashCode()
        result = 31 * result + receivedAtNanos.hashCode()
        return result
    }
}

/**
 * Entschlüsselt und authentisiert Pakete des Wearables (AES-128).
 *
 * TODO(Elyesa): Implementieren. Festzulegen und zu dokumentieren:
 *  - Betriebsart: AES-128-CCM oder AES-128-GCM (beide liefern Verschlüsselung und
 *    Authentisierung in einem Schritt; CCM ist auf dem ESP32 günstiger)
 *  - Schlüsselableitung aus dem LE-Secure-Connections-Schlüssel, Speicherung im
 *    Android Keystore, nicht in den SharedPreferences
 *  - Nonce-Aufbau: Geräte-ID plus Paketzähler, niemals wiederverwenden
 *  - Verhalten bei Fehlschlag: Paket verwerfen und zählen, Verbindung erst nach
 *    mehreren Fehlschlägen in Folge trennen (ein einzelner Bitfehler ist normal)
 */
interface SecureChannel {

    /** @return Klartext, oder null wenn die Authentisierung fehlschlägt. */
    fun open(packet: BlePacket): ByteArray?

    /** Anzahl verworfener Pakete seit Verbindungsaufbau — gehört ins Fehlerprotokoll. */
    val rejectedPacketCount: Long
}

/**
 * Schützt vor dem Wiedereinspielen aufgezeichneter Pakete.
 *
 * Angriffsbild: Jemand zeichnet die Pakete einer sauberen Wiederholung auf und
 * spielt sie später ein, um saubere Ausführung vorzutäuschen. Ohne Replay-Schutz
 * ist das trivial, weil die Pakete authentisch sind — nur eben alt.
 */
interface ReplayGuard {

    /** @return true, wenn das Paket neu ist und verarbeitet werden darf. */
    fun accept(deviceAddress: String, counter: Long): Boolean

    fun reset()
}
