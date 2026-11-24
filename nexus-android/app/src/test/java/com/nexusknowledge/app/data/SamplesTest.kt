package com.nexusknowledge.app.data
import org.junit.Assert.assertTrue
import org.junit.Test
class SamplesTest {
    @Test fun todosLosEnlacesDeEjemploExisten() {
        val all = SampleNotes.all()
        val missing = all.flatMap { n -> Links.extract(n.content).filter { Links.findByTitle(it, all) == null }.map { "${n.title} -> $it" } }
        assertTrue(missing.toString(), missing.isEmpty())
        assertTrue(all.map { it.title.lowercase() }.toSet().size == all.size)
    }
}
