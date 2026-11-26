package jp.ryutancamp.throwwatcher

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var counts: TextView
    private lateinit var last: TextView
    private lateinit var log: TextView
    private val listener: () -> Unit = { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        fun label(size: Float, bold: Boolean = false) = TextView(this).apply {
            textSize = size
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, pad / 4, 0, pad / 4)
        }
        root.addView(label(20f, true).apply { text = "投擲ウォッチャー（テスト版）" })
        root.addView(label(14f).apply {
            text = "① 下のボタンでユーザー補助を開き「投擲ウォッチャー」をON\n" +
                "② 公式アプリでいつも通りプレイ\n" +
                "③ この画面に戻って、刺さった場所が記録されているか確認"
        })
        status = label(16f, true)
        root.addView(status)
        root.addView(Button(this).apply {
            text = "ユーザー補助の設定を開く"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(Button(this).apply {
            text = "記録をクリア"
            setOnClickListener { KeyLog.clear() }
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        row.addView(Button(this).apply {
            text = "記録を共有"
            setOnClickListener { share() }
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        root.addView(row)
        last = label(40f, true).apply { setTextColor(Color.rgb(0x15, 0x60, 0x82)) }
        root.addView(last)
        counts = label(14f)
        root.addView(counts)
        log = label(12f).apply { typeface = Typeface.MONOSPACE }
        val scroll = ScrollView(this).apply { addView(log) }
        root.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        KeyLog.addListener(listener)
        refresh()
    }

    override fun onPause() {
        super.onPause()
        KeyLog.removeListener(listener)
    }

    private fun serviceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(this, KeyWatcherService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    private fun refresh() {
        val on = serviceEnabled()
        status.text = if (on) "● サービス：ON（キーを見守り中）" else "○ サービス：OFF（設定でONにしてください）"
        status.setTextColor(if (on) Color.rgb(0x19, 0x6B, 0x24) else Color.rgb(0xE9, 0x71, 0x32))
        val list = KeyLog.snapshot()
        last.text = list.firstOrNull { it.segment != null }?.segment ?: "—"
        counts.text = "区画として判定：${KeyLog.hits} 件 ／ 未登録のキー：${KeyLog.unknown} 件"
        log.text = list.joinToString("\n") { it.text }
    }

    private fun share() {
        val body = "投擲ウォッチャー テスト記録\n" +
            "端末: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} / Android ${android.os.Build.VERSION.RELEASE}\n" +
            "区画: ${KeyLog.hits} 件 / 未登録: ${KeyLog.unknown} 件\n\n" +
            KeyLog.snapshot().reversed().joinToString("\n") { it.text }
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, body)
        }
        startActivity(Intent.createChooser(i, "記録を共有"))
    }
}
