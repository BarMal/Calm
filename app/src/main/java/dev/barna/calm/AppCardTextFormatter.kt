package dev.barna.calm

class AppCardTextFormatter {
    fun cardText(app: AppEntry): String {
        return buildString {
            append(app.label)
            if (app.isWorkProfile) append("\nWork")
        }
    }
}
