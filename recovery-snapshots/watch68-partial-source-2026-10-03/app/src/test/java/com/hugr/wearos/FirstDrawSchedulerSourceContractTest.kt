package com.hugr.wearos

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstDrawSchedulerSourceContractTest {
    @Test
    fun `first draw callback defers listener removal until after draw dispatch`() {
        val candidates = listOf(
            File("src/main/java/com/hugr/wearos/MainActivity.kt"),
            File("app/src/main/java/com/hugr/wearos/MainActivity.kt"),
        )
        val source = candidates.first { it.isFile }.readText()
        val onDrawStart = source.indexOf("override fun onDraw() {")
        val deferredPost = source.indexOf("evidenceText.post {", onDrawStart)
        val deferredRemoval = source.indexOf("removeOnDrawListener(firstDrawListener)", deferredPost)

        require(onDrawStart >= 0)
        require(deferredPost > onDrawStart)
        require(deferredRemoval > deferredPost)

        val drawDispatchBody = source.substring(onDrawStart, deferredPost)

        assertFalse(drawDispatchBody.contains("removeOnDrawListener"))
        assertTrue(drawDispatchBody.contains("FIRST_DRAW_OBSERVED"))
        assertTrue(source.contains("addOnDrawListener(firstDrawListener)"))
    }
}
