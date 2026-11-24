@file:OptIn(ExperimentalMaterial3Api::class)

package com.nexusknowledge.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nexusknowledge.app.data.Links
import com.nexusknowledge.app.data.Note

private val LEVELS = listOf("No la sé", "Regular", "Bien", "Dominada")

/**
 * Repaso con fichas: el título es la pregunta y el contenido la respuesta.
 * El orden se fija al empezar la sesión (primero lo menos dominado) y no cambia al puntuar.
 */
@Composable
fun ReviewScreen(notes: List<Note>, onRate: (Long, Int) -> Unit) {
    var category by rememberSaveable { mutableStateOf("") }
    var session by rememberSaveable { mutableIntStateOf(0) }
    val candidates = notes.filter { it.category != "Guía" && it.content.isNotBlank() }
    val categories = candidates.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()

    // Ids de la sesión: se calculan al cambiar de categoría o al pulsar «Repetir».
    val deck = remember(category, session) {
        Links.reviewOrder(candidates.filter { category.isEmpty() || it.category == category }).map { it.id }
    }
    var index by remember(category, session) { mutableIntStateOf(0) }
    var revealed by remember(category, session) { mutableStateOf(false) }
    val counts = remember(category, session) { IntArray(4) }

    Scaffold(topBar = { TopAppBar(title = { Text("Repaso") }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                val chips = listOf("Todas" to "") + categories.map { it to it }
                items(chips) { (label, value) ->
                    FilterChip(selected = category == value, onClick = { category = value }, label = { Text(label) })
                }
            }

            // Si se borra una nota durante la sesión, simplemente desaparece del mazo.
            val cards = deck.mapNotNull { id -> notes.firstOrNull { it.id == id } }
            if (cards.isEmpty() && index == 0) {
                Text("No hay fichas en esta categoría. Crea notas en «Mis notas».", Modifier.padding(top = 24.dp))
                return@Column
            }

            if (index >= cards.size) {
                Summary(counts) { session++ }
                return@Column
            }

            val card = cards[index]

            LinearProgressIndicator(
                progress = { index / cards.size.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Ficha ${index + 1} de ${cards.size}  ·  dominio actual: ${LEVELS[card.mastery.coerceIn(0, 3)]}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            Card(Modifier.fillMaxWidth().weight(1f)) {
                Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
                    Text(card.category, style = MaterialTheme.typography.labelMedium)
                    Text(card.title, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(16.dp))
                    if (revealed) {
                        Text(card.content.replace(Regex("""\[\[|]]"""), ""), style = MaterialTheme.typography.bodyLarge)
                    } else {
                        Text(
                            "Piensa la respuesta y luego muéstrala.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            if (!revealed) {
                Button(onClick = { revealed = true }, modifier = Modifier.fillMaxWidth()) { Text("Mostrar respuesta") }
            } else {
                Text("¿Cómo te ha ido?", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                    LEVELS.forEachIndexed { level, label ->
                        OutlinedButton(
                            onClick = {
                                onRate(card.id, level)
                                counts[level]++
                                revealed = false
                                index++
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                        ) { Text(label, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun Summary(counts: IntArray, onRepeat: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Sesión terminada", style = MaterialTheme.typography.headlineSmall)
        LEVELS.forEachIndexed { i, label -> Text("$label: ${counts[i]}") }
        Text(
            "La próxima sesión empezará por las fichas que menos dominas.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onRepeat, modifier = Modifier.fillMaxWidth()) { Text("Repetir") }
    }
}
