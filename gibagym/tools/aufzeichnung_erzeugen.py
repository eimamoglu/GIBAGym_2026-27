#!/usr/bin/env python3
"""Erzeugt die Demo-Aufzeichnung fuer den Wiedergabemodus (ReplayEmgSource).

Die Datei ``app/src/main/assets/aufzeichnungen/bankdruecken-demo.csv`` bildet einen
Satz Bankdruecken mit acht Wiederholungen ab, davon zwei bewusst abgefaelscht.
Sie ersetzt keine echte Messung mit dem Wearable, sondern liefert einen
reproduzierbaren Datensatz, solange die Hardware noch nicht fertig ist.

Das Signalmodell entspricht dem des Kotlin-Simulators
(``EmgSignalGenerator``): amplitudenmoduliertes Rauschen, dessen Huellkurve die
Muskelspannung abbildet.

Aufruf:
    python3 tools/aufzeichnung_erzeugen.py
"""

from __future__ import annotations

import math
import random
from pathlib import Path

ZIEL = Path(__file__).resolve().parents[1] / "app/src/main/assets/aufzeichnungen/bankdruecken-demo.csv"

ABTASTRATE_HZ = 1000
WIEDERHOLUNGEN = 8
WIEDERHOLUNG_MS = 2400
PAUSE_MS = 1100
VORLAUF_MS = 1500
NACHLAUF_MS = 2000

# Kanalbelegung laut Uebungskatalog: Brust, Trizeps, vordere Schulter, Rumpf.
SPITZEN_MV = [1.42, 1.05, 0.90, 0.62]
ANTEIL_SAUBER = [1.00, 0.36, 0.28, 0.16]
ANTEIL_SCHWUNG = [0.70, 0.86, 0.68, 0.52]
GRUNDRAUSCHEN_MV = 0.017

# Wiederholung 4 und 7 werden mit Schwung ausgefuehrt.
ABGEFAELSCHT = {4, 7}

random.seed(20260914)


def huellkurve_sauber(phase: float) -> float:
    bogen = math.sin(math.pi * phase)
    return bogen * bogen * 0.85 + bogen * 0.15


def huellkurve_schwung(phase: float) -> float:
    ruck = math.exp(-((phase - 0.12) ** 2) / 0.0035) * 1.05
    rest = math.sin(math.pi * phase) * 0.45
    return min(ruck + rest, 1.15)


def zustand(millis: float) -> tuple[float, bool]:
    if millis < VORLAUF_MS:
        return 0.0, False
    seit_start = millis - VORLAUF_MS
    zyklus = WIEDERHOLUNG_MS + PAUSE_MS
    nummer = int(seit_start // zyklus) + 1
    if nummer > WIEDERHOLUNGEN:
        return 0.0, False
    im_zyklus = seit_start % zyklus
    if im_zyklus >= WIEDERHOLUNG_MS:
        return 0.0, False
    phase = im_zyklus / WIEDERHOLUNG_MS
    schwung = nummer in ABGEFAELSCHT
    return (huellkurve_schwung(phase) if schwung else huellkurve_sauber(phase)), schwung


def main() -> None:
    gesamt_ms = VORLAUF_MS + WIEDERHOLUNGEN * (WIEDERHOLUNG_MS + PAUSE_MS) + NACHLAUF_MS
    gesamt_proben = int(gesamt_ms * ABTASTRATE_HZ / 1000)

    ZIEL.parent.mkdir(parents=True, exist_ok=True)
    with ZIEL.open("w", encoding="utf-8", newline="\n") as datei:
        datei.write("# GIBAGym Demo-Aufzeichnung: Bankdruecken, 8 Wiederholungen\n")
        datei.write("# Kanaele: 0=Brust 1=Trizeps 2=Schulter vorne 3=Rumpf\n")
        datei.write("# Wiederholung 4 und 7 wurden bewusst mit Schwung ausgefuehrt\n")
        datei.write(f"# Abtastrate: {ABTASTRATE_HZ} Hz, Werte in Millivolt\n")
        datei.write("# Erzeugt mit tools/aufzeichnung_erzeugen.py\n")
        datei.write("t_ms;ch0_mv;ch1_mv;ch2_mv;ch3_mv;quality\n")

        for index in range(gesamt_proben):
            millis = index * 1000 / ABTASTRATE_HZ
            huelle, schwung = zustand(millis)
            anteile = ANTEIL_SCHWUNG if schwung else ANTEIL_SAUBER
            werte = []
            for kanal in range(4):
                rauschen = random.gauss(0.0, 1.0)
                grund = random.gauss(0.0, GRUNDRAUSCHEN_MV)
                werte.append(huelle * anteile[kanal] * SPITZEN_MV[kanal] * rauschen + grund)
            # Zwischen Wiederholung 5 und 6 loest sich eine Elektrode kurz.
            guete = "NOISY" if 19_000 <= millis < 20_200 else "GOOD"
            datei.write(f"{int(millis)};" + ";".join(f"{wert:.4f}" for wert in werte) + f";{guete}\n")

    print(f"{ZIEL} geschrieben: {gesamt_proben} Zeilen, {gesamt_ms / 1000:.1f} s")


if __name__ == "__main__":
    main()
