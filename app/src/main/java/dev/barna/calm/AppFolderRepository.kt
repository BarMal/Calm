package dev.barna.calm

interface AppFolderRepository {
    fun getAll(): List<AppFolder>
    fun getById(id: String): AppFolder?
    fun save(folder: AppFolder)
    fun delete(id: String)
}

class InMemoryAppFolderRepository : AppFolderRepository {
    private val folders = LinkedHashMap<String, AppFolder>()

    override fun getAll(): List<AppFolder> = folders.values.toList()

    override fun getById(id: String): AppFolder? = folders[id]

    override fun save(folder: AppFolder) {
        folders[folder.id] = folder
    }

    override fun delete(id: String) {
        folders.remove(id)
    }
}
