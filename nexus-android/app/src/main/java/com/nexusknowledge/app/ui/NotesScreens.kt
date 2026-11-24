@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.nexusknowledge.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.nexusknowledge.app.data.Links
import com.nexusknowledge.app.data.Note

private const val FAVORITAS = "★ Favoritas"

// ---------------------------------------------------------------- Lista

@Composable
fun NoteListScreen(
    notes: List<Note>,
    onOpen: (Long) -> Unit,
    onNew: () -> Unit,
    onRestoreSamples: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    val categories = notes.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
    val shown = Links.search(query, notes).filter {
        when (filter) {
            "" -> true
            FAVORITAS -> it.favorite
            else -> it.category == filter
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mis notas") },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Más opciones") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Recuperar conceptos de ejemplo") },
                            onClick = { menu = false; onRestoreSamples() },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNew) { Icon(Icons.Filled.Add, "Nueva nota") }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Buscar en títulos, contenido y etiquetas") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, "Borrar") }
                },
                singleLine = true,
            )
            LazyRow(
                modifier = Modifier.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            ) {
                val chips = listOf("Todas" to "", FAVORITAS to FAVORITAS) + categories.map { it to it }
                items(chips) { (label, value) ->
                    FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(label) })
                }
            }
            if (shown.isEmpty()) {
                Text(
                    "No hay notas que coincidan.",
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(shown, key = { it.id }) { note -> NoteCard(note) { onOpen(note.id) } }
            }
        }
    }
}

@Composable
private fun NoteCard(note: Note, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    note.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (note.favorite) Icon(Icons.Filled.Favorite, "Favorita", tint = MaterialTheme.colorScheme.primary)
            }
            val meta = listOf(note.category).filter { it.isNotBlank() } + note.tags.map { "#$it" }
            if (meta.isNotEmpty()) {
                Text(
                    meta.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                note.content.replace(Regex("""\[\[|]]"""), ""),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- Ficha

@Composable
fun NoteDetailScreen(
    id: Long,
    notes: List<Note>,
    onBack: () -> Unit,
    onOpenLink: (String) -> Unit,
    onEdit: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    val note = notes.firstOrNull { it.id == id }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(note?.category?.ifBlank { "Nota" } ?: "Nota") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") }
                },
                actions = {
                    if (note != null) {
                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                if (note.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                if (note.favorite) "Quitar de favoritas" else "Marcar como favorita",
                            )
                        }
                        IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Editar") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Borrar") }
                    }
                },
            )
        },
    ) { padding ->
        if (note == null) {
            Text("Esta nota ya no existe.", Modifier.padding(padding).padding(24.dp))
            return@Scaffold
        }
        val linkColor = MaterialTheme.colorScheme.primary
        val text = remember(note.content, notes, linkColor) {
            buildAnnotatedString {
                Links.segments(note.content).forEach { seg ->
                    when (seg) {
                        is Links.Segment.Plain -> append(seg.text)
                        is Links.Segment.Link -> {
                            val exists = Links.findByTitle(seg.title, notes) != null
                            withLink(
                                LinkAnnotation.Clickable(
                                    tag = "nota:${seg.title}",
                                    styles = TextLinkStyles(
                                        style = SpanStyle(
                                            color = linkColor,
                                            fontWeight = FontWeight.SemiBold,
                                            textDecoration = if (exists) TextDecoration.Underline else TextDecoration.LineThrough,
                                        ),
                                    ),
                                    linkInteractionListener = { onOpenLink(seg.title) },
                                ),
                            ) { append(seg.title) }
                        }
                    }
                }
            }
        }
        val backlinks = Links.backlinks(note, notes)

        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            Text(note.title, style = MaterialTheme.typography.headlineSmall)
            if (note.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                    note.tags.forEach { AssistChip(onClick = {}, label = { Text("#$it") }) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge)
            Text(
                "Dominio en Repaso: ${"●".repeat(note.mastery)}${"○".repeat(3 - note.mastery)}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 16.dp),
            )

            Spacer(Modifier.height(24.dp))
            Text("Mencionada en", style = MaterialTheme.typography.titleMedium)
            if (backlinks.isEmpty()) {
                Text("Ninguna nota enlaza todavía a esta.", style = MaterialTheme.typography.bodyMedium)
            }
            backlinks.forEach { b ->
                TextButton(onClick = { onOpenLink(b.title) }) { Text("← ${b.title}") }
            }
            Text(
                "Consejo: escribe [[Título]] en cualquier nota para enlazarla. Los enlaces tachados llevan a notas que aún no existen: púlsalos para crearlas.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }

    if (confirmDelete && note != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Borrar «${note.title}»?") },
            text = { Text("No se puede deshacer. Los enlaces que apuntan a ella quedarán tachados.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

// ---------------------------------------------------------------- Editor

@Composable
fun NoteEditScreen(
    original: Note?,
    presetTitle: String,
    notes: List<Note>,
    onCancel: () -> Unit,
    onSave: (Note) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(original?.title ?: presetTitle) }
    var category by rememberSaveable { mutableStateOf(original?.category ?: "") }
    var tags by rememberSaveable { mutableStateOf(original?.tags?.joinToString(", ") ?: "") }
    var content by rememberSaveable { mutableStateOf(original?.content ?: "") }

    val duplicate = notes.any { it.id != original?.id && it.title.equals(title.trim(), ignoreCase = true) }
    val error = when {
        title.isBlank() -> "El título es obligatorio."
        duplicate -> "Ya existe una nota con ese título."
        else -> null
    }
    val categories = notes.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (original == null) "Nueva nota" else "Editar nota") },
                navigationIcon = {
                    IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancelar") }
                },
                actions = {
                    IconButton(
                        enabled = error == null,
                        onClick = {
                            val base = original ?: Note(id = 0, title = "", content = "")
                            onSave(
                                base.copy(
                                    title = title.trim(),
                                    category = category.trim(),
                                    tags = Links.parseTags(tags),
                                    content = content.trim(),
                                ),
                            )
                        },
                    ) { Icon(Icons.Filled.Check, "Guardar") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Título (en Repaso, la pregunta)") },
                isError = error != null,
                supportingText = { error?.let { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text("Categoría") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (categories.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
                    }
                }
            }
            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                label = { Text("Etiquetas, separadas por comas") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("Contenido (en Repaso, la respuesta)") },
                placeholder = { Text("Usa [[Otra nota]] para enlazar conceptos.") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
            )
        }
    }
}
