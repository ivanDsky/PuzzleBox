package com.ivandsky.puzzlebox.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Numeric MAJOR.MINOR.PATCH parse and order (A3). */
class AppVersionTest {

    private fun v(raw: String) = requireNotNull(AppVersion.parse(raw)) { raw }

    @Test fun parsesPlainCore() = assertEquals(AppVersion(0, 15, 0), v("0.15.0"))

    @Test fun ignoresLeadingV() = assertEquals(AppVersion(0, 15, 0), v("v0.15.0"))

    @Test fun ignoresPreReleaseSuffix() = assertEquals(AppVersion(0, 14, 0), v("0.14.0-slug.3"))

    @Test fun ignoresBuildMetadata() = assertEquals(AppVersion(0, 15, 0), v("0.15.0+build.7"))

    @Test fun suffixedEqualsPlain() = assertEquals(0, v("v1.2.3-rc.1+b.9").compareTo(v("1.2.3")))

    @Test fun majorOrders() {
        assertTrue(v("2.0.0") > v("1.9.9"))
        assertTrue(v("1.9.9") < v("2.0.0"))
    }

    @Test fun minorOrders() {
        assertTrue(v("1.3.0") > v("1.2.9"))
        assertTrue(v("1.2.9") < v("1.3.0"))
    }

    @Test fun patchOrders() {
        assertTrue(v("1.2.4") > v("1.2.3"))
        assertTrue(v("1.2.3") < v("1.2.4"))
    }

    @Test fun equalCompareZero() = assertEquals(0, v("1.2.3").compareTo(v("1.2.3")))

    @Test fun multiDigitIsNumeric() {
        assertTrue(v("0.10.0") > v("0.9.0"))
        assertTrue(v("0.0.10") > v("0.0.9"))
    }

    @Test fun rejectsTwoPart() = assertNull(AppVersion.parse("0.15"))

    @Test fun rejectsFourPart() = assertNull(AppVersion.parse("0.15.0.1"))

    @Test fun rejectsEmpty() {
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse("v"))
    }

    @Test fun rejectsNonNumeric() {
        assertNull(AppVersion.parse("a.b.c"))
        assertNull(AppVersion.parse("1.x.3"))
    }

    @Test fun rejectsNegative() = assertNull(AppVersion.parse("1.-2.3"))
}
