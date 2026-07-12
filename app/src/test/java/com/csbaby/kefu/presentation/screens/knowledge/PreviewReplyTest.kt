package com.csbaby.kefu.presentation.screens.knowledge

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 列表回复预览截断逻辑测试 (knowledge §3.9)。
 */
class PreviewReplyTest {

    @Test
    fun `empty template returns empty`() {
        assertEquals("", previewReply(""))
        assertEquals("", previewReply("   "))
    }

    @Test
    fun `short template within limit returned as is`() {
        assertEquals("短的回复", previewReply("短的回复", limit = 20))
    }

    @Test
    fun `exactly 20 chars not truncated`() {
        val s = "12345678901234567890"
        assertEquals(s, previewReply(s, limit = 20))
    }

    @Test
    fun `21 chars truncated to 20 plus ellipsis`() {
        val s = "12345678901234567890X"
        assertEquals("12345678901234567890...", previewReply(s, limit = 20))
    }

    @Test
    fun `chinese characters counted as one each`() {
        val s = "一二三四五六七八九十一二三四五六七八九十一"  // 21 chars
        val out = previewReply(s, limit = 20)
        assertEquals("一二三四五六七八九十一二三四五六七八九十一".take(20) + "...", out)
        assertEquals(23, out.length)  // 20 + 3 dots
    }

    @Test
    fun `newlines preserved in preview as-is when under limit`() {
        val s = "第一行\n第二行"
        assertEquals("第一行\n第二行", previewReply(s, limit = 20))
    }

    @Test
    fun `newlines counted as one character each`() {
        val s = "abcdefghijklmnopqrst\n"  // 21 chars
        assertEquals("abcdefghijklmnopqrst...", previewReply(s, limit = 20))
    }

    @Test
    fun `default limit is 20`() {
        val s21 = "01234567890123456789X"
        assertEquals("01234567890123456789...", previewReply(s21))
    }
}
