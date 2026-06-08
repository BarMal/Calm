package dev.barna.calm

data class AppFolder(
    val id: String,
    val name: String,
    val packageNames: Set<String>,
) {
    fun contains(packageName: String): Boolean = packageName in packageNames

    fun withPackage(packageName: String): AppFolder =
        copy(packageNames = packageNames + packageName)

    fun withoutPackage(packageName: String): AppFolder =
        copy(packageNames = packageNames - packageName)

    fun renamed(newName: String): AppFolder = copy(name = newName)
}
