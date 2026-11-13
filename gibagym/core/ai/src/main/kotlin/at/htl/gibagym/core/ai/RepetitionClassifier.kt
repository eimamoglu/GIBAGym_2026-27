package at.htl.gibagym.core.ai

import at.htl.gibagym.core.model.Exercise
import at.htl.gibagym.core.model.RepetitionAssessment
import at.htl.gibagym.core.model.RepetitionWindow

/**
 * Bewertet eine abgeschlossene Wiederholung: sauber oder abgefälscht.
 *
 * Das ist die Schnittstelle zur KI-Komponente. Sie ist absichtlich `suspend` und
 * nimmt ein bereits verdichtetes [RepetitionWindow] entgegen — beides sind
 * Entscheidungen, die später nicht mehr zurückgenommen werden müssen:
 *
 * - `suspend`: Eine TensorFlow-Lite-Inferenz gehört nicht auf den Main-Thread.
 *   Wer hier synchron rechnet, blockiert die 60-Bilder-Anzeige des Avatars.
 * - [RepetitionWindow] statt Rohdatenstrom: Der Klassifizierer soll nicht selbst
 *   segmentieren müssen. Wo eine Wiederholung beginnt, weiß die Signalverarbeitung
 *   besser.
 * - Rückgabe mit [RepetitionAssessment.confidence]: Die UI muss ein unsicheres
 *   Modell als unsicher anzeigen dürfen. Ein hartes "abgefälscht" bei 51 %
 *   Sicherheit wäre für den Trainierenden nicht nachvollziehbar.
 *
 * TODO(Samoel): Implementieren. Vorgeschlagener Weg:
 *  1. Trainingsdaten aufnehmen (saubere und bewusst abgefälschte Sätze, gelabelt)
 *  2. Merkmale aus [RepetitionWindow] ableiten, oder direkt auf
 *     [RepetitionWindow.targetCurve] ein kleines 1D-CNN rechnen
 *  3. Modell als `.tflite` in `:core:ai/src/main/assets` legen und hier laden
 *  4. Inferenzzeit in [RepetitionAssessment.inferenceMillis] zurückgeben — sie
 *     geht in das 100-ms-Budget aus /LF40/ ein
 *  Wichtig: Das Modell läuft lokal auf dem Gerät. Es gibt keinen Cloud-Aufruf,
 *  das ist eine Zusage des Projekts und keine Implementierungsdetailfrage.
 */
interface RepetitionClassifier {

    /** Menschenlesbarer Name des Verfahrens — die UI zeigt ihn im Entwicklerbereich an. */
    val displayName: String

    /**
     * @param window Die abgeschlossene Wiederholung.
     * @param exercise Übung mit Zielkorridor und Kanalbelegung.
     */
    suspend fun classify(window: RepetitionWindow, exercise: Exercise): RepetitionAssessment
}
