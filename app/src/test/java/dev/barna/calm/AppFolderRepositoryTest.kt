package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFolderRepositoryTest {
    private fun repo() = InMemoryAppFolderRepository()

    @Test
    fun emptyRepoHasNoFolders() {
        assertTrue(repo().getAll().isEmpty())
    }

    @Test
    fun savedFolderIsRetrievable() {
        val r = repo()
        val folder = AppFolder(id = "f1", name = "Social", packageNames = setOf("com.a"))
        r.save(folder)
        assertEquals(folder, r.getById("f1"))
    }

    @Test
    fun getAllReturnsSavedFolders() {
        val r = repo()
        val f1 = AppFolder(id = "f1", name = "Social", packageNames = setOf("com.a"))
        val f2 = AppFolder(id = "f2", name = "Work", packageNames = setOf("com.b"))
        r.save(f1)
        r.save(f2)
        assertEquals(setOf(f1, f2), r.getAll().toSet())
    }

    @Test
    fun savingWithSameIdOverwrites() {
        val r = repo()
        r.save(AppFolder(id = "f1", name = "Old", packageNames = setOf("com.a")))
        r.save(AppFolder(id = "f1", name = "New", packageNames = setOf("com.b")))
        assertEquals("New", r.getById("f1")?.name)
        assertEquals(1, r.getAll().size)
    }

    @Test
    fun deletedFolderIsGone() {
        val r = repo()
        r.save(AppFolder(id = "f1", name = "Social", packageNames = setOf("com.a")))
        r.delete("f1")
        assertNull(r.getById("f1"))
        assertTrue(r.getAll().isEmpty())
    }

    @Test
    fun deletingNonExistentIdIsNoOp() {
        val r = repo()
        r.delete("missing")
        assertTrue(r.getAll().isEmpty())
    }

    @Test
    fun getByIdReturnsNullForUnknown() {
        assertNull(repo().getById("unknown"))
    }
}
