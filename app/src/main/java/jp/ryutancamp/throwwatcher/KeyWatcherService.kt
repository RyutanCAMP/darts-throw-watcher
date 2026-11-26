package jp.ryutancamp.throwwatcher

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * キー入力を「見るだけ」のサービス。
 * onKeyEvent は必ず false を返す → キーはそのまま公式アプリに届く（握りつぶさない）。
 * 区画として判定できたものだけ Sender に渡す（通信は別スレッド。ここでは待たない）。
 */
class KeyWatcherService : AccessibilityService() {

    @Volatile private var foreground = "?"

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.packageName?.let { foreground = it.toString() }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        try {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                val seg = KeyLog.add(event, foreground)
                if (seg != null) Sender.enqueue(seg, System.currentTimeMillis())
            }
        } catch (_: Throwable) {
            // 記録に失敗しても入力は止めない
        }
        return false
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        Sender.init(this)
        KeyLog.notifyChanged()
    }
}
