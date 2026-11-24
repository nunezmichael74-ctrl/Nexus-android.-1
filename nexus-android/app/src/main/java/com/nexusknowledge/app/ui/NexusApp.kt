package com.nexusknowledge.app.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import com.nexusknowledge.app.data.NoteStore

private enum class Tab(val label: String, val icon: ImageVector) {
    Comunidad("Comunidad", Icons.Filled.Person),
    Notas("Mis notas", Icons.Filled.Edit),
    Repaso("Repaso", Icons.Filled.Refresh),
}

/** Pantallas internas de «Mis notas». */
sealed interface NotesRoute {
    data object Home : NotesRoute
    data class Detail(val id: Long) : NotesRoute
    /** id = 0 → nota nueva; presetTitle rellena el título al crear desde un [[enlace]]. */
    data class Edit(val id: Long, val presetTitle: String = "") : NotesRoute
}

@Composable
fun NexusApp(store: NoteStore) {
    val context = LocalContext.current
    val notes by store.notes.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.Comunidad) }
    var route by remember { mutableStateOf<NotesRoute>(NotesRoute.Home) }
    // Pila sencilla para volver atrás entre notas enlazadas.
    val history = remember { ArrayDeque<NotesRoute>() }
    val webView = remember { createCommunityWebView(context) }

    fun go(to: NotesRoute) { history.addLast(route); route = to }
    fun back() { route = history.removeLastOrNull() ?: NotesRoute.Home }

    BackHandler(enabled = tab == Tab.Comunidad) {
        if (webView.canGoBack()) webView.goBack() else (context as? Activity)?.finish()
    }
    BackHandler(enabled = tab == Tab.Notas && route != NotesRoute.Home) { back() }
    BackHandler(enabled = tab != Tab.Comunidad && (tab != Tab.Notas || route == NotesRoute.Home)) {
        tab = Tab.Comunidad
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = {
                            if (t == Tab.Notas && tab == Tab.Notas) { history.clear(); route = NotesRoute.Home }
                            tab = t
                        },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.Comunidad -> CommunityScreen(webView)
                Tab.Notas -> when (val r = route) {
                    NotesRoute.Home -> NoteListScreen(
                        notes = notes,
                        onOpen = { go(NotesRoute.Detail(it)) },
                        onNew = { go(NotesRoute.Edit(0)) },
                        onRestoreSamples = { store.restoreSamples() },
                    )
                    is NotesRoute.Detail -> NoteDetailScreen(
                        id = r.id,
                        notes = notes,
                        onBack = ::back,
                        onOpenLink = { title ->
                            val target = com.nexusknowledge.app.data.Links.findByTitle(title, notes)
                            go(if (target != null) NotesRoute.Detail(target.id) else NotesRoute.Edit(0, title))
                        },
                        onEdit = { go(NotesRoute.Edit(r.id)) },
                        onToggleFavorite = { store.toggleFavorite(r.id) },
                        onDelete = { store.delete(r.id); history.clear(); route = NotesRoute.Home },
                    )
                    is NotesRoute.Edit -> NoteEditScreen(
                        original = store.get(r.id),
                        presetTitle = r.presetTitle,
                        notes = notes,
                        onCancel = ::back,
                        onSave = { saved ->
                            val s = store.save(saved)
                            // Tras guardar, se sustituye el editor por la ficha de la nota.
                            route = NotesRoute.Detail(s.id)
                        },
                    )
                }
                Tab.Repaso -> ReviewScreen(notes = notes, onRate = { id, level -> store.setMastery(id, level) })
            }
        }
    }
}
