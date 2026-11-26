package jp.ryutancamp.throwwatcher

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * キー入力を「見るだけ」のサービス。
 * onKeyEvent は必ず false を返す → キーはそのまま公式アプリに届く（握りつぶさない）。
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
                KeyLog.add(event, foreground)
            }
        } catch (_: Throwable) {
            // 記録に失敗しても入力は止めない
        }
        return false
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        KeyLog.notifyChanged()
    }
}
