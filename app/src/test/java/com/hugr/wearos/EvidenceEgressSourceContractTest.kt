package com.hugr.wearos

import java.io.File
import java.security.MessageDigest
import java.util.jar.JarFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Temporary source-recovery boundary.
 *
 * Editable Evidence Egress source was not recovered. The candidate retains the
 * exact compiled Watch68 egress class set as a hash-pinned binary payload; this
 * test deliberately makes no semantic claim beyond that payload identity and
 * the explicit separation of the ordinary path from egress activation.
 */
class EvidenceEgressSourceContractTest {
    private val manifest by lazy {
        listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml"))
            .first { it.isFile }.readText()
    }
    private val gatt by lazy { sourceAt("BleGattService.kt") }

    @Test
    fun `Watch68 egress compatibility classes are exact recovered binary payloads`() {
        val jar = existing("libs/egress-compat-watch68.jar")
        val dex = existing("egress-compat-classes2.dex")
        assertEquals("9ebe368e89618128ece8e315b7db0e8ad4d63e6603db59801d36f4ee7f6d873b", sha256(jar))
        assertEquals("a2477754c9e4b10a9d91584ca2281b4190a9b0776411fec19970dd47f96ab784", sha256(dex))
        JarFile(jar).use { payload ->
            val classes = payload.entries().asSequence()
                .map { it.name }
                .filter { it.startsWith("com/hugr/wearos/EvidenceEgress") && it.endsWith(".class") }
                .toList()
            assertEquals(76, classes.size)
            listOf(
                "com/hugr/wearos/EvidenceEgressActivity.class",
                "com/hugr/wearos/EvidenceEgressActivation.class",
                "com/hugr/wearos/EvidenceEgressContract.class",
                "com/hugr/wearos/EvidenceEgressGattStartPolicy.class",
            ).forEach { required -> assertTrue("missing recovered egress class $required", required in classes) }
        }
    }

    @Test
    fun `egress remains a manifest-visible explicit component and ordinary launch does not activate it`() {
        assertTrue(manifest.contains("android:name=\".EvidenceEgressActivity\""))
        val main = sourceAt("MainActivity.kt")
        val health = sourceAt("HealthSensorService.kt")
        assertFalse(main.contains("ACTION_START_EGRESS_ONLY"))
        assertFalse(main.contains("EvidenceEgressActivation"))
        assertFalse(health.contains("ACTION_START_EGRESS_ONLY"))
        assertTrue(gatt.contains("EvidenceEgressGattRuntimeMode.EGRESS_ONLY"))
        assertTrue(gatt.contains("EvidenceEgressGattStartPolicy.requestedMode"))
    }

    @Test
    fun `temporary candidate declares unavailable readback launcher rather than fabricating it`() {
        assertFalse(manifest.contains(".ExactRangeReadbackActivity"))
        assertFalse(sourceFileOrNull("ExactRangeReadback.kt")?.isFile == true)
    }

    private fun existing(path: String): File = listOf(File(path), File("app/$path"))
        .firstOrNull { it.isFile } ?: error("missing compatibility payload: $path")

    private fun sourceAt(name: String): String = sourceFileOrNull(name)?.readText() ?: error("missing source: $name")

    private fun sourceFileOrNull(name: String): File? = listOf(
        File("src/main/java/com/hugr/wearos/$name"),
        File("app/src/main/java/com/hugr/wearos/$name"),
    ).firstOrNull { it.isFile }

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes()).joinToString("") { "%02x".format(it) }
}
