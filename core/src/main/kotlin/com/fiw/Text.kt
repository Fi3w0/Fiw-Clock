package com.fiw

/**
 * Minecraft formatting codes. Names match `net.minecraft.ChatFormatting`, so the platform
 * side can map them with `ChatFormatting.valueOf(format.name)`.
 */
enum class Format(val code: Char, val isColor: Boolean) {
	BLACK('0', true),
	DARK_BLUE('1', true),
	DARK_GREEN('2', true),
	DARK_AQUA('3', true),
	DARK_RED('4', true),
	DARK_PURPLE('5', true),
	GOLD('6', true),
	GRAY('7', true),
	DARK_GRAY('8', true),
	BLUE('9', true),
	GREEN('a', true),
	AQUA('b', true),
	RED('c', true),
	LIGHT_PURPLE('d', true),
	YELLOW('e', true),
	WHITE('f', true),
	OBFUSCATED('k', false),
	BOLD('l', false),
	STRIKETHROUGH('m', false),
	UNDERLINE('n', false),
	ITALIC('o', false),
}

/** A run of text with one style. */
class TextPart(val text: String, val formats: List<Format>)

/**
 * Chat text written with `&` codes, e.g. `&6Notch &ereached &610 &ekills!`, the same
 * convention most server plugins use. A color resets bold/italic/etc., `&r` resets everything.
 * Kept Minecraft-free so messages (config templates, command replies) can be unit-tested.
 */
object Text {
	private const val CODE = '&'
	private const val RESET = 'r'

	@JvmStatic
	fun parse(text: String): List<TextPart> {
		val parts = ArrayList<TextPart>()
		val current = StringBuilder()
		var color: Format? = null
		val decorations = LinkedHashSet<Format>()

		fun flush() {
			if (current.isEmpty()) return
			parts.add(TextPart(current.toString(), listOfNotNull(color) + decorations))
			current.setLength(0)
		}

		var i = 0
		while (i < text.length) {
			val c = text[i]
			val next = if (i + 1 < text.length) text[i + 1].lowercaseChar() else null
			val format = next?.let { code -> Format.entries.firstOrNull { it.code == code } }
			if (c == CODE && (format != null || next == RESET)) {
				flush()
				when {
					next == RESET -> {
						color = null
						decorations.clear()
					}
					format!!.isColor -> {
						color = format
						decorations.clear()
					}
					else -> decorations.add(format)
				}
				i += 2
				continue
			}
			current.append(c)
			i++
		}
		flush()
		return parts
	}

	/** [text] without formatting codes, e.g. for the server log. */
	@JvmStatic
	fun plain(text: String): String = parse(text).joinToString("") { it.text }
}
