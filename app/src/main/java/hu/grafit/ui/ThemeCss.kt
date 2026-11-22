package hu.grafit.ui

/** A deliberately bounded CSS custom-property dialect translated into native Compose tokens. */
object ThemeCss {
    val colors = listOf("primary", "on-primary", "primary-container", "on-primary-container",
        "secondary", "on-secondary", "secondary-container", "on-secondary-container",
        "tertiary", "on-tertiary", "tertiary-container", "on-tertiary-container", "background", "on-background",
        "surface", "on-surface", "surface-variant", "on-surface-variant", "card", "outline", "outline-variant",
        "error", "on-error", "error-container", "on-error-container") + (1..5).map { "grade-$it" }
    private val enums = mapOf("toolbar" to listOf("pill", "standard"), "navigation" to listOf("pill", "standard"),
        "card-style" to listOf("filled", "outlined", "elevated"), "font-family" to listOf("sans", "serif", "mono"))
    val template = ":root {\n  --grafit-primary: #506A53;\n  --grafit-grade-5: #216B4A;\n  --grafit-radius: 24px;\n  --grafit-toolbar: pill;\n}\n"

    fun parse(css: String): Map<String, String> {
        require(css.length <= 16_384) { "A téma legfeljebb 16 384 karakter lehet." }
        val clean = css.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "").trim()
        if (clean.isEmpty()) return emptyMap()
        // Android uses ICU regex, whose brace handling differs from the desktop JVM.
        val opening = clean.indexOf('{')
        require(opening >= 0 && clean.substring(0, opening).trim() == ":root" && clean.endsWith('}')) {
            "A téma egy :root { … } blokk legyen. Csak Grafit CSS-változók használhatók."
        }
        val body = clean.substring(opening + 1, clean.length - 1)
        require('{' !in body && '}' !in body) { "Csak egyetlen :root blokk használható." }
        return buildMap {
            body.split(';').filter { it.isNotBlank() }.forEach { declaration ->
                val pair = declaration.trim().split(':', limit = 2)
                require(pair.size == 2 && pair[0].trim().startsWith("--grafit-")) { "Hibás CSS-változó: ${declaration.trim().take(70)}" }
                val name = pair[0].trim().removePrefix("--grafit-")
                val value = pair[1].trim()
                when (name) {
                    in colors -> require(Regex("#[0-9a-fA-F]{3}([0-9a-fA-F]{3})?").matches(value)) { "$name: #RGB vagy #RRGGBB szín szükséges." }
                    in enums -> require(value in enums.getValue(name)) { "$name: ${enums.getValue(name).joinToString()} használható." }
                    "radius" -> require(value.matches(Regex("[0-9]+(\\.[0-9]+)?px")) && value.removeSuffix("px").toFloatOrNull()?.let { it in 0f..40f } == true) { "radius: 0px–40px használható." }
                    "font-scale" -> require(value.toFloatOrNull()?.let { it in .85f..1.3f } == true) { "font-scale: 0.85–1.30 használható." }
                    else -> throw IllegalArgumentException("Ismeretlen Grafit-változó: $name")
                }
                put(name, value)
            }
        }
    }
    fun format(tokens: Map<String, String>): String = if (tokens.isEmpty()) "" else
        tokens.entries.joinToString("\n", ":root {\n", "\n}") { "  --grafit-${it.key}: ${it.value};" }
    fun replace(css: String, name: String, value: String?): String {
        val tokens = parse(css).toMutableMap()
        if (value == null) tokens.remove(name) else tokens[name] = value
        return format(tokens).also { parse(it) }
    }
}
