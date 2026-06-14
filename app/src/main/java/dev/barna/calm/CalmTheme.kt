package dev.barna.calm

object CalmTheme {
    const val OVERVIEW_KEY = "dev.barna.calm.OVERVIEW"
    const val WORK_OVERVIEW_KEY = "dev.barna.calm.OVERVIEW.WORK"
    const val APP_LIBRARY_KEY = "dev.barna.calm.APPS"
    const val PERSONAL_APP_LIBRARY_KEY = "dev.barna.calm.APPS.PERSONAL"
    const val WORK_APP_LIBRARY_KEY = "dev.barna.calm.APPS.WORK"
    const val PINNED_KEY = "dev.barna.calm.PINNED"
    const val CATEGORY_FOLDER_KEY = "dev.barna.calm.CATEGORIES"
    const val CATEGORY_PAGE_KEY_PREFIX = "dev.barna.calm.CATEGORY."
    const val CONTACTS_KEY = "dev.barna.calm.CONTACTS"
    const val AGENDA_KEY = "dev.barna.calm.AGENDA"
    const val ALARMS_KEY = "dev.barna.calm.ALARMS"
    const val RSS_KEY = "dev.barna.calm.RSS"

    val INK: Int = rgb(236, 232, 222)
    val MUTED_INK: Int = rgb(166, 161, 151)
    val GLASS: Int = argb(82, 15, 15, 20)
    val QUIET_GLASS: Int = argb(40, 31, 30, 38)
    val STROKE: Int = argb(30, 255, 246, 226)
    val GLOSS: Int = argb(18, 255, 252, 240)
    val SHADOW: Int = argb(12, 0, 0, 0)
    val SHADE_TOP: Int = argb(118, 5, 5, 10)
    val SHADE_MID: Int = argb(16, 190, 168, 128)
    val SHADE_BOTTOM: Int = argb(140, 2, 2, 6)
    val ACCENT: Int = rgb(198, 181, 151)
    val REFRACTION_BLUE: Int = argb(14, 116, 145, 210)
    val REFRACTION_LILAC: Int = argb(12, 220, 198, 172)
    val SURFACE: Int = rgb(12, 11, 16)
    val SURFACE_CONTAINER: Int = rgb(28, 26, 34)

    private fun rgb(r: Int, g: Int, b: Int): Int = (0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
    private fun argb(a: Int, r: Int, g: Int, b: Int): Int = (a shl 24) or (r shl 16) or (g shl 8) or b
}
