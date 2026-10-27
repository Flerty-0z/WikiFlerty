package com.flerty.wikiflerty.ui

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flerty.wikiflerty.reader.ReaderActivity
import kotlinx.coroutines.launch

/** The main sections of the app, opened from the side panel. */
enum class Section(val title: String, val icon: ImageVector) {
    Library("Библиотека", Icons.Filled.Home),
    Notes("Заметки", Icons.Filled.Edit),
    Dictionary("Словарь", Icons.Filled.Search),
    Sync("Синхронизация", Icons.Filled.Refresh),
    Settings("Настройки", Icons.Filled.Settings),
    About("О приложении", Icons.Filled.Info),
}

@Composable
fun MainScreen(libraryViewModel: LibraryViewModel) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var section by rememberSaveable { mutableStateOf(Section.Library) }
    val busy by libraryViewModel.busy.collectAsStateWithLifecycle()
    val feedback = LocalFeedback.current
    val view = LocalView.current

    val pickBook = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) libraryViewModel.import(uri)
    }

    LaunchedEffect(Unit) {
        libraryViewModel.openReader.collect {
            context.startActivity(Intent(context, ReaderActivity::class.java))
        }
    }
    LaunchedEffect(Unit) {
        libraryViewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    BackHandler(enabled = drawerState.isOpen || section != Section.Library) {
        if (drawerState.isOpen) scope.launch { drawerState.close() } else section = Section.Library
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    "WikiFlerty",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 28.dp, top = 24.dp, bottom = 20.dp),
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp))
                Spacer(Modifier.height(12.dp))
                Section.entries.forEach { item ->
                    if (item == Section.Settings) Spacer(Modifier.height(12.dp))
                    NavigationDrawerItem(
                        label = { Text(item.title) },
                        icon = { Icon(item.icon, contentDescription = null) },
                        selected = item == section,
                        onClick = {
                            feedback?.play(FeedbackSound.Tap, view)
                            section = item
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = { Text(section.title) },
                        navigationIcon = {
                            IconButton(onClick = {
                                feedback?.play(FeedbackSound.Open, view)
                                scope.launch { drawerState.open() }
                            }) {
                                Icon(Icons.Filled.Menu, contentDescription = "Меню")
                            }
                        },
                    )
                    if (busy != null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            },
            floatingActionButton = {
                if (section == Section.Library) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            feedback?.play(FeedbackSound.Open, view)
                            pickBook.launch(arrayOf("application/epub+zip", "application/octet-stream"))
                        },
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        text = { Text("Книга") },
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            val modifier = Modifier.fillMaxSize().padding(padding)
            when (section) {
                Section.Library -> LibraryScreen(
                    viewModel = libraryViewModel,
                    onAddBook = { pickBook.launch(arrayOf("application/epub+zip", "application/octet-stream")) },
                    modifier = modifier,
                )
                Section.Notes -> NotesScreen(libraryViewModel, modifier)
                Section.Dictionary -> DictionaryScreen(libraryViewModel, modifier)
                Section.Sync -> PlaceholderScreen(
                    "Синхронизация прогресса, заметок и словаря с Google Drive и Dropbox, а также экспорт и импорт всех данных одним файлом.",
                    modifier,
                )
                Section.Settings -> SettingsScreen(modifier)
                Section.About -> AboutScreen(modifier)
            }
        }
    }
}
