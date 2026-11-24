package com.nexusknowledge.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Guarda las notas en un archivo JSON privado de la app (sin conexión, sin cuentas).
 * Para unas cientos de notas es más que suficiente y no necesita base de datos.
 */
class NoteStore(context: Context) {
    private val file = File(context.filesDir, "nexus_notas.json")
    private val _notes = MutableStateFlow(load())
    val notes: StateFlow<List<Note>> = _notes

    fun get(id: Long): Note? = _notes.value.firstOrNull { it.id == id }

    /** Crea (id = 0) o actualiza una nota. Si cambia el título, actualiza los [[enlaces]] de las demás. */
    fun save(note: Note): Note {
        val now = System.currentTimeMillis()
        val current = _notes.value
        val old = current.firstOrNull { it.id == note.id }
        val saved = if (old == null) {
            note.copy(id = (current.maxOfOrNull { it.id } ?: 0L) + 1, createdAt = now, updatedAt = now)
        } else {
            note.copy(createdAt = old.createdAt, updatedAt = now)
        }
        var list = current.filter { it.id != saved.id } + saved
        if (old != null && !old.title.equals(saved.title, ignoreCase = true)) {
            list = list.map { n ->
                val updated = Links.renameLinks(n.content, old.title, saved.title)
                if (updated != n.content) n.copy(content = updated) else n
            }
        }
        commit(list)
        return saved
    }

    fun delete(id: Long) = commit(_notes.value.filter { it.id != id })

    fun toggleFavorite(id: Long) = update(id) { it.copy(favorite = !it.favorite) }

    fun setMastery(id: Long, level: Int) =
        update(id) { it.copy(mastery = level.coerceIn(0, 3), updatedAt = System.currentTimeMillis()) }

    /** Vuelve a cargar los conceptos de ejemplo que falten (no toca las notas propias). */
    fun restoreSamples() {
        val current = _notes.value
        var next = (current.maxOfOrNull { it.id } ?: 0L) + 1
        val missing = SampleNotes.all().filter { s -> Links.findByTitle(s.title, current) == null }
            .map { it.copy(id = next++) }
        commit(current + missing)
    }

    private fun update(id: Long, change: (Note) -> Note) =
        commit(_notes.value.map { if (it.id == id) change(it) else it })

    private fun commit(list: List<Note>) {
        val sorted = list.sortedBy { it.title.lowercase() }
        _notes.value = sorted
        persist(sorted)
    }

    private fun load(): List<Note> {
        if (!file.exists()) {
            val samples = SampleNotes.all().mapIndexed { i, n -> n.copy(id = i + 1L) }
            persist(samples)
            return samples.sortedBy { it.title.lowercase() }
        }
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }.sortedBy { it.title.lowercase() }
        } catch (e: Exception) {
            // Archivo dañado: se guarda una copia y se empieza con los ejemplos.
            file.copyTo(File(file.parentFile, "nexus_notas.danado.json"), overwrite = true)
            SampleNotes.all().mapIndexed { i, n -> n.copy(id = i + 1L) }
        }
    }

    private fun persist(list: List<Note>) {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file)
    }

    private fun toJson(n: Note) = JSONObject().apply {
        put("id", n.id); put("title", n.title); put("content", n.content); put("category", n.category)
        put("tags", JSONArray(n.tags)); put("favorite", n.favorite); put("mastery", n.mastery)
        put("createdAt", n.createdAt); put("updatedAt", n.updatedAt)
    }

    private fun fromJson(o: JSONObject): Note {
        val tags = o.optJSONArray("tags")
        return Note(
            id = o.getLong("id"),
            title = o.optString("title"),
            content = o.optString("content"),
            category = o.optString("category"),
            tags = if (tags == null) emptyList() else (0 until tags.length()).map { tags.getString(it) },
            favorite = o.optBoolean("favorite"),
            mastery = o.optInt("mastery"),
            createdAt = o.optLong("createdAt"),
            updatedAt = o.optLong("updatedAt"),
        )
    }
}
