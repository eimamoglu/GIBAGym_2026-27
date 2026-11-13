package at.htl.gibagym.screenshots

import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import at.htl.gibagym.core.model.ActivationZone
import at.htl.gibagym.core.model.AppSettings
import at.htl.gibagym.core.model.ConnectionState
import at.htl.gibagym.core.model.EmgSourceKind
import at.htl.gibagym.core.model.ExerciseCatalog
import at.htl.gibagym.core.model.FormCue
import at.htl.gibagym.core.model.LatencySnapshot
import at.htl.gibagym.core.model.PaletteChoice
import at.htl.gibagym.core.model.RatioResult
import at.htl.gibagym.core.model.RatioVerdict
import at.htl.gibagym.core.model.RepetitionAssessment
import at.htl.gibagym.core.model.RepetitionVerdict
import at.htl.gibagym.core.model.SignalQuality
import at.htl.gibagym.core.model.SmoothingConfig
import at.htl.gibagym.core.model.SmoothingStrategy
import at.htl.gibagym.core.model.TrainingSession
import at.htl.gibagym.core.model.ZonePosition
import at.htl.gibagym.core.ui.render.LocalLiveRendering
import at.htl.gibagym.core.ui.theme.GibaGymTheme
import at.htl.gibagym.feature.calibration.CalibrationScreen
import at.htl.gibagym.feature.calibration.CalibrationUiState
import at.htl.gibagym.feature.calibration.KalibrierSchritt
import at.htl.gibagym.feature.history.HistoryScreen
import at.htl.gibagym.feature.history.HistoryUiState
import at.htl.gibagym.feature.onboarding.NameScreen
import at.htl.gibagym.feature.onboarding.OnboardingSchritt
import at.htl.gibagym.feature.onboarding.OnboardingScreen
import at.htl.gibagym.feature.settings.SettingsScreen
import at.htl.gibagym.feature.training.TrainingScreen
import at.htl.gibagym.feature.training.TrainingUiState
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Nimmt von jedem Bildschirm ein echtes PNG auf und legt es in `docs/screenshots/`.
 *
 * Gerendert wird mit Robolectric im nativen Grafikmodus — also mit demselben
 * Skia-Renderer wie auf dem Gerät, aber ohne Emulator. Auf Rechnern ohne
 * Hardware-Virtualisierung (und damit ohne Emulator) ist das der einzige Weg,
 * überhaupt zu belegen, dass die Oberfläche so aussieht wie beschrieben.
 *
 * Aufnehmen:  `./gradlew :app:recordRoborazziDebug`
 * Vergleichen: `./gradlew :app:verifyRoborazziDebug`
 *
 * Die Bilder sind bewusst Teil des Repositorys: Sie sind der Nachweis in der
 * Diplomarbeit, und eine Änderung an der Oberfläche wird im Diff sichtbar.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "de-rAT-w411dp-h891dp-xhdpi")
class ScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val bankdruecken = ExerciseCatalog.bankdruecken

    // --- Erste Schritte -----------------------------------------------------

    @Test
    fun nameAbfrage() = aufnehmen("00-name") {
        NameScreen(anfangsname = "")
    }

    @Test
    fun onboardingWillkommen() = aufnehmen("01-onboarding-willkommen") {
        OnboardingScreen(
            schritt = OnboardingSchritt.WILLKOMMEN,
            gewaehlteQuelle = EmgSourceKind.SIMULATION,
            anzeigename = BEISPIELNAME,
        )
    }

    @Test
    fun onboardingElektroden() = aufnehmen("02-onboarding-elektroden") {
        OnboardingScreen(
            schritt = OnboardingSchritt.ELEKTRODEN,
            gewaehlteQuelle = EmgSourceKind.SIMULATION,
        )
    }

    @Test
    fun onboardingVerbinden() = aufnehmen("03-onboarding-verbinden") {
        OnboardingScreen(
            schritt = OnboardingSchritt.VERBINDEN,
            gewaehlteQuelle = EmgSourceKind.SIMULATION,
        )
    }

    // --- MVC-Kalibrierung ---------------------------------------------------

    @Test
    fun kalibrierungErklaerung() = aufnehmen("04-kalibrierung-erklaerung") {
        CalibrationScreen(
            state = CalibrationUiState(schritt = KalibrierSchritt.ERKLAERUNG),
            renderBuffer = Demodaten.gefuellterPuffer(),
            settings = AppSettings(displayName = BEISPIELNAME),
        )
    }

    @Test
    fun kalibrierungAufnahme() = aufnehmen("05-kalibrierung-aufnahme") {
        CalibrationScreen(
            state = CalibrationUiState(
                schritt = KalibrierSchritt.AUFNAHME,
                restsekunden = 3,
                aktuelleMillivolt = listOf(0.58f, 0.31f, 0.22f, 0.09f),
                spitzenMillivolt = listOf(0.62f, 0.34f, 0.24f, 0.11f),
                signalQuality = SignalQuality.GOOD,
            ),
            renderBuffer = Demodaten.gefuellterPuffer(),
            settings = AppSettings(displayName = BEISPIELNAME),
        )
    }

    @Test
    fun kalibrierungErgebnis() = aufnehmen("06-kalibrierung-ergebnis") {
        CalibrationScreen(
            state = CalibrationUiState(
                schritt = KalibrierSchritt.ERGEBNIS,
                spitzenMillivolt = listOf(0.62f, 0.34f, 0.24f, 0.11f),
            ),
            renderBuffer = Demodaten.gefuellterPuffer(),
            settings = AppSettings(displayName = BEISPIELNAME),
        )
    }

    // --- Live-Training ------------------------------------------------------

    @Test
    fun trainingUebungWaehlen() {
        val puffer = Demodaten.gefuellterPuffer()
        aufnehmen("17-training-uebung") {
            TrainingScreen(
                state = trainingState(puffer, PaletteChoice.STANDARD).copy(
                    running = false,
                    lastAssessment = null,
                ),
                renderBuffer = puffer,
                windowSizeClass = kompakteFensterklasse(),
            )
        }
    }

    @Test
    fun trainingStandardpalette() {
        val puffer = Demodaten.gefuellterPuffer()
        aufnehmen("07-training-standardpalette") {
            TrainingScreen(
                state = trainingState(puffer, PaletteChoice.STANDARD),
                renderBuffer = puffer,
                windowSizeClass = kompakteFensterklasse(),
            )
        }
    }

    @Test
    fun trainingRotGruenSicherePalette() {
        val puffer = Demodaten.gefuellterPuffer()
        aufnehmen("08-training-rotgruen-sichere-palette") {
            TrainingScreen(
                state = trainingState(puffer, PaletteChoice.ROT_GRUEN_SICHER),
                renderBuffer = puffer,
                windowSizeClass = kompakteFensterklasse(),
            )
        }
    }

    @Test
    fun trainingRatioUndBewertung() {
        val puffer = Demodaten.gefuellterPuffer()
        aufnehmen("09-training-ratio-und-bewertung", scrollZu = "Bewertung der letzten") {
            TrainingScreen(
                state = trainingState(puffer, PaletteChoice.STANDARD),
                renderBuffer = puffer,
                windowSizeClass = kompakteFensterklasse(),
            )
        }
    }

    @Test
    fun trainingAbgefaelschteWiederholung() {
        val puffer = Demodaten.gefuellterPuffer()
        aufnehmen("10-training-abgefaelschte-wiederholung", scrollZu = "Training beenden") {
            TrainingScreen(
                state = trainingState(puffer, PaletteChoice.STANDARD).copy(
                    ratio = RatioResult(
                        targetPercent = 48f,
                        supportPercent = 42f,
                        ratio = 0.88f,
                        verdict = RatioVerdict.HILFSMUSKEL_DOMINIERT,
                    ),
                    zonePosition = ZonePosition.ZU_HOCH,
                    signalQuality = SignalQuality.NOISY,
                    lastAssessment = RepetitionAssessment(
                        verdict = RepetitionVerdict.ABGEFAELSCHT,
                        confidence = 0.83f,
                        cues = listOf(FormCue.SCHWUNG, FormCue.HILFSMUSKEL_UEBERNIMMT),
                        inferenceMillis = 25,
                    ),
                    repetitions = 7,
                    cleanRepetitions = 4,
                ),
                renderBuffer = puffer,
                windowSizeClass = kompakteFensterklasse(),
            )
        }
    }

    @Test
    @Config(qualifiers = "de-rAT-w1280dp-h800dp-land-xhdpi")
    fun trainingTabletQuerformat() {
        val puffer = Demodaten.gefuellterPuffer()
        aufnehmen("11-training-tablet-querformat") {
            TrainingScreen(
                state = trainingState(puffer, PaletteChoice.STANDARD),
                renderBuffer = puffer,
                windowSizeClass = breiteFensterklasse(),
            )
        }
    }

    @Test
    fun trainingSchriftgroesse200Prozent() {
        val puffer = Demodaten.gefuellterPuffer()
        aufnehmen("12-training-schriftgroesse-200-prozent") {
            // Android erlaubt bis zu 200 % Schriftgröße. Der Screenshot belegt,
            // dass dabei nichts abgeschnitten wird — der Inhalt wird länger und
            // scrollt, statt sich zu überlagern.
            val basis = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = basis.density, fontScale = 2f),
            ) {
                TrainingScreen(
                    state = trainingState(puffer, PaletteChoice.STANDARD),
                    renderBuffer = puffer,
                    windowSizeClass = kompakteFensterklasse(),
                )
            }
        }
    }

    // --- Verlauf und Einstellungen -----------------------------------------

    @Test
    fun verlaufMitEinheiten() = aufnehmen("13-verlauf-einheiten") {
        HistoryScreen(
            state = HistoryUiState(laedt = false, sessions = demoEinheiten()),
            anzeigename = BEISPIELNAME,
        )
    }

    @Test
    fun verlaufLeer() = aufnehmen("14-verlauf-leer") {
        HistoryScreen(
            state = HistoryUiState(laedt = false, sessions = emptyList()),
            anzeigename = BEISPIELNAME,
        )
    }

    @Test
    fun einstellungen() = aufnehmen("15-einstellungen") {
        SettingsScreen(settings = einstellungenMitName())
    }

    @Test
    fun einstellungenEigeneFarben() = aufnehmen("16-einstellungen-farben", scrollZu = "Zu hoch") {
        SettingsScreen(settings = einstellungenMitName())
    }

    @Test
    fun einstellungenZuruecksetzen() = aufnehmen("18-einstellungen-zuruecksetzen", scrollZu = "Alles zurücksetzen") {
        SettingsScreen(settings = einstellungenMitName())
    }

    // --- Hilfsmittel --------------------------------------------------------

    /**
     * @param scrollZu Text eines Elements, zu dem vor der Aufnahme gescrollt wird.
     *   Ein Telefonbildschirm zeigt nur den oberen Teil der Trainingsansicht;
     *   ohne diesen Schritt gäbe es kein Bild von Ratio-Analyse und KI-Bewertung.
     */
    private fun aufnehmen(name: String, scrollZu: String? = null, content: @Composable () -> Unit) {
        composeRule.setContent {
            // Ohne Abschalten der Zeichenschleife käme der Test nie zur Ruhe:
            // withFrameNanos meldet endlos neue Bilder an.
            CompositionLocalProvider(LocalLiveRendering provides false) {
                GibaGymTheme(darkTheme = false) { content() }
            }
        }
        if (scrollZu != null) {
            composeRule.onNodeWithText(scrollZu, substring = true).performScrollTo()
        }
        composeRule.onRoot().captureRoboImage(file = File(AUSGABE, "$name.png"))
    }

    private fun einstellungenMitName() = AppSettings(
        sourceKind = EmgSourceKind.REPLAY,
        smoothing = SmoothingConfig(SmoothingStrategy.GLEITENDER_RMS, windowSamples = 50),
        palette = PaletteChoice.EIGEN,
        displayName = BEISPIELNAME,
        eigenWenigArgb = 0xFF90CAF9.toInt(),
        eigenZielArgb = 0xFF2E7D32.toInt(),
        eigenHochArgb = 0xFFC62828.toInt(),
    )

    private fun trainingState(
        puffer: at.htl.gibagym.core.engine.render.EmgRenderBuffer,
        palette: PaletteChoice,
    ) = TrainingUiState(
        exercise = bankdruecken,
        running = true,
        sourceKind = EmgSourceKind.SIMULATION,
        connection = ConnectionState.Connected("Simulator (kein Gerät nötig)"),
        signalQuality = SignalQuality.GOOD,
        percentPerChannel = Demodaten.prozentwerte(puffer),
        zonePosition = ActivationZone.DEFAULT.classify(puffer.latestPercent(0)),
        ratio = RatioResult(
            targetPercent = puffer.latestPercent(0),
            supportPercent = puffer.latestPercent(1),
            ratio = if (puffer.latestPercent(0) > 0f) puffer.latestPercent(1) / puffer.latestPercent(0) else 0f,
            verdict = RatioVerdict.GUT,
        ),
        latency = LatencySnapshot(lastMillis = 38f, averageMillis = 41f, maxMillis = 63f),
        lastAssessment = RepetitionAssessment(
            verdict = RepetitionVerdict.SAUBER,
            confidence = 0.88f,
            cues = emptyList(),
            inferenceMillis = 25,
        ),
        repetitions = 6,
        cleanRepetitions = 5,
        palette = palette,
        displayName = BEISPIELNAME,
        kalibriert = true,
        klassifiziererName = "Heuristik-Platzhalter (kein trainiertes Modell)",
        glaettungsLatenzMillis = 25f,
    )

    private fun demoEinheiten(): List<TrainingSession> = listOf(
        TrainingSession(
            id = "1",
            startedAtEpochMillis = 1_757_000_000_000L,
            durationSeconds = 42 * 60,
            exerciseId = "bankdruecken",
            exerciseName = "Bankdrücken",
            repetitions = 32,
            cleanRepetitions = 27,
            averageTargetPercent = 78.4f,
            averageRatio = 0.44f,
            sourceKind = EmgSourceKind.SIMULATION,
        ),
        TrainingSession(
            id = "2",
            startedAtEpochMillis = 1_756_800_000_000L,
            durationSeconds = 51 * 60,
            exerciseId = "kniebeuge",
            exerciseName = "Kniebeuge",
            repetitions = 40,
            cleanRepetitions = 25,
            averageTargetPercent = 71.9f,
            averageRatio = 0.62f,
            sourceKind = EmgSourceKind.SIMULATION,
        ),
        TrainingSession(
            id = "3",
            startedAtEpochMillis = 1_756_500_000_000L,
            durationSeconds = 28 * 60,
            exerciseId = "bizepscurl",
            exerciseName = "Bizepscurl",
            repetitions = 24,
            cleanRepetitions = 14,
            averageTargetPercent = 66.2f,
            averageRatio = 0.51f,
            sourceKind = EmgSourceKind.REPLAY,
        ),
        TrainingSession(
            id = "4",
            startedAtEpochMillis = 1_756_300_000_000L,
            durationSeconds = 35 * 60,
            exerciseId = "schulterdruecken",
            exerciseName = "Schulterdrücken",
            repetitions = 28,
            cleanRepetitions = 22,
            averageTargetPercent = 74.1f,
            averageRatio = 0.47f,
            sourceKind = EmgSourceKind.SIMULATION,
        ),
        TrainingSession(
            id = "5",
            startedAtEpochMillis = 1_756_100_000_000L,
            durationSeconds = 38 * 60,
            exerciseId = "rudern",
            exerciseName = "Rudern",
            repetitions = 30,
            cleanRepetitions = 24,
            averageTargetPercent = 69.8f,
            averageRatio = 0.53f,
            sourceKind = EmgSourceKind.SIMULATION,
        ),
        TrainingSession(
            id = "6",
            startedAtEpochMillis = 1_755_900_000_000L,
            durationSeconds = 22 * 60,
            exerciseId = "wadenheben",
            exerciseName = "Wadenheben",
            repetitions = 36,
            cleanRepetitions = 31,
            averageTargetPercent = 81.0f,
            averageRatio = 0.38f,
            sourceKind = EmgSourceKind.SIMULATION,
        ),
    )

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    private fun kompakteFensterklasse(): WindowSizeClass =
        WindowSizeClass.calculateFromSize(DpSize(411.dp, 891.dp))

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    private fun breiteFensterklasse(): WindowSizeClass =
        WindowSizeClass.calculateFromSize(DpSize(1280.dp, 800.dp))

    private companion object {
        /**
         * Die Bilder landen im Repository und nicht in `build/`: Sie sind
         * Projektergebnis, kein Zwischenstand.
         */
        val AUSGABE: File = File("../docs/screenshots").canonicalFile
        const val BEISPIELNAME = "Anna"
    }
}
