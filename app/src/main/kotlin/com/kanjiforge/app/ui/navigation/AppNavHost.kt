package com.kanjiforge.app.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.kanjiforge.app.AppContainer
import com.kanjiforge.app.data.reference.CatalogBootstrapState
import com.kanjiforge.app.data.reference.ReferenceCatalog
import com.kanjiforge.app.data.reference.model.StudySequenceFamily
import com.kanjiforge.app.domain.QuizPrompt
import com.kanjiforge.app.domain.SessionMode
import com.kanjiforge.app.domain.SessionSpec
import com.kanjiforge.app.domain.StudyPreferences
import com.kanjiforge.app.ui.components.KanjiHaptic
import com.kanjiforge.app.ui.components.LocalSnackbarHostState
import com.kanjiforge.app.ui.components.LocalSpeechSpeaker
import com.kanjiforge.app.ui.components.rememberKanjiHaptics
import com.kanjiforge.app.ui.components.rememberReduceMotion
import com.kanjiforge.app.ui.kanji.KanjiDetailScreen
import com.kanjiforge.app.ui.kanji.KanjiDetailViewModel
import com.kanjiforge.app.ui.kanji.KanjiListScreen
import com.kanjiforge.app.ui.kanji.KanjiListViewModel
import com.kanjiforge.app.ui.library.KanaLibraryScreen
import com.kanjiforge.app.ui.library.KanaLibraryViewModel
import com.kanjiforge.app.ui.library.OutlierLibraryScreen
import com.kanjiforge.app.ui.library.OutlierLibraryViewModel
import com.kanjiforge.app.ui.library.RadicalsLibraryScreen
import com.kanjiforge.app.ui.library.RadicalsLibraryViewModel
import com.kanjiforge.app.ui.progress.ProgressScreen
import com.kanjiforge.app.ui.progress.ProgressViewModel
import com.kanjiforge.app.ui.sets.CollectionDetailScreen
import com.kanjiforge.app.ui.sets.CollectionDetailViewModel
import com.kanjiforge.app.ui.sets.CollectionsScreen
import com.kanjiforge.app.ui.sets.CollectionsViewModel
import com.kanjiforge.app.ui.settings.SettingsScreen
import com.kanjiforge.app.ui.settings.SettingsViewModel
import com.kanjiforge.app.ui.setup.CatalogSetupScreen
import com.kanjiforge.app.ui.study.QuizFocusPickerDialog
import com.kanjiforge.app.ui.study.StudyLevelsScreen
import com.kanjiforge.app.ui.study.StudyLevelsViewModel
import com.kanjiforge.app.ui.study.StudySequencesScreen
import com.kanjiforge.app.ui.study.StudySequencesViewModel
import com.kanjiforge.app.ui.study.StudySessionScreen
import com.kanjiforge.app.ui.study.StudySessionViewModel
import com.kanjiforge.app.ui.study.StudySetsScreen
import com.kanjiforge.app.ui.study.StudySetsViewModel
import com.kanjiforge.app.ui.theme.KanjiForgeTheme
import com.kanjiforge.app.ui.today.TodayScreen
import com.kanjiforge.app.ui.today.TodayViewModel
import com.kanjiforge.app.ui.words.WordDetailScreen
import com.kanjiforge.app.ui.words.WordDetailViewModel
import com.kanjiforge.app.ui.words.WordListScreen
import com.kanjiforge.app.ui.words.WordListViewModel
import kotlinx.coroutines.launch

@Composable
fun KanjiForgeApp(container: AppContainer) {
    val bootstrapState by container.catalogBootstrap.state.collectAsStateWithLifecycle()
    when (val current = bootstrapState) {
        is CatalogBootstrapState.Copying,
        CatalogBootstrapState.Verifying,
        is CatalogBootstrapState.Failed,
        -> CatalogSetupScreen(current, onRetry = container.catalogBootstrap::retry)

        is CatalogBootstrapState.Ready -> {
            val studyCatalog = remember(current.catalog) { container.studyCatalogFor(current.catalog) }
            CompositionLocalProvider(LocalSpeechSpeaker provides container.speechSpeaker) {
                AppShell(container = container, reference = current.catalog, studyCatalog = studyCatalog)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppShell(
    container: AppContainer,
    reference: ReferenceCatalog,
    studyCatalog: com.kanjiforge.app.domain.StudyCatalog,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, studyCatalog) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                studyCatalog.refreshClock()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRouteKey = backStack?.destination?.toAppRouteKey() ?: AppRouteKey.TODAY
    val chrome = resolveChrome(currentRouteKey)
    val drawerSelection = drawerSelectionFor(currentRouteKey)

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = rememberKanjiHaptics()
    val reduceMotion = rememberReduceMotion()
    val scope = rememberCoroutineScope()
    var sessionExitHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
    val preferences by container.studyStore.preferences.collectAsStateWithLifecycle(initialValue = StudyPreferences())
    var pendingQuizSpec by remember { mutableStateOf<SessionSpec?>(null) }

    fun launchSession(mode: SessionMode, spec: SessionSpec, quizFocus: QuizPrompt? = null) {
        if (mode == SessionMode.QUIZ && quizFocus != null) {
            scope.launch {
                container.studyStore.updatePreferences { it.copy(quizPrompt = quizFocus) }
            }
            navController.navigate(StudySessionRoute.from(mode, spec, quizFocus))
        } else if (mode == SessionMode.QUIZ) {
            pendingQuizSpec = spec
        } else {
            navController.navigate(StudySessionRoute.from(mode, spec))
        }
    }

    fun navigateToTopLevel(route: Any) {
        navController.navigate(route) {
            popUpTo<TodayRoute> { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = drawerState.isOpen,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.widthIn(max = 304.dp),
                    drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 22.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                "KanjiForge",
                                style = MaterialTheme.typography.headlineSmall,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Offline Japanese study",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider(color = KanjiForgeTheme.colors.dockBorder.copy(alpha = 0.7f))
                        LazyColumn(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            DrawerSection.entries.forEachIndexed { sectionIndex, section ->
                                val sectionItems = drawerDestinations.filter { it.section == section }
                                if (sectionItems.isNotEmpty()) {
                                    if (sectionIndex > 0) {
                                        item(key = "divider-${section.name}") {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                                                color = MaterialTheme.colorScheme.outlineVariant,
                                            )
                                        }
                                    }
                                    item(key = "header-${section.name}") {
                                        Text(
                                            text = section.title,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                                        )
                                    }
                                    items(sectionItems, key = { it.key.name }) { item ->
                                        NavigationDrawerItem(
                                            label = { Text(item.label, style = MaterialTheme.typography.titleSmall) },
                                            selected = drawerSelection == item.key,
                                            icon = { Icon(item.icon, contentDescription = null) },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = NavigationDrawerItemDefaults.colors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                unselectedTextColor = MaterialTheme.colorScheme.onSurface,
                                            ),
                                            onClick = {
                                                haptics(KanjiHaptic.TICK)
                                                scope.launch { drawerState.close() }
                                                navigateToTopLevel(item.route)
                                            },
                                            modifier = Modifier.padding(horizontal = 12.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
        ) {
            val colors = KanjiForgeTheme.colors
            val selectedTab = primaryTabSelectionFor(currentRouteKey)

            BoxWithConstraints(Modifier.fillMaxSize()) {
                val useRail = maxWidth >= 600.dp

                Row(Modifier.fillMaxSize()) {
                    if (useRail && chrome.showBottomNav) {
                        NavigationRail(
                            containerColor = colors.dockContainer,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            header = {
                                IconButton(onClick = {
                                    haptics(KanjiHaptic.TAP)
                                    scope.launch { drawerState.open() }
                                }) {
                                    Icon(Icons.Outlined.Menu, contentDescription = "Open navigation")
                                }
                            },
                        ) {
                            Spacer(Modifier.weight(1f))
                            primaryTabDestinations.forEach { tab ->
                                val selected = selectedTab == tab.key
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = {
                                        if (!selected) {
                                            haptics(KanjiHaptic.TICK)
                                            navigateToTopLevel(tab.route)
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            if (selected) tab.selectedIcon else tab.icon,
                                            contentDescription = null,
                                        )
                                    },
                                    label = { Text(tab.label, maxLines = 1) },
                                    colors = NavigationRailItemDefaults.colors(
                                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                )
                            }
                            Spacer(Modifier.weight(1f))
                        }
                    }

                    Scaffold(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.background,
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        topBar = {
                            Column {
                                CenterAlignedTopAppBar(
                                    navigationIcon = {
                                        if (!chrome.isTopLevel || !(useRail && chrome.showBottomNav)) {
                                            IconButton(
                                                onClick = {
                                                    haptics(KanjiHaptic.TAP)
                                                    if (chrome.isTopLevel) {
                                                        scope.launch { drawerState.open() }
                                                    } else {
                                                        val activeExit = sessionExitHandler
                                                        if (currentRouteKey == AppRouteKey.STUDY_SESSION && activeExit != null) {
                                                            activeExit()
                                                        } else {
                                                            navController.popBackStack()
                                                        }
                                                    }
                                                },
                                            ) {
                                                Icon(
                                                    if (chrome.isTopLevel) Icons.Outlined.Menu else Icons.AutoMirrored.Outlined.ArrowBack,
                                                    contentDescription = if (chrome.isTopLevel) "Open navigation" else "Go back",
                                                )
                                            }
                                        }
                                    },
                                    title = {
                                        Text(
                                            chrome.title,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontFamily = FontFamily.Serif,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    },
                                    actions = {
                                        if (chrome.showSearchFab) {
                                            IconButton(onClick = {
                                                haptics(KanjiHaptic.TAP)
                                                navigateToTopLevel(KanjiListRoute())
                                            }) {
                                                Icon(Icons.Outlined.Search, contentDescription = "Search kanji")
                                            }
                                        }
                                        if (chrome.showSettings) {
                                            IconButton(onClick = {
                                                haptics(KanjiHaptic.TAP)
                                                navController.navigate(SettingsRoute) { launchSingleTop = true }
                                            }) {
                                                Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                                            }
                                        } else if (!chrome.showSearchFab) {
                                            Spacer(Modifier.widthIn(min = 48.dp))
                                        }
                                    },
                                    colors = TopAppBarDefaults.topAppBarColors(
                                        containerColor = colors.topBarContainer,
                                        titleContentColor = colors.topBarContent,
                                        navigationIconContentColor = colors.topBarContent,
                                        actionIconContentColor = colors.topBarContent,
                                    ),
                                )
                                HorizontalDivider(thickness = 0.75.dp, color = colors.dockBorder.copy(alpha = 0.6f))
                            }
                        },
                        bottomBar = {
                            if (!useRail && chrome.showBottomNav) {
                                Column {
                                    HorizontalDivider(thickness = 0.75.dp, color = colors.dockBorder.copy(alpha = 0.75f))
                                    NavigationBar(containerColor = colors.dockContainer) {
                                        primaryTabDestinations.forEach { tab ->
                                            val selected = selectedTab == tab.key
                                            NavigationBarItem(
                                                selected = selected,
                                                onClick = {
                                                    if (!selected) {
                                                        haptics(KanjiHaptic.TICK)
                                                        navigateToTopLevel(tab.route)
                                                    }
                                                },
                                                icon = {
                                                    Icon(
                                                        if (selected) tab.selectedIcon else tab.icon,
                                                        contentDescription = null,
                                                    )
                                                },
                                                label = { Text(tab.label, maxLines = 1) },
                                                colors = NavigationBarItemDefaults.colors(
                                                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                        },
                    ) { contentPadding ->
                        val maxContentWidth = when (currentRouteKey) {
                            AppRouteKey.TODAY -> 1040.dp
                            AppRouteKey.STUDY_SESSION -> 880.dp
                            AppRouteKey.KANJI_DETAIL,
                            AppRouteKey.KANJI_LIST,
                            AppRouteKey.WORD_LIST,
                            AppRouteKey.STUDY_SEQUENCES,
                            AppRouteKey.STUDY_LEVELS,
                            AppRouteKey.STUDY_SETS,
                            -> 1000.dp
                            else -> 760.dp
                        }
                        CenteredContent(
                            maxWidth = maxContentWidth,
                            modifier = Modifier.padding(contentPadding).fillMaxSize(),
                        ) {
                            NavHost(
                                navController = navController,
                                startDestination = TodayRoute,
                                modifier = Modifier.fillMaxSize(),
                                enterTransition = { if (reduceMotion) EnterTransition.None else fadeIn(tween(180)) },
                                exitTransition = { if (reduceMotion) ExitTransition.None else fadeOut(tween(140)) },
                                popEnterTransition = { if (reduceMotion) EnterTransition.None else fadeIn(tween(180)) },
                                popExitTransition = { if (reduceMotion) ExitTransition.None else fadeOut(tween(140)) },
                            ) {
                    composable<TodayRoute> {
                        val vm = rememberVm("today") { TodayViewModel(studyCatalog) }
                        TodayScreen(
                            viewModel = vm,
                            onStart = { mode ->
                                launchSession(mode, SessionSpec.DailyReview)
                            },
                            onStudy = {
                                navigateToTopLevel(StudySequencesRoute)
                            },
                            onKana = {
                                navController.navigate(KanaLibraryRoute) { launchSingleTop = true }
                            },
                            onBeginnerKanji = {
                                navController.navigate(StudySetsRoute.from(StudySequenceFamily.JLPT, 5)) { launchSingleTop = true }
                            },
                            onProgress = {
                                navController.navigate(ProgressRoute) { launchSingleTop = true }
                            },
                        )
                    }

                    composable<KanjiListRoute> { entry ->
                        val route = entry.toRoute<KanjiListRoute>()
                        val vm = rememberVm("kanji-list-${route.initialGrade}") {
                            KanjiListViewModel(route.initialGrade, studyCatalog, reference)
                        }
                        KanjiListScreen(vm) { code ->
                            navController.navigate(KanjiDetailRoute(code))
                        }
                    }

                    composable<KanjiDetailRoute> { entry ->
                        val route = entry.toRoute<KanjiDetailRoute>()
                        val vm = rememberVm("kanji-detail-${route.code}") {
                            KanjiDetailViewModel(route.code, studyCatalog)
                        }
                        KanjiDetailScreen(
                            viewModel = vm,
                            onStart = { mode, selectedCode ->
                                navController.navigate(StudySessionRoute.from(mode, SessionSpec.SingleKanji(selectedCode)))
                            },
                            onWord = { wordId -> navController.navigate(WordDetailRoute(wordId)) },
                        )
                    }

                    composable<WordListRoute> {
                        val vm = rememberVm("word-list") { WordListViewModel(reference) }
                        WordListScreen(vm) { id -> navController.navigate(WordDetailRoute(id)) }
                    }

                    composable<WordDetailRoute> { entry ->
                        val route = entry.toRoute<WordDetailRoute>()
                        val vm = rememberVm("word-detail-${route.id}") {
                            WordDetailViewModel(route.id, studyCatalog)
                        }
                        WordDetailScreen(vm)
                    }

                    composable<StudySequencesRoute> {
                        val vm = rememberVm("study-sequences") { StudySequencesViewModel(studyCatalog) }
                        StudySequencesScreen(
                            viewModel = vm,
                            onSelectSequence = { family ->
                                navController.navigate(StudyLevelsRoute.from(family))
                            },
                            onStartDaily = { mode ->
                                launchSession(mode, SessionSpec.DailyReview)
                            },
                        )
                    }

                    composable<StudyLevelsRoute> { entry ->
                        val route = entry.toRoute<StudyLevelsRoute>()
                        val vm = rememberVm("study-levels-${route.family.name}") {
                            StudyLevelsViewModel(route.family, studyCatalog)
                        }
                        StudyLevelsScreen(vm) { level ->
                            navController.navigate(StudySetsRoute.from(route.family, level))
                        }
                    }

                    composable<StudySetsRoute> { entry ->
                        val route = entry.toRoute<StudySetsRoute>()
                        val vm = rememberVm("study-sets-${route.family.name}-${route.level}") {
                            StudySetsViewModel(route.family, route.level, studyCatalog)
                        }
                        StudySetsScreen(
                            viewModel = vm,
                            defaultQuizFocus = preferences.quizPrompt,
                        ) { mode, quizFocus, offset, batchSize ->
                            launchSession(
                                mode,
                                SessionSpec.SequenceSet(route.family, route.level, offset, batchSize),
                                quizFocus,
                            )
                        }
                    }

                    composable<StudySessionRoute> { entry ->
                        val route = entry.toRoute<StudySessionRoute>()
                        val spec = route.toSpec()
                        val vm = rememberVm("study-session-${route.modeName}-${route.quizPromptName}-${route.specKind}-${route.code}-${route.setId}-${route.familyName}-${route.level}-${route.offset}-${route.batchSize}") {
                            StudySessionViewModel(
                                mode = route.mode,
                                spec = spec,
                                catalog = reference,
                                store = container.studyStore,
                                clock = container.clock,
                                applicationScope = container.applicationScope,
                                quizFocus = route.quizPrompt,
                            )
                        }
                        StudySessionScreen(
                            viewModel = vm,
                            registerExitHandler = { handler -> sessionExitHandler = handler },
                            onExit = { navController.popBackStack() },
                        )
                    }

                    composable<CollectionsRoute> {
                        val vm = rememberVm("collections") { CollectionsViewModel(studyCatalog) }
                        CollectionsScreen(vm) { setId ->
                            navController.navigate(CollectionDetailRoute(setId))
                        }
                    }

                    composable<CollectionDetailRoute> { entry ->
                        val route = entry.toRoute<CollectionDetailRoute>()
                        val vm = rememberVm("collection-detail-${route.setId}") {
                            CollectionDetailViewModel(route.setId, studyCatalog)
                        }
                        CollectionDetailScreen(
                            viewModel = vm,
                            defaultQuizFocus = preferences.quizPrompt,
                            onStartStudy = { mode, quizFocus, setId ->
                                launchSession(mode, SessionSpec.Collection(setId), quizFocus)
                            },
                            onOpenKanji = { code -> navController.navigate(KanjiDetailRoute(code)) },
                            onOpenWord = { wordId -> navController.navigate(WordDetailRoute(wordId)) },
                            onDeleted = { navController.popBackStack() },
                        )
                    }

                    composable<KanaLibraryRoute> {
                        val vm = rememberVm("library-kana") { KanaLibraryViewModel(reference) }
                        KanaLibraryScreen(vm)
                    }

                    composable<RadicalsLibraryRoute> {
                        val vm = rememberVm("library-radicals") { RadicalsLibraryViewModel(reference) }
                        RadicalsLibraryScreen(vm)
                    }

                    composable<OutlierLibraryRoute> {
                        val vm = rememberVm("library-outlier") { OutlierLibraryViewModel(reference) }
                        OutlierLibraryScreen(vm)
                    }

                    composable<ProgressRoute> {
                        val vm = rememberVm("progress") { ProgressViewModel(studyCatalog) }
                        ProgressScreen(vm)
                    }

                            composable<SettingsRoute> {
                                val vm = rememberVm("settings") { SettingsViewModel(container.studyStore, container.backupManager) }
                                SettingsScreen(vm)
                            }
                        }
                        pendingQuizSpec?.let { spec ->
                            QuizFocusPickerDialog(
                                currentFocus = preferences.quizPrompt,
                                onSelectFocus = { focus ->
                                    pendingQuizSpec = null
                                    launchSession(SessionMode.QUIZ, spec, focus)
                                },
                                onDismiss = { pendingQuizSpec = null },
                            )
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
private fun CenteredContent(
    maxWidth: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier) {
        Box(
            modifier = Modifier.widthIn(max = maxWidth).fillMaxWidth().fillMaxHeight().align(Alignment.TopCenter),
        ) {
            content()
        }
    }
}

@Composable
private inline fun <reified VM : ViewModel> rememberVm(
    key: String,
    crossinline create: () -> VM,
): VM {
    val factory = remember(key) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
        }
    }
    return viewModel(key = key, factory = factory)
}

private fun NavDestination.toAppRouteKey(): AppRouteKey = when {
    hasRoute<TodayRoute>() -> AppRouteKey.TODAY
    hasRoute<KanjiListRoute>() -> AppRouteKey.KANJI_LIST
    hasRoute<KanjiDetailRoute>() -> AppRouteKey.KANJI_DETAIL
    hasRoute<WordListRoute>() -> AppRouteKey.WORD_LIST
    hasRoute<WordDetailRoute>() -> AppRouteKey.WORD_DETAIL
    hasRoute<StudySequencesRoute>() -> AppRouteKey.STUDY_SEQUENCES
    hasRoute<StudyLevelsRoute>() -> AppRouteKey.STUDY_LEVELS
    hasRoute<StudySetsRoute>() -> AppRouteKey.STUDY_SETS
    hasRoute<StudySessionRoute>() -> AppRouteKey.STUDY_SESSION
    hasRoute<CollectionsRoute>() -> AppRouteKey.COLLECTIONS
    hasRoute<CollectionDetailRoute>() -> AppRouteKey.COLLECTION_DETAIL
    hasRoute<KanaLibraryRoute>() -> AppRouteKey.KANA_LIBRARY
    hasRoute<RadicalsLibraryRoute>() -> AppRouteKey.RADICALS_LIBRARY
    hasRoute<OutlierLibraryRoute>() -> AppRouteKey.OUTLIER_LIBRARY
    hasRoute<ProgressRoute>() -> AppRouteKey.PROGRESS
    hasRoute<SettingsRoute>() -> AppRouteKey.SETTINGS
    else -> AppRouteKey.TODAY
}
