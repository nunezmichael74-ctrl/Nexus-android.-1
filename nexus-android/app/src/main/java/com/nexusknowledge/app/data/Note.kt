package com.nexusknowledge.app.data

/** Una nota de conocimiento. En el modo Repaso, el título es la pregunta y el contenido la respuesta. */
data class Note(
    val id: Long,
    val title: String,
    val content: String,
    val category: String = "",
    val tags: List<String> = emptyList(),
    val favorite: Boolean = false,
    /** Nivel de dominio en Repaso: 0 = no la sé … 3 = dominada. */
    val mastery: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
