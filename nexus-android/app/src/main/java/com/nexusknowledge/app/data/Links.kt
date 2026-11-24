package com.nexusknowledge.app.data

/**
 * Lógica pura (sin Android) de enlaces, búsqueda y repaso. Se prueba con tests unitarios.
 * Un enlace se escribe como [[Título de otra nota]].
 */
object Links {
    private val LINK = Regex("""\[\[([^\[\]\n]+)]]""")

    /** Títulos enlazados en un texto, sin repetir y respetando el orden de aparición. */
    fun extract(text: String): List<String> =
        LINK.findAll(text).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }.toList()

    /** Trozos del texto: texto normal o enlace, para pintarlos en pantalla. */
    sealed interface Segment {
        data class Plain(val text: String) : Segment
        data class Link(val title: String) : Segment
    }

    fun segments(text: String): List<Segment> {
        val out = mutableListOf<Segment>()
        var last = 0
        for (m in LINK.findAll(text)) {
            if (m.range.first > last) out += Segment.Plain(text.substring(last, m.range.first))
            out += Segment.Link(m.groupValues[1].trim())
            last = m.range.last + 1
        }
        if (last < text.length) out += Segment.Plain(text.substring(last))
        return out
    }

    fun findByTitle(title: String, notes: List<Note>): Note? =
        notes.firstOrNull { it.title.equals(title.trim(), ignoreCase = true) }

    /** Notas que enlazan a [target] («Mencionada en»). */
    fun backlinks(target: Note, notes: List<Note>): List<Note> =
        notes.filter { n -> n.id != target.id && extract(n.content).any { it.equals(target.title, ignoreCase = true) } }

    /** Cambia [[viejo]] por [[nuevo]] en un texto (al renombrar una nota). */
    fun renameLinks(text: String, oldTitle: String, newTitle: String): String =
        LINK.replace(text) { m ->
            if (m.groupValues[1].trim().equals(oldTitle.trim(), ignoreCase = true)) "[[${newTitle.trim()}]]" else m.value
        }

    /** Búsqueda sin distinguir mayúsculas ni tildes en título, contenido, categoría y etiquetas. */
    fun search(query: String, notes: List<Note>): List<Note> {
        val q = normalize(query.trim())
        if (q.isEmpty()) return notes
        return notes.filter { n ->
            normalize(n.title).contains(q) || normalize(n.content).contains(q) ||
                normalize(n.category).contains(q) || n.tags.any { normalize(it).contains(q) }
        }
    }

    /** Orden de repaso: primero lo menos dominado y, a igualdad, lo que hace más tiempo que no se toca. */
    fun reviewOrder(notes: List<Note>): List<Note> =
        notes.sortedWith(compareBy<Note> { it.mastery }.thenBy { it.updatedAt }.thenBy { it.id })

    /** "a, b ,, c" → [a, b, c] */
    fun parseTags(text: String): List<String> =
        text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }

    fun normalize(s: String): String =
        java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
}
