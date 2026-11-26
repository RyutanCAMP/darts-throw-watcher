package jp.ryutancamp.throwwatcher

import android.view.KeyEvent

/**
 * DARTSLIVE-200S のキー割当（darts-training-lab の data/200s-keymap.json と同じ内容）。
 * PC では「コード | US配列の文字」で記録しているので、Android でも
 * キー番号と Shift の有無から同じ形の文字列を作って照合する（端末の配列設定の影響を受けない）。
 */
object KeyMap {
    private val SEGMENTS: Map<String, String> = mapOf(
        "S20O" to "KeyA | A", "S20I" to "KeyW | W", "T20" to "Digit7 | &", "D20" to "KeyY | Y",
        "S1O" to "KeyQ | Q", "S1I" to "KeyH | h", "T1" to "Minus | -", "D1" to "KeyJ | J",
        "S18O" to "KeyZ | Z", "S18I" to "KeyR | r", "T18" to "Quote | \"", "D18" to "KeyT | t",
        "S4O" to "Slash | /", "S4I" to "KeyM | M", "T4" to "Digit2 | @", "D4" to "KeyK | K",
        "S13O" to "Digit2 | 2", "S13I" to "Digit5 | %", "T13" to "KeyQ | q", "D13" to "Digit6 | ^",
        "S6O" to "Period | .", "S6I" to "KeyU | U", "T6" to "Digit4 | 4", "D6" to "KeyV | V",
        "S10O" to "Equal | =", "S10I" to "KeyM | m", "T10" to "KeyA | a", "D10" to "KeyL | l",
        "S15O" to "KeyH | H", "S15I" to "Digit4 | $", "T15" to "KeyE | e", "D15" to "BracketLeft | [",
        "S2O" to "KeyR | R", "S2I" to "Comma | <", "T2" to "KeyD | d", "D2" to "Digit9 | 9",
        "S17O" to "KeyI | I", "S17I" to "KeyF | f", "T17" to "Digit1 | 1", "D17" to "Comma | ,",
        "S3O" to "KeyD | D", "S3I" to "KeyI | i", "T3" to "Semicolon | ;", "D3" to "KeyS | S",
        "S19O" to "Digit8 | 8", "S19I" to "KeyC | c", "T19" to "Equal | +", "D19" to "KeyB | B",
        "S7O" to "KeyO | O", "S7I" to "KeyN | N", "T7" to "Slash | ?", "D7" to "Digit3 | 3",
        "S16O" to "Digit3 | #", "S16I" to "Minus | _", "T16" to "Period | >", "D16" to "KeyB | b",
        "S8O" to "KeyP | p", "S8I" to "KeyC | C", "T8" to "Digit8 | *", "D8" to "KeyK | k",
        "S11O" to "Backslash | \\", "S11I" to "Digit5 | 5", "T11" to "Digit7 | 7", "D11" to "KeyT | T",
        "S14O" to "KeyL | L", "S14I" to "Digit0 | 0", "T14" to "Digit6 | 6", "D14" to "KeyS | s",
        "S9O" to "KeyG | g", "S9I" to "KeyF | F", "T9" to "KeyE | E", "D9" to "Semicolon | :",
        "S12O" to "Quote | '", "S12I" to "KeyN | n", "T12" to "KeyP | P", "D12" to "KeyX | X",
        "S5O" to "Digit9 | (", "S5I" to "BracketRight | ]", "T5" to "KeyJ | j", "D5" to "Digit1 | !",
        "OB" to "KeyG | G", "IB" to "KeyO | o"
    )

    /** "KeyA | A" → "S20O" */
    private val BY_KEY: Map<String, String> = SEGMENTS.entries.associate { (seg, key) -> key to seg }

    private val SHIFTED_DIGITS = ")!@#$%^&*("

    private val PUNCT: Map<Int, Triple<String, String, String>> = mapOf(
        KeyEvent.KEYCODE_MINUS to Triple("Minus", "-", "_"),
        KeyEvent.KEYCODE_EQUALS to Triple("Equal", "=", "+"),
        KeyEvent.KEYCODE_LEFT_BRACKET to Triple("BracketLeft", "[", "{"),
        KeyEvent.KEYCODE_RIGHT_BRACKET to Triple("BracketRight", "]", "}"),
        KeyEvent.KEYCODE_BACKSLASH to Triple("Backslash", "\\", "|"),
        KeyEvent.KEYCODE_SEMICOLON to Triple("Semicolon", ";", ":"),
        KeyEvent.KEYCODE_APOSTROPHE to Triple("Quote", "'", "\""),
        KeyEvent.KEYCODE_COMMA to Triple("Comma", ",", "<"),
        KeyEvent.KEYCODE_PERIOD to Triple("Period", ".", ">"),
        KeyEvent.KEYCODE_SLASH to Triple("Slash", "/", "?"),
        KeyEvent.KEYCODE_GRAVE to Triple("Backquote", "`", "~")
    )

    /** キー番号と Shift から「コード | US配列の文字」を作る。対象外のキーは null。 */
    fun webKey(keyCode: Int, shift: Boolean): String? {
        if (keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) {
            val c = 'a' + (keyCode - KeyEvent.KEYCODE_A)
            val ch = if (shift) c.uppercaseChar() else c
            return "Key${c.uppercaseChar()} | $ch"
        }
        if (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
            val d = keyCode - KeyEvent.KEYCODE_0
            val ch = if (shift) SHIFTED_DIGITS[d].toString() else d.toString()
            return "Digit$d | $ch"
        }
        PUNCT[keyCode]?.let { (code, plain, shifted) ->
            return "$code | ${if (shift) shifted else plain}"
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_TAB -> "Tab"
            KeyEvent.KEYCODE_ENTER -> "Enter"
            else -> null
        }
    }

    /** 区画名（T20 など）。START=Tab、CHANGE=Enter は PC 版アプリと同じ扱い。 */
    fun segmentOf(keyCode: Int, shift: Boolean): String? {
        val k = webKey(keyCode, shift) ?: return null
        return when (k) {
            "Tab" -> "START"
            "Enter" -> "CHANGE"
            else -> BY_KEY[k]
        }
    }

    fun isModifier(keyCode: Int): Boolean = KeyEvent.isModifierKey(keyCode)
}
