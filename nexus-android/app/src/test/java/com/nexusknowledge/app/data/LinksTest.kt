package com.nexusknowledge.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinksTest {
    private fun note(id: Long, title: String, content: String = "", mastery: Int = 0, updated: Long = 0) =
        Note(id = id, title = title, content = content, mastery = mastery, updatedAt = updated)

    @Test fun extraeEnlacesSinRepetir() {
        assertEquals(listOf("Picking", "SGA / WMS"), Links.extract("Ver [[Picking]], [[ SGA / WMS ]] y [[picking]]."))
    }

    @Test fun ignoraCorchetesIncompletos() {
        assertEquals(emptyList<String>(), Links.extract("[[sin cerrar y [simple] y [[]]"))
    }

    @Test fun segmentosAlternanTextoYEnlace() {
        val s = Links.segments("A [[B]] C")
        assertEquals(listOf(Links.Segment.Plain("A "), Links.Segment.Link("B"), Links.Segment.Plain(" C")), s)
    }

    @Test fun backlinksEncuentraQuienEnlaza() {
        val a = note(1, "OTIF")
        val b = note(2, "KPIs", "Incluye [[otif]]")
        val c = note(3, "Otra", "Nada")
        assertEquals(listOf(b), Links.backlinks(a, listOf(a, b, c)))
    }

    @Test fun renombrarActualizaEnlaces() {
        assertEquals("Ver [[Nuevo]] y [[Otro]]", Links.renameLinks("Ver [[viejo]] y [[Otro]]", "Viejo", "Nuevo"))
    }

    @Test fun busquedaIgnoraTildesYMayusculas() {
        val n = note(1, "Análisis ABC").copy(tags = listOf("stock"))
        assertEquals(listOf(n), Links.search("analisis", listOf(n)))
        assertEquals(listOf(n), Links.search("STOCK", listOf(n)))
        assertEquals(emptyList<Note>(), Links.search("cmr", listOf(n)))
    }

    @Test fun repasoEmpiezaPorLoMenosDominado() {
        val a = note(1, "A", mastery = 2)
        val b = note(2, "B", mastery = 0, updated = 5)
        val c = note(3, "C", mastery = 0, updated = 1)
        assertEquals(listOf(c, b, a), Links.reviewOrder(listOf(a, b, c)))
    }

    @Test fun buscarPorTitulo() {
        val n = note(1, "Cabotaje")
        assertEquals(n, Links.findByTitle(" cabotaje ", listOf(n)))
        assertNull(Links.findByTitle("ADR", listOf(n)))
    }

    @Test fun etiquetas() {
        assertEquals(listOf("a", "b", "c"), Links.parseTags("a, b ,, c, A"))
    }
}
