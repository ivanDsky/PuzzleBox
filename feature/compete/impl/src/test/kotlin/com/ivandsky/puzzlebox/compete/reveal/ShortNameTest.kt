package com.ivandsky.puzzlebox.compete.reveal

import org.junit.Assert.assertEquals
import org.junit.Test

class ShortNameTest {

    @Test fun `curly quotes give the nickname`() = assertEquals("ZloyDI", shortName("Ivan “ZloyDI” Dobrovolskyi"))

    @Test fun `straight quotes give the nickname`() = assertEquals("ZloyDI", shortName("Ivan \"ZloyDI\" Dobrovolskyi"))

    @Test fun `a nickname with a space stays whole`() = assertEquals("Zloy DI", shortName("Ivan “Zloy DI” Dobrovolskyi"))

    @Test fun `no nickname gives the first word`() = assertEquals("Alexandrina", shortName("Alexandrina Konstantinopolska"))

    @Test fun `one word stays as it is`() = assertEquals("Sam", shortName("Sam"))

    @Test fun `empty quotes fall back to the first word`() = assertEquals("Ivan", shortName("Ivan \"\" Dobrovolskyi"))

    @Test fun `surrounding space is dropped`() = assertEquals("Ivan", shortName("  Ivan Dobrovolskyi "))

    @Test fun `blank stays blank`() {
        assertEquals("", shortName(""))
        assertEquals("", shortName("   "))
    }
}
