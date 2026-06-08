package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFolderTest {
    @Test
    fun folderContainsPackage() {
        val folder = AppFolder(id = "f1", name = "Social", packageNames = setOf("com.a", "com.b"))
        assertTrue(folder.contains("com.a"))
        assertTrue(folder.contains("com.b"))
        assertFalse(folder.contains("com.c"))
    }

    @Test
    fun emptyFolderContainsNothing() {
        val folder = AppFolder(id = "f1", name = "Empty", packageNames = emptySet())
        assertFalse(folder.contains("com.a"))
    }

    @Test
    fun folderEquality() {
        val a = AppFolder(id = "f1", name = "Social", packageNames = setOf("com.a"))
        val b = AppFolder(id = "f1", name = "Social", packageNames = setOf("com.a"))
        assertEquals(a, b)
    }

    @Test
    fun folderWithAddedPackage() {
        val original = AppFolder(id = "f1", name = "Social", packageNames = setOf("com.a"))
        val updated = original.withPackage("com.b")
        assertTrue(updated.contains("com.a"))
        assertTrue(updated.contains("com.b"))
        assertFalse(original.contains("com.b"))
    }

    @Test
    fun folderWithRemovedPackage() {
        val original = AppFolder(id = "f1", name = "Social", packageNames = setOf("com.a", "com.b"))
        val updated = original.withoutPackage("com.a")
        assertFalse(updated.contains("com.a"))
        assertTrue(updated.contains("com.b"))
        assertTrue(original.contains("com.a"))
    }

    @Test
    fun folderRenamedPreservesPackages() {
        val original = AppFolder(id = "f1", name = "Old", packageNames = setOf("com.a"))
        val renamed = original.renamed("New")
        assertEquals("New", renamed.name)
        assertEquals("f1", renamed.id)
        assertTrue(renamed.contains("com.a"))
    }
}
