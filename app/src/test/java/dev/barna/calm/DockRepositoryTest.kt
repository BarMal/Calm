package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DockRepositoryTest {
    private fun repo(maxSize: Int = 6) = InMemoryDockRepository(maxSize)

    @Test
    fun emptyRepoHasNoKeys() {
        assertTrue(repo().getKeys().isEmpty())
    }

    @Test
    fun addedKeyIsRetrievable() {
        val r = repo()
        r.add("com.a")
        assertEquals(listOf("com.a"), r.getKeys())
    }

    @Test
    fun addReturnsTrueOnSuccess() {
        assertTrue(repo().add("com.a"))
    }

    @Test
    fun addReturnsFalseWhenFull() {
        val r = repo(maxSize = 2)
        r.add("com.a")
        r.add("com.b")
        assertFalse(r.add("com.c"))
        assertEquals(listOf("com.a", "com.b"), r.getKeys())
    }

    @Test
    fun addReturnsFalseForDuplicate() {
        val r = repo()
        r.add("com.a")
        assertFalse(r.add("com.a"))
        assertEquals(listOf("com.a"), r.getKeys())
    }

    @Test
    fun orderPreservedAcrossMultipleAdds() {
        val r = repo()
        r.add("com.a")
        r.add("com.b")
        r.add("com.c")
        assertEquals(listOf("com.a", "com.b", "com.c"), r.getKeys())
    }

    @Test
    fun removedKeyIsGone() {
        val r = repo()
        r.add("com.a")
        r.add("com.b")
        r.remove("com.a")
        assertEquals(listOf("com.b"), r.getKeys())
    }

    @Test
    fun removingNonExistentKeyIsNoOp() {
        val r = repo()
        r.add("com.a")
        r.remove("com.missing")
        assertEquals(listOf("com.a"), r.getKeys())
    }

    @Test
    fun removedSlotAllowsNewAdd() {
        val r = repo(maxSize = 1)
        r.add("com.a")
        r.remove("com.a")
        assertTrue(r.add("com.b"))
        assertEquals(listOf("com.b"), r.getKeys())
    }

    @Test
    fun containsReturnsTrueForAddedKey() {
        val r = repo()
        r.add("com.a")
        assertTrue(r.contains("com.a"))
    }

    @Test
    fun containsReturnsFalseForAbsentKey() {
        assertFalse(repo().contains("com.a"))
    }

    @Test
    fun moveShiftsItemToNewPosition() {
        val r = repo()
        r.add("com.a")
        r.add("com.b")
        r.add("com.c")
        r.move("com.a", 2)
        assertEquals(listOf("com.b", "com.c", "com.a"), r.getKeys())
    }

    @Test
    fun moveToSameIndexIsNoOp() {
        val r = repo()
        r.add("com.a")
        r.add("com.b")
        r.move("com.b", 1)
        assertEquals(listOf("com.a", "com.b"), r.getKeys())
    }

    @Test
    fun moveToZeroMakesItemFirst() {
        val r = repo()
        r.add("com.a")
        r.add("com.b")
        r.add("com.c")
        r.move("com.c", 0)
        assertEquals(listOf("com.c", "com.a", "com.b"), r.getKeys())
    }

    @Test
    fun moveNonExistentKeyIsNoOp() {
        val r = repo()
        r.add("com.a")
        r.move("com.missing", 0)
        assertEquals(listOf("com.a"), r.getKeys())
    }

    @Test
    fun moveIndexClampedToListBounds() {
        val r = repo()
        r.add("com.a")
        r.add("com.b")
        r.move("com.a", 999)
        assertEquals(listOf("com.b", "com.a"), r.getKeys())
    }
}
