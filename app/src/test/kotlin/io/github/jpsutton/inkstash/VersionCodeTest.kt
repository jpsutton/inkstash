package io.github.jpsutton.inkstash

import org.junit.Assert.assertEquals
import org.junit.Test

/** The versionCode rule from app/build.gradle.kts, which scripts/ci/version.sh repeats. */
class VersionCodeTest {
    private fun code(name: String): Int {
        val (major, minor, patch) = name.substringBefore('-').split('.').map { it.toInt() }
        return major * 10000 + minor * 100 + patch
    }

    @Test fun math() {
        assertEquals(100, code("0.1.0"))
        assertEquals(10203, code("1.2.3"))
        assertEquals(20000, code("2.0.0"))
        assertEquals(101, code("0.1.1-beta"))
    }

    @Test fun buildFileHasTheSameRule() {
        val gradle = java.io.File("build.gradle.kts").readText()
        assert(gradle.contains("major * 10000 + minor * 100 + patch"))
    }
}
