package jp.ryutancamp.throwwatcher

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.HandlerThread
import java.io.File
import java.util.UUID

/**
 * 拾った投擲を PC（darts-training-lab）へ送る係。
 * - キー受信側（ユーザー補助）は enqueue() で時刻と区画を渡すだけ。通信は専用スレッドで行うので入力は遅れない。
 * - 送れなかった分はファイルに残し、通信が戻ったら古い順に再送する（docId 固定なので二重登録にならない）。
 */
object Sender {
    data class Item(val id: String, val seg: String, val t: Long, val n: Long)

    enum class LinkState { NONE, CHECKING, OK, NOT_FOUND, ERROR }

    private const val PREFS = "sender"
    private const val MAX_QUEUE = 5000
    private val thread = HandlerThread("sender").apply { start() }
    private val worker = Handler(thread.looper)
    private val queue = ArrayDeque<Item>()
    private var prefs: SharedPreferences? = null
    private var queueFile: File? = null
    private var rest = FirestoreRest()
    private var retryMs = 0L
    private var running = false

    @Volatile var code: String = ""
        private set
    @Volatile var enabled: Boolean = true
        private set
    @Volatile var linkState: LinkState = LinkState.NONE
        private set
    @Volatile var sent = 0
        private set
    @Volatile var lastError: String = ""
        private set
    @Volatile var lastSentSeg: String = ""
        private set
    val pending: Int get() = synchronized(queue) { queue.size }

    /** アプリ／サービスの起動時に呼ぶ（何度呼んでもよい）。 */
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        code = p.getString("code", "") ?: ""
        enabled = p.getBoolean("enabled", true)
        if (code.isNotEmpty()) linkState = LinkState.OK
        if (!p.contains("device")) p.edit().putString("device", UUID.randomUUID().toString().take(6)).apply()
        queueFile = File(context.applicationContext.filesDir, "send-queue.txt")
        worker.post {
            loadQueue()
            kick()
        }
    }

    /** ユーザー補助サービスから呼ばれる。ここでは何も待たない。 */
    fun enqueue(seg: String, t: Long) {
        if (code.isEmpty() || !enabled) return
        worker.post {
            val p = prefs ?: return@post
            val n = p.getLong("seq", 0L) + 1
            p.edit().putLong("seq", n).apply()
            val item = Item("${t}_${n}_${p.getString("device", "x")}", seg, t, n)
            synchronized(queue) {
                queue.addLast(item)
                while (queue.size > MAX_QUEUE) queue.removeFirst()
            }
            saveQueue()
            KeyLog.notifyChanged()
            kick()
        }
    }

    /** 連携コードを確認して保存する。結果は linkState に入る。 */
    fun link(input: String) {
        val c = FirestoreRest.normalizeCode(input)
        if (c == null) {
            linkState = LinkState.NOT_FOUND
            lastError = "コードの形が違います（8文字）"
            KeyLog.notifyChanged()
            return
        }
        linkState = LinkState.CHECKING
        KeyLog.notifyChanged()
        worker.post {
            when (rest.checkLink(c)) {
                FirestoreRest.Outcome.OK -> {
                    if (c != code) sent = 0
                    code = c
                    prefs?.edit()?.putString("code", c)?.apply()
                    linkState = LinkState.OK
                    lastError = ""
                    retryNow()   // 未送信があれば、新しいコード宛てにすぐ送る
                }
                FirestoreRest.Outcome.NOT_FOUND -> {
                    linkState = LinkState.NOT_FOUND; lastError = "そのコードは見つかりません（PCで発行したコードを確認）"
                }
                FirestoreRest.Outcome.REJECTED -> {
                    linkState = LinkState.ERROR; lastError = "サーバーに断られました（Firestoreのルール未更新の可能性）"
                }
                FirestoreRest.Outcome.RETRY -> {
                    linkState = if (code.isNotEmpty()) LinkState.OK else LinkState.ERROR
                    lastError = "通信できません（ネット接続を確認）"
                }
            }
            KeyLog.notifyChanged()
        }
    }

    fun unlink() {
        worker.post {
            code = ""
            linkState = LinkState.NONE
            lastError = ""
            prefs?.edit()?.remove("code")?.apply()
            synchronized(queue) { queue.clear() }
            saveQueue()
            KeyLog.notifyChanged()
        }
    }

    fun setEnabled(on: Boolean) {
        enabled = on
        prefs?.edit()?.putBoolean("enabled", on)?.apply()
        if (on) worker.post { retryNow() }
        KeyLog.notifyChanged()
    }

    /** テスト用：送り先を差し替える。 */
    internal fun useRest(r: FirestoreRest) { rest = r }

    // ---- ここから下は worker スレッドだけで動く ----

    private fun kick() {
        if (running) return
        running = true
        worker.post { drain() }
    }

    private fun drain() {
        while (true) {
            val c = code
            if (c.isEmpty() || !enabled) { running = false; return }
            val item = synchronized(queue) { queue.firstOrNull() } ?: run { running = false; retryMs = 0; return }
            when (rest.postThrow(c, item.id, item.seg, item.t, item.n)) {
                FirestoreRest.Outcome.OK -> {
                    synchronized(queue) { if (queue.firstOrNull() == item) queue.removeFirst() }
                    sent++
                    lastSentSeg = item.seg
                    lastError = ""
                    retryMs = 0
                    saveQueue()
                    KeyLog.notifyChanged()
                }
                FirestoreRest.Outcome.REJECTED -> {
                    // 区画は決まった形しか送らないので、断られる＝ルール未更新など。捨てずに待って再送する
                    lastError = "送信を断られました（連携コードの期限30日切れ、またはFirestoreのルール未更新）→ PCで新しいコードを発行して入力し直し"
                    retryLater(60_000)
                    return
                }
                FirestoreRest.Outcome.NOT_FOUND -> {
                    lastError = "連携コードがPC側で見つかりません（PCで新しいコードにした場合は入力し直し）"
                    linkState = LinkState.NOT_FOUND
                    retryLater(60_000)
                    return
                }
                FirestoreRest.Outcome.RETRY -> {
                    lastError = "通信待ち（自動で再送します）"
                    retryLater(if (retryMs == 0L) 2_000 else minOf(retryMs * 2, 30_000))
                    return
                }
            }
        }
    }

    private val retryTask = Runnable { running = false; kick() }

    private fun retryLater(ms: Long) {
        retryMs = ms
        KeyLog.notifyChanged()
        worker.postDelayed(retryTask, ms)
    }

    /** 再送待ちを取り消して、すぐに送り直す。 */
    private fun retryNow() {
        worker.removeCallbacks(retryTask)
        running = false
        retryMs = 0
        kick()
    }

    private fun loadQueue() {
        val f = queueFile ?: return
        if (!f.exists()) return
        val items = f.readLines().mapNotNull { line ->
            val p = line.split('\t')
            if (p.size == 4) Item(p[0], p[1], p[2].toLongOrNull() ?: return@mapNotNull null, p[3].toLongOrNull() ?: 0) else null
        }
        synchronized(queue) { queue.clear(); queue.addAll(items) }
    }

    private fun saveQueue() {
        val f = queueFile ?: return
        val text = synchronized(queue) { queue.joinToString("\n") { "${it.id}\t${it.seg}\t${it.t}\t${it.n}" } }
        try {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(text)
            tmp.renameTo(f)
        } catch (_: Exception) {
            // 保存に失敗しても送信は続ける
        }
    }
}
