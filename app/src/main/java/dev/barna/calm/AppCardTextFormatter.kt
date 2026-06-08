package dev.barna.calm

class AppCardTextFormatter {
    fun cardText(app: AppEntry): String {
        return buildString {
            if (app.isWorkProfile) append("💼 ")
            append(app.label)
        }
    }
}
