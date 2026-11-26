package jp.ryutancamp.throwwatcher

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var counts: TextView
    private lateinit var last: TextView
    private lateinit var log: TextView
    private lateinit var codeInput: EditText
    private lateinit var linkStatus: TextView
    private lateinit var linkError: TextView
    private lateinit var sendSwitch: Switch
    private lateinit var unlinkButton: Button
    private val listener: () -> Unit = { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Sender.init(this)
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
        root.addView(label(20f, true).apply { text = "投擲ウォッチャー v${BuildConfig.VERSION_NAME}" })
        root.addView(label(14f).apply {
            text = "① ユーザー補助で「投擲ウォッチャー」をON\n" +
                "② PC（darts-training-lab）の PHONE で出たコードを下に入力して「連携」\n" +
                "③ 公式アプリでいつも通りプレイ → 刺さった場所が PC に届きます"
        })
        status = label(16f, true)
        root.addView(status)
        root.addView(Button(this).apply {
            text = "ユーザー補助の設定を開く"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        // ---- PC連携 ----
        root.addView(label(16f, true).apply { text = "PC連携"; setPadding(0, pad / 2, 0, 0) })
        val linkRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        codeInput = EditText(this).apply {
            hint = "PCのコード 例 AB12-CD34"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            filters = arrayOf(InputFilter.AllCaps(), InputFilter.LengthFilter(9))
            isSingleLine = true
        }
        linkRow.addView(codeInput, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        linkRow.addView(Button(this).apply {
            text = "連携"
            setOnClickListener { Sender.link(codeInput.text.toString()) }
        })
        root.addView(linkRow)
        linkStatus = label(15f, true)
        root.addView(linkStatus)
        linkError = label(13f).apply { setTextColor(Color.rgb(0xC0, 0x39, 0x2B)) }
        root.addView(linkError)
        val sendRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        sendSwitch = Switch(this).apply {
            text = "PCへ送る"
            setOnCheckedChangeListener { _, on -> if (on != Sender.enabled) Sender.setEnabled(on) }
        }
        sendRow.addView(sendSwitch, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        unlinkButton = Button(this).apply {
            text = "連携を解除"
            setOnClickListener { Sender.unlink(); codeInput.setText("") }
        }
        sendRow.addView(unlinkButton)
        root.addView(sendRow)

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
        val code = Sender.code
        val fmt = if (code.length == 8) code.substring(0, 4) + "-" + code.substring(4) else code
        linkStatus.text = when (Sender.linkState) {
            Sender.LinkState.CHECKING -> "… コードを確認中"
            Sender.LinkState.NONE -> "○ 未連携（PCのコードを入力してください）"
            else -> if (code.isEmpty()) "○ 未連携" else
                (if (Sender.enabled) "● 連携中：$fmt" else "‖ 送信停止中：$fmt") +
                    "\n送信 ${Sender.sent} 件 ／ 未送信 ${Sender.pending} 件" +
                    (if (Sender.lastSentSeg.isNotEmpty()) " ／ 最後 ${Sender.lastSentSeg}" else "")
        }
        linkStatus.setTextColor(
            if (code.isNotEmpty() && Sender.enabled && Sender.lastError.isEmpty()) Color.rgb(0x19, 0x6B, 0x24)
            else Color.rgb(0xE9, 0x71, 0x32)
        )
        linkError.text = Sender.lastError
        if (sendSwitch.isChecked != Sender.enabled) sendSwitch.isChecked = Sender.enabled
        sendSwitch.isEnabled = code.isNotEmpty()
        unlinkButton.isEnabled = code.isNotEmpty()
        if (code.isNotEmpty() && codeInput.text.isEmpty() && !codeInput.hasFocus()) codeInput.setText(fmt)

        val list = KeyLog.snapshot()
        last.text = list.firstOrNull { it.segment != null }?.segment ?: "—"
        counts.text = "区画として判定：${KeyLog.hits} 件 ／ 未登録のキー：${KeyLog.unknown} 件"
        log.text = list.joinToString("\n") { it.text }
    }

    private fun share() {
        val body = "投擲ウォッチャー 記録 v${BuildConfig.VERSION_NAME}\n" +
            "端末: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} / Android ${android.os.Build.VERSION.RELEASE}\n" +
            "区画: ${KeyLog.hits} 件 / 未登録: ${KeyLog.unknown} 件\n" +
            "PC送信: ${Sender.sent} 件 / 未送信: ${Sender.pending} 件 ${Sender.lastError}\n\n" +
            KeyLog.snapshot().reversed().joinToString("\n") { it.text }
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, body)
        }
        startActivity(Intent.createChooser(i, "記録を共有"))
    }
}
