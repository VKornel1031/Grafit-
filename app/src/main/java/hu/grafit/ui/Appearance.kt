package hu.grafit.ui

import org.json.JSONObject

data class Appearance(val mode: String = "system", val color: Int = 0, val dynamic: Boolean = true,
    val compact: Boolean = false, val hideGrades: Boolean = false,
    val showGrades: Boolean = true, val showAbsences: Boolean = true, val radius: Float = 24f, val refreshRate: Int = 120,
    val pillToolbar: Boolean = false, val pillNavigation: Boolean = false, val navigationLabels: Boolean = true,
    val cardStyle: String = "outlined", val textScale: Float = 1f, val fontFamily: String = "sans",
    val pureBlack: Boolean = false, val haptics: Boolean = true,
    val homeMetrics: Boolean = true, val homeGrades: Boolean = true, val homeTasks: Boolean = true,
    val homeTimetable: Boolean = true, val customCss: String = "") {

    fun resolveCss(): Appearance {
        val tokens = ThemeCss.parse(customCss)
        return copy(radius = tokens["radius"]?.removeSuffix("px")?.toFloat() ?: radius,
            textScale = tokens["font-scale"]?.toFloat() ?: textScale, fontFamily = tokens["font-family"] ?: fontFamily,
            pillToolbar = tokens["toolbar"]?.let { it == "pill" } ?: pillToolbar,
            pillNavigation = tokens["navigation"]?.let { it == "pill" } ?: pillNavigation,
            cardStyle = tokens["card-style"] ?: cardStyle)
    }

    fun syncCssControls(previous: Appearance): Appearance {
        if (customCss != previous.customCss) return this
        val tokens = ThemeCss.parse(customCss).toMutableMap()
        fun update(name: String, changed: Boolean, value: String) { if (changed && name in tokens) tokens[name] = value }
        update("radius", radius != previous.radius, "${radius}px")
        update("font-scale", textScale != previous.textScale, textScale.toString())
        update("font-family", fontFamily != previous.fontFamily, fontFamily)
        update("toolbar", pillToolbar != previous.pillToolbar, if (pillToolbar) "pill" else "standard")
        update("navigation", pillNavigation != previous.pillNavigation, if (pillNavigation) "pill" else "standard")
        update("card-style", cardStyle != previous.cardStyle, cardStyle)
        return copy(customCss = ThemeCss.format(tokens))
    }

    fun json(): String = JSONObject().put("mode", mode).put("color", color).put("dynamic", dynamic)
        .put("compact", compact).put("hide", hideGrades).put("grades", showGrades).put("absences", showAbsences)
        .put("radius", radius).put("refreshRate", refreshRate).put("pillToolbar", pillToolbar)
        .put("pillNavigation", pillNavigation).put("navigationLabels", navigationLabels).put("cardStyle", cardStyle)
        .put("textScale", textScale).put("fontFamily", fontFamily).put("pureBlack", pureBlack).put("haptics", haptics)
        .put("homeMetrics", homeMetrics).put("homeGrades", homeGrades).put("homeTasks", homeTasks)
        .put("homeTimetable", homeTimetable).put("customCss", customCss).toString()

    companion object {
        fun parse(j: JSONObject): Appearance = Appearance(
            mode = j.optString("mode", "system").takeIf { it in listOf("system", "light", "dark") } ?: "system",
            color = j.optInt("color", 0).coerceIn(0, 6), dynamic = j.optBoolean("dynamic", true),
            compact = j.optBoolean("compact"), hideGrades = j.optBoolean("hide"),
            showGrades = j.optBoolean("grades", true), showAbsences = j.optBoolean("absences", true),
            radius = j.optDouble("radius", 24.0).takeIf { it.isFinite() }?.toFloat()?.coerceIn(0f, 40f) ?: 24f,
            refreshRate = if (j.optInt("refreshRate", 120) == 60) 60 else 120,
            pillToolbar = j.optBoolean("pillToolbar"), pillNavigation = j.optBoolean("pillNavigation"),
            navigationLabels = j.optBoolean("navigationLabels", true),
            cardStyle = j.optString("cardStyle", "outlined").takeIf { it in listOf("filled", "outlined", "elevated") } ?: "outlined",
            textScale = j.optDouble("textScale", 1.0).takeIf { it.isFinite() }?.toFloat()?.coerceIn(.85f, 1.3f) ?: 1f,
            fontFamily = j.optString("fontFamily", "sans").takeIf { it in listOf("sans", "serif", "mono") } ?: "sans",
            pureBlack = j.optBoolean("pureBlack"), haptics = j.optBoolean("haptics", true),
            homeMetrics = j.optBoolean("homeMetrics", true), homeGrades = j.optBoolean("homeGrades", true),
            homeTasks = j.optBoolean("homeTasks", true), homeTimetable = j.optBoolean("homeTimetable", true),
            customCss = j.optString("customCss", "").takeIf { runCatching { ThemeCss.parse(it) }.isSuccess } ?: "")
    }
}
