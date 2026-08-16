package com.ytune.app.data

object PlaylistIdParser {
    private val pattern = Regex("(?:[?&]list=|^)([A-Za-z0-9_-]{10,})")
    fun parse(value: String): String? = pattern.find(value.trim())?.groupValues?.getOrNull(1)
        ?.takeIf { it.startsWith("PL") || it.startsWith("OLAK") || value.contains("list=") }
}
