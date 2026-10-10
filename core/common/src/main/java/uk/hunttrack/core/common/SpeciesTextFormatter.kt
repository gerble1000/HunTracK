package uk.hunttrack.core.common

/**
 * Splits field-guide prose into readable paragraphs.
 * Semicolons become sentence breaks; existing sentences are kept separate.
 */
fun formatSpeciesProse(text: String): List<String> {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return emptyList()

    val segments = trimmed.split(";")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    return segments.flatMap { segment -> splitIntoSentences(segment) }
        .map { sentence -> ensureSentencePunctuation(sentence) }
        .filter { it.isNotBlank() }
}

private fun splitIntoSentences(segment: String): List<String> {
    val parts = segment.split(Regex("(?<=[.!?])\\s+"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    return if (parts.isEmpty()) listOf(segment) else parts
}

private fun ensureSentencePunctuation(sentence: String): String {
    val core = sentence.trim().trimEnd('.', '!', '?')
    if (core.isEmpty()) return ""
    val capitalised = core.replaceFirstChar { ch ->
        if (ch.isLowerCase()) ch.titlecase() else ch.toString()
    }
    return "$capitalised."
}
