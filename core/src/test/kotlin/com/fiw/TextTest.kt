package com.fiw

import kotlin.test.Test
import kotlin.test.assertEquals

class TextTest {

	@Test
	fun `codes split text into styled parts`() {
		val parts = Text.parse("&6Gold &lbold&e yellow&rplain")
		assertEquals(listOf("Gold ", "bold", " yellow", "plain"), parts.map { it.text })
		assertEquals(
			listOf(listOf(Format.GOLD), listOf(Format.GOLD, Format.BOLD), listOf(Format.YELLOW), emptyList()),
			parts.map { it.formats },
		)
	}

	@Test
	fun `unknown codes and a trailing ampersand stay as text`() {
		assertEquals("a &z b &", Text.plain("a &z b &"))
		assertEquals("Upper case", Text.plain("&AUpper &Lcase"))
	}
}
