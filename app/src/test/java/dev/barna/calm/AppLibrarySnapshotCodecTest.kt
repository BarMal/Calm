package dev.barna.calm

import android.content.ComponentName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
class AppLibrarySnapshotCodecTest {

    // ---- round-trip correctness ----

    @Test
    fun roundTripPersonalApp() {
        val snapshot = snapshotOf(app("com.example.app", "Example App"))

        val decoded = decode(encode(snapshot))

        assertEquals(snapshot.apps, decoded?.apps)
        assertEquals(snapshot.fingerprint, decoded?.fingerprint)
    }

    @Test
    fun roundTripWorkProfileApp() {
        val entry = app(
            packageName = "com.work.app",
            label = "Work App",
            componentName = ComponentName("com.work.app", "com.work.app.Main"),
            profileLabel = "Work",
            isWorkProfile = true,
            identityKey = AppIdentity.launcherKey("com.work.app", "Main", 10),
            notificationSourceKey = AppIdentity.notificationKey("com.work.app", 10),
        )
        val snapshot = snapshotOf(entry)

        val decoded = decode(encode(snapshot))

        assertEquals(snapshot.apps, decoded?.apps)
    }

    @Test
    fun roundTripNullComponentNameDecodesAsNull() {
        val snapshot = snapshotOf(app("com.example", "App", componentName = null))

        val decoded = decode(encode(snapshot))!!

        assertNull(decoded.apps.single().componentName)
    }

    @Test
    fun roundTripEmptyProfileLabel() {
        val snapshot = snapshotOf(app("com.example", "App", profileLabel = ""))

        val decoded = decode(encode(snapshot))!!

        assertEquals("", decoded.apps.single().profileLabel)
    }

    @Test
    fun roundTripLabelWithTabNewlineAndPipe() {
        val label = "App\twith\nnewline|pipe"
        val snapshot = snapshotOf(app("com.example", label))

        val decoded = decode(encode(snapshot))!!

        assertEquals(label, decoded.apps.single().label)
    }

    @Test
    fun roundTripMultipleAppsPreservesOrder() {
        val apps = listOf(app("com.a", "A"), app("com.b", "B"), app("com.c", "C"))
        val snapshot = snapshotOf(*apps.toTypedArray())

        val decoded = decode(encode(snapshot))!!

        assertEquals(apps, decoded.apps)
    }

    @Test
    fun roundTripNegativeHueColor() {
        val snapshot = snapshotOf(app("com.example", "App", hueColor = 0xFF5A00))

        val decoded = decode(encode(snapshot))!!

        assertEquals(0xFF5A00, decoded.apps.single().hueColor)
    }

    // ---- empty / blank input ----

    @Test
    fun decodeEmptyStringReturnsEmptySnapshot() {
        val decoded = decode("")

        assertNotNull(decoded)
        assertEquals(emptyList<AppEntry>(), decoded!!.apps)
    }

    @Test
    fun decodeBlankStringReturnsEmptySnapshot() {
        val decoded = decode("   \n  \n")

        assertNotNull(decoded)
        assertEquals(emptyList<AppEntry>(), decoded!!.apps)
    }

    // ---- corrupt / partial input ----

    @Test
    fun corruptLineIsSkippedAndValidLineDecodes() {
        val valid = encode(snapshotOf(app("com.valid", "Valid")))
        val combined = "$valid\nnot-a-valid-encoded-line"

        val decoded = decode(combined)

        assertNotNull(decoded)
        assertEquals(1, decoded!!.apps.size)
        assertEquals("com.valid", decoded.apps.single().packageName)
    }

    @Test
    fun allCorruptLinesReturnsNull() {
        val decoded = decode("garbage\nalso-garbage")

        assertNull(decoded)
    }

    @Test
    fun lineMissingOneFieldIsSkipped() {
        val validEncoded = encode(snapshotOf(app("com.valid", "Valid")))
        // Trim one tab-separated field to make a line with only 8 fields
        val truncatedLine = validEncoded.substringBeforeLast('\t')
        val combined = "$validEncoded\n$truncatedLine"

        val decoded = decode(combined)

        assertNotNull(decoded)
        assertEquals(1, decoded!!.apps.size)
    }

    // ---- format stability ----

    @Test
    fun encodedLineHasNineTabSeparatedFields() {
        val encoded = encode(snapshotOf(app("com.example", "Example")))

        val fields = encoded.lines().single().split('\t')

        assertEquals(9, fields.size)
    }

    @Test
    fun nonEmptyEncodedFieldsAreValidBase64Url() {
        val encoded = encode(snapshotOf(app("com.example", "Example App")))

        val fields = encoded.lines().single().split('\t')
        fields.filter { it.isNotEmpty() }.forEach { field ->
            // throws if not valid base64
            assertNotNull(Base64.getUrlDecoder().decode(field))
        }
    }

    @Test
    fun multiAppSnapshotHasOneLinePerApp() {
        val apps = listOf(app("com.a", "A"), app("com.b", "B"), app("com.c", "C"))
        val encoded = encode(snapshotOf(*apps.toTypedArray()))

        val nonBlankLines = encoded.lines().filter { it.isNotBlank() }

        assertEquals(3, nonBlankLines.size)
    }

    // ---- fingerprint ----

    @Test
    fun fingerprintDiffersForDifferentApps() {
        val fp1 = AppLibrarySnapshotCodec.fingerprint(listOf(app("com.a", "A")))
        val fp2 = AppLibrarySnapshotCodec.fingerprint(listOf(app("com.b", "B")))

        assertNotEquals(fp1, fp2)
    }

    @Test
    fun fingerprintIsStableForSameInput() {
        val apps = listOf(app("com.a", "A"), app("com.b", "B"))

        assertEquals(
            AppLibrarySnapshotCodec.fingerprint(apps),
            AppLibrarySnapshotCodec.fingerprint(apps),
        )
    }

    @Test
    fun fingerprintEmptyListIsEmpty() {
        assertEquals("", AppLibrarySnapshotCodec.fingerprint(emptyList()))
    }

    // ---- helpers ----

    private fun encode(snapshot: AppLibrarySnapshot) = AppLibrarySnapshotCodec.encode(snapshot)
    private fun decode(s: String) = AppLibrarySnapshotCodec.decode(s)
    private fun snapshotOf(vararg apps: AppEntry) = AppLibrarySnapshot.from(apps.toList())

    private fun app(
        packageName: String,
        label: String,
        hueColor: Int = 0,
        identityKey: String = AppIdentity.packageOnly(packageName).key,
        notificationSourceKey: String = AppIdentity.packageOnly(packageName).notificationSourceKey,
        componentName: ComponentName? = ComponentName(packageName, "MainActivity"),
        profileLabel: String = "Personal",
        isWorkProfile: Boolean = false,
    ) = AppEntry(
        packageName = packageName,
        label = label,
        hueColor = hueColor,
        identityKey = identityKey,
        notificationSourceKey = notificationSourceKey,
        componentName = componentName,
        profileLabel = profileLabel,
        isWorkProfile = isWorkProfile,
    )
}
