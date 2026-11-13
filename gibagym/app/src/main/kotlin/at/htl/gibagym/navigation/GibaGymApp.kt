package at.htl.gibagym.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import at.htl.gibagym.AppContainer
import at.htl.gibagym.R
import at.htl.gibagym.core.model.AppSettings
import at.htl.gibagym.core.model.CustomExercise
import at.htl.gibagym.core.model.EmgSourceKind
import at.htl.gibagym.core.model.ExerciseCatalog
import java.util.UUID
import at.htl.gibagym.feature.calibration.CalibrationRoute
import at.htl.gibagym.feature.calibration.CalibrationViewModel
import at.htl.gibagym.feature.history.HistoryRoute
import at.htl.gibagym.feature.history.HistoryViewModel
import at.htl.gibagym.feature.onboarding.NameScreen
import at.htl.gibagym.feature.onboarding.OnboardingRoute
import at.htl.gibagym.feature.settings.SettingsScreen
import at.htl.gibagym.feature.training.TrainingRoute
import at.htl.gibagym.feature.training.TrainingViewModel
import kotlinx.coroutines.launch

/** Ziele der App-Navigation. */
private enum class Ziel(val route: String, val titelRes: Int, val icon: ImageVector) {
    TRAINING("training", R.string.navigation_training, Icons.Default.FitnessCenter),
    KALIBRIERUNG("kalibrierung", R.string.navigation_kalibrierung, Icons.Default.Straighten),
    VERLAUF("verlauf", R.string.navigation_verlauf, Icons.Default.History),
    EINSTELLUNGEN("einstellungen", R.string.navigation_einstellungen, Icons.Default.Settings),
}

private const val ROUTE_ONBOARDING = "erste-schritte"
private const val ROUTE_NAME = "name"

/**
 * Gerüst der App: Onboarding beim ersten Start, danach vier Bereiche.
 *
 * Die Navigationsleiste wandert bei breiten Fenstern an den linken Rand
 * (`NavigationRail`). Das ist nicht nur Kosmetik: Im Querformat auf einem
 * Telefon bliebe von einer unteren Leiste zu wenig Höhe für den Avatar übrig.
 */
@Composable
fun GibaGymApp(
    container: AppContainer,
    windowSizeClass: WindowSizeClass,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
    val umfang = rememberCoroutineScope()

    // Das Startziel wird genau einmal bestimmt. Würde es dem Einstellungswert
    // folgen, baute der NavHost beim Abschluss von „Erste Schritte“ seinen Graphen
    // neu auf — und der gezielte Wechsel zur Kalibrierung ginge dabei verloren.
    val startZiel = remember {
        val aktuell = container.settingsRepository.settings.value
        when {
            aktuell.displayName.isBlank() -> ROUTE_NAME
            aktuell.onboardingCompleted -> Ziel.TRAINING.route
            else -> ROUTE_ONBOARDING
        }
    }

    val aktuelleRoute = backStackEntry?.destination?.route
    val zeigeNavigation = aktuelleRoute != null &&
        aktuelleRoute != ROUTE_ONBOARDING &&
        aktuelleRoute != ROUTE_NAME
    val breitesLayout = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

    Scaffold(
        bottomBar = {
            if (zeigeNavigation && !breitesLayout) {
                NavigationBar {
                    Ziel.entries.forEach { ziel ->
                        NavigationBarItem(
                            selected = backStackEntry?.destination?.hierarchy?.any { it.route == ziel.route } == true,
                            onClick = { navController.wechsleZu(ziel.route) },
                            icon = { Icon(ziel.icon, contentDescription = null) },
                            label = { Text(stringResource(ziel.titelRes)) },
                            alwaysShowLabel = true,
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Row(modifier = Modifier.fillMaxSize()) {
            if (zeigeNavigation && breitesLayout) {
                NavigationRail(modifier = Modifier.padding(innerPadding)) {
                    Ziel.entries.forEach { ziel ->
                        NavigationRailItem(
                            selected = backStackEntry?.destination?.hierarchy?.any { it.route == ziel.route } == true,
                            onClick = { navController.wechsleZu(ziel.route) },
                            icon = { Icon(ziel.icon, contentDescription = null) },
                            label = { Text(stringResource(ziel.titelRes)) },
                            alwaysShowLabel = true,
                        )
                    }
                }
            }

            NavHost(
                navController = navController,
                startDestination = startZiel,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (breitesLayout) PaddingValues() else innerPadding),
            ) {
                composable(ROUTE_NAME) {
                    NameScreen(
                        anfangsname = settings.displayName,
                        onWeiter = { name ->
                            container.settingsRepository.update { it.copy(displayName = name) }
                            val naechste = if (container.settingsRepository.settings.value.onboardingCompleted) {
                                Ziel.TRAINING.route
                            } else {
                                ROUTE_ONBOARDING
                            }
                            navController.navigate(naechste) {
                                popUpTo(ROUTE_NAME) { inclusive = true }
                            }
                        },
                    )
                }

                composable(ROUTE_ONBOARDING) {
                    OnboardingRoute(
                        gewaehlteQuelle = settings.sourceKind,
                        anzeigename = settings.displayName,
                        onQuelleWechseln = { kind ->
                            container.settingsRepository.update { it.copy(sourceKind = kind) }
                        },
                        onAbgeschlossen = {
                            container.settingsRepository.update { it.copy(onboardingCompleted = true) }
                            navController.wechsleZu(Ziel.KALIBRIERUNG.route)
                        },
                    )
                }

                composable(Ziel.TRAINING.route) {
                    val viewModel: TrainingViewModel = viewModel(
                        factory = TrainingViewModel.factory(
                            sourceFactory = container.sourceFactory,
                            settingsRepository = container.settingsRepository,
                            mvcRepository = container.mvcRepository,
                            sessionRepository = container.sessionRepository,
                            classifier = container.classifier,
                        ),
                    )
                    TrainingRoute(
                        viewModel = viewModel,
                        windowSizeClass = windowSizeClass,
                        hapticsEnabled = settings.hapticsEnabled,
                        onQuelleWechseln = { kind: EmgSourceKind ->
                            container.settingsRepository.update { it.copy(sourceKind = kind) }
                        },
                        onEinstellungen = { navController.wechsleZu(Ziel.EINSTELLUNGEN.route) },
                        eigeneUebungen = settings.customExercises,
                        onUebungWaehlen = { uebung ->
                            container.settingsRepository.update { it.copy(selectedExerciseId = uebung.id) }
                        },
                        onEigenAnlegen = { name, vorlage ->
                            container.settingsRepository.update { it.mitEigenerUebung(name, vorlage) }
                        },
                        onEigenLoeschen = { id ->
                            container.settingsRepository.update { it.ohneEigeneUebung(id) }
                        },
                    )
                }

                composable(Ziel.KALIBRIERUNG.route) {
                    val viewModel: CalibrationViewModel = viewModel(
                        factory = CalibrationViewModel.factory(
                            sourceFactory = container.sourceFactory,
                            settingsRepository = container.settingsRepository,
                            mvcRepository = container.mvcRepository,
                        ),
                    )
                    CalibrationRoute(
                        viewModel = viewModel,
                        settings = settings,
                        onFertig = { navController.wechsleZu(Ziel.TRAINING.route) },
                        onUebungWaehlen = { uebung ->
                            container.settingsRepository.update { it.copy(selectedExerciseId = uebung.id) }
                        },
                        onEigenAnlegen = { name, vorlage ->
                            container.settingsRepository.update { it.mitEigenerUebung(name, vorlage) }
                        },
                        onEigenLoeschen = { id ->
                            container.settingsRepository.update { it.ohneEigeneUebung(id) }
                        },
                    )
                }

                composable(Ziel.VERLAUF.route) {
                    val viewModel: HistoryViewModel = viewModel(
                        factory = HistoryViewModel.factory(container.sessionRepository),
                    )
                    HistoryRoute(
                        viewModel = viewModel,
                        anzeigename = settings.displayName,
                        onTrainingStarten = { navController.wechsleZu(Ziel.TRAINING.route) },
                    )
                }

                composable(Ziel.EINSTELLUNGEN.route) {
                    SettingsScreen(
                        settings = settings,
                        onSettingsChange = { neu -> container.settingsRepository.update { neu } },
                        onZuruecksetzen = {
                            container.settingsRepository.update { AppSettings() }
                            container.mvcRepository.clear()
                            umfang.launch { container.sessionRepository.clear() }
                            navController.navigate(ROUTE_NAME) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    inclusive = true
                                }
                                launchSingleTop = true
                            }
                        },
                    )
                }
            }
        }
    }
}

private fun AppSettings.mitEigenerUebung(name: String, templateId: String): AppSettings {
    val bereinigt = AppSettings.nameBereinigen(name)
    if (bereinigt.isEmpty()) return this
    val id = "eigen-${UUID.randomUUID()}"
    return copy(
        customExercises = customExercises + CustomExercise(id, bereinigt, templateId),
        selectedExerciseId = id,
    )
}

private fun AppSettings.ohneEigeneUebung(id: String): AppSettings {
    val rest = customExercises.filterNot { it.id == id }
    return copy(
        customExercises = rest,
        selectedExerciseId = if (selectedExerciseId == id) {
            rest.lastOrNull()?.id ?: ExerciseCatalog.bankdruecken.id
        } else {
            selectedExerciseId
        },
    )
}

/**
 * Wechselt das Ziel, ohne den Rücksprungstapel aufzublähen.
 *
 * Ohne `launchSingleTop` und das Zurücksetzen bis zum Startziel entstünde bei
 * mehrfachem Hin- und Herwechseln ein Stapel, aus dem der Nutzer sich mit der
 * Zurück-Taste minutenlang herausklicken müsste.
 */
private fun androidx.navigation.NavHostController.wechsleZu(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
