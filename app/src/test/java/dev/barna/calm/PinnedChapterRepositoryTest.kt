package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinnedChapterRepositoryTest {
    private fun repo() = InMemoryPinnedChapterRepository()

    @Test
    fun emptyRepoReturnsEmptySet() {
        assertTrue(repo().getPinnedPackages().isEmpty())
    }

    @Test
    fun pinnedPackageIsRetrievable() {
        val r = repo()
        r.pin("com.whatsapp")
        assertTrue(r.getPinnedPackages().contains("com.whatsapp"))
    }

    @Test
    fun isPinnedReturnsTrueAfterPin() {
        val r = repo()
        r.pin("com.whatsapp")
        assertTrue(r.isPinned("com.whatsapp"))
    }

    @Test
    fun isPinnedReturnsFalseForAbsentPackage() {
        assertFalse(repo().isPinned("com.whatsapp"))
    }

    @Test
    fun unpinRemovesPackage() {
        val r = repo()
        r.pin("com.whatsapp")
        r.unpin("com.whatsapp")
        assertFalse(r.isPinned("com.whatsapp"))
        assertTrue(r.getPinnedPackages().isEmpty())
    }

    @Test
    fun unpinNonPinnedPackageIsNoOp() {
        val r = repo()
        r.unpin("com.missing")
        assertTrue(r.getPinnedPackages().isEmpty())
    }

    @Test
    fun pinPinUnpinLeavesOtherPackagesIntact() {
        val r = repo()
        r.pin("com.a")
        r.pin("com.b")
        r.unpin("com.a")
        assertEquals(setOf("com.b"), r.getPinnedPackages())
    }

    @Test
    fun pinSamePackageTwiceIsIdempotent() {
        val r = repo()
        r.pin("com.a")
        r.pin("com.a")
        assertEquals(setOf("com.a"), r.getPinnedPackages())
    }

    @Test
    fun multiplePinnedPackagesAllPresent() {
        val r = repo()
        r.pin("com.gmail")
        r.pin("com.outlook")
        r.pin("com.calendar")
        assertEquals(setOf("com.gmail", "com.outlook", "com.calendar"), r.getPinnedPackages())
    }
}
