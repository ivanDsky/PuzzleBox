package com.ivandsky.puzzlebox.konsist.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CommentStripperTest {
    private fun assertShape(src: String, out: String) {
        assertEquals(src.length, out.length)
        assertEquals(src.map { it == '\n' }, out.map { it == '\n' })
    }

    @Test fun lineCommentBlanked() {
        val src = "val a = 1 // note\nval b = 2"
        val out = CommentStripper.strip(src)
        assertEquals("val a = 1        \nval b = 2", out)
        assertShape(src, out)
    }

    @Test fun blockCommentBlankedKeepingNewlines() {
        val src = "a /* x\ny */ b"
        val out = CommentStripper.strip(src)
        assertEquals("a     \n     b", out)
        assertShape(src, out)
    }

    @Test fun kdocBlanked() {
        val src = "/** doc @see X */\nclass A"
        assertEquals("                 \nclass A", CommentStripper.strip(src))
    }

    @Test fun nestedBlockCommentBlankedToOuterEnd() {
        val src = "a /* o /* i */ still */ b"
        assertEquals("a " + " ".repeat("/* o /* i */ still */".length) + " b", CommentStripper.strip(src))
    }

    @Test fun stringContentsUntouched() {
        val src = "val s = \"// not /* a comment */\""
        assertEquals(src, CommentStripper.strip(src))
    }

    @Test fun rawStringContentsUntouched() {
        val src = "val s = \"\"\"a // b\n/* c */ \"d\" \"\"\" // real"
        assertEquals("val s = \"\"\"a // b\n/* c */ \"d\" \"\"\"        ", CommentStripper.strip(src))
    }

    @Test fun templateWithStringContainingCommentOpener() {
        val src = "val s = \"\${\"/*\"} x\" // c"
        assertEquals("val s = \"\${\"/*\"} x\"     ", CommentStripper.strip(src))
    }

    @Test fun commentInsideTemplateExpressionBlanked() {
        val src = "\"\${a /* c */}\""
        assertEquals("\"\${a " + " ".repeat("/* c */".length) + "}\"", CommentStripper.strip(src))
    }

    @Test fun charLiteralSlashUntouched() {
        val src = "val c = '/'; val d = '/' // x"
        assertEquals("val c = '/'; val d = '/'     ", CommentStripper.strip(src))
    }

    @Test fun escapedQuoteDoesNotEndString() {
        val src = "val s = \"a\\\" // still string\" // c"
        assertEquals("val s = \"a\\\" // still string\"     ", CommentStripper.strip(src))
    }

    @Test fun escapedQuoteCharLiteral() {
        val src = "val c = '\\''; // c"
        assertEquals("val c = '\\'';     ", CommentStripper.strip(src))
    }

    @Test fun unterminatedBlockBlanksToEndWithoutException() {
        val src = "a /* never\nclosed"
        val out = CommentStripper.strip(src)
        assertEquals("a " + " ".repeat("/* never".length) + "\n" + " ".repeat("closed".length), out)
        assertShape(src, out)
    }

    @Test fun lengthAndLineCountPreserved() {
        val src = "// a\n/* b\n c */\nval s = \"\"\"x\n// y\"\"\"\n"
        val out = CommentStripper.strip(src)
        assertShape(src, out)
        assertEquals(src.lines().size, out.lines().size)
    }

    @Test fun codeBlanksStringAndCharContentsButKeepsTemplates() {
        val src = "val s = \"Dispatchers.IO \${Dispatchers.IO} x\"; val c = '\"' // c"
        val out = CommentStripper.code(src)
        assertEquals("val s = \"               \${Dispatchers.IO}  \"; val c = ' '     ", out)
        assertShape(src, out)
    }

    @Test fun codeBlanksRawStringKeepingClosingQuotes() {
        val src = "val s = \"\"\"\npackage x\n\"\"\"\"\nval t = 1"
        val out = CommentStripper.code(src)
        assertEquals("val s = \"\"\"\n         \n \"\"\"\nval t = 1", out)
        assertShape(src, out)
    }
}
