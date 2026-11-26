package jp.ryutancamp.throwwatcher

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 受け取ったキーの記録（画面表示用。このスマホのメモリ内だけ）。 */
object KeyLog {
    data class Entry(val text: String, val segment: String?)

    private const val MAX = 500
    private val entries = ArrayDeque<Entry>()
    private val listeners = mutableListOf<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.JAPAN)

    @Volatile var hits = 0
        private set
    @Volatile var unknown = 0
        private set

    /** サービスから呼ばれる。重い処理はしない（公式アプリへの配信を遅らせないため）。 */
    fun add(e: KeyEvent, foreground: String): String? {
        val shift = e.isShiftPressed
        val modifier = KeyMap.isModifier(e.keyCode)
        val seg = if (modifier) null else KeyMap.segmentOf(e.keyCode, shift)
        val web = KeyMap.webKey(e.keyCode, shift) ?: KeyEvent.keyCodeToString(e.keyCode)
        val label = when {
            modifier -> "（修飾キー）"
            seg != null -> seg
            else -> "？未登録"
        }
        val dev = e.device?.name ?: "不明"
        val line = "${fmt.format(Date())}  $label  [$web]  key=${e.keyCode} scan=${e.scanCode} " +
            "shift=${if (shift) 1 else 0} dev=$dev 前面=$foreground"
        synchronized(this) {
            if (!modifier) { if (seg != null) hits++ else unknown++ }
            entries.addFirst(Entry(line, seg))
            while (entries.size > MAX) entries.removeLast()
        }
        notifyChanged()
        return seg
    }

    fun snapshot(): List<Entry> = synchronized(this) { entries.toList() }

    fun clear() {
        synchronized(this) { entries.clear(); hits = 0; unknown = 0 }
        notifyChanged()
    }

    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }

    fun notifyChanged() {
        main.post { listeners.toList().forEach { it() } }
    }
}
