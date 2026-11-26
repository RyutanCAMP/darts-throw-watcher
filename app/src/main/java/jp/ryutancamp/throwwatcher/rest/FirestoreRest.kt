package jp.ryutancamp.throwwatcher

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Firestore の REST API を直接たたく小さなクライアント（ライブラリなし・Android の API も使わない）。
 * darts-training-lab（PC）と同じ Firebase プロジェクトの links/{連携コード}/throws に1投ずつ書き込む。
 * ログインはしない。連携コードそのものが合言葉（firestore.rules で形と中身を制限している）。
 */
class FirestoreRest(
    private val base: String = BASE,
    private val apiKey: String = API_KEY,
) {
    companion object {
        const val PROJECT_ID = "darts-training-lab"
        /** Firebase の Web 用 API キー（PC 版の app.js と同じ。秘密の値ではない） */
        const val API_KEY = "AIzaSyCiPeb0A-_dCbRGSHtVM0yHLoNHray-iJ0"
        const val BASE = "https://firestore.googleapis.com/v1/projects/$PROJECT_ID/databases/(default)/documents"
        private const val REFERER = "https://darts-training-lab.web.app/"
        private val CODE = Regex("^[2-9A-HJ-NP-Z]{8}$")

        /** 入力された連携コードを整える（小文字・ハイフン・空白を許す）。形が違えば null。 */
        fun normalizeCode(input: String): String? {
            val c = input.uppercase().filter { it.isLetterOrDigit() }
            return if (CODE.matches(c)) c else null
        }
    }

    enum class Outcome { OK, NOT_FOUND, REJECTED, RETRY }

    /** 連携コードが PC で発行済みか確認する。 */
    fun checkLink(code: String): Outcome = try {
        when (val status = request("GET", "$base/links/$code", null)) {
            200 -> Outcome.OK
            404 -> Outcome.NOT_FOUND
            400, 403 -> Outcome.REJECTED
            else -> if (status in 500..599 || status == 429) Outcome.RETRY else Outcome.REJECTED
        }
    } catch (_: IOException) {
        Outcome.RETRY
    }

    /**
     * 1投を書き込む。docId をこちらで決めるので、再送しても二重にならない（既にあれば 409 → 成功扱い）。
     * OK=送れた、REJECTED=送り直しても無駄（コード違い・ルール未更新など）、RETRY=あとで再送。
     */
    fun postThrow(code: String, docId: String, seg: String, t: Long, n: Long): Outcome = try {
        val body = """{"fields":{"seg":{"stringValue":"${esc(seg)}"},"t":{"integerValue":"$t"},"n":{"integerValue":"$n"}}}"""
        when (val status = request("POST", "$base/links/$code/throws?documentId=$docId", body)) {
            200, 409 -> Outcome.OK
            in 500..599, 429, 408 -> Outcome.RETRY
            404 -> Outcome.NOT_FOUND
            else -> Outcome.REJECTED
        }
    } catch (_: IOException) {
        Outcome.RETRY
    }

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun request(method: String, url: String, body: String?): Int {
        val sep = if (url.contains('?')) '&' else '?'
        val conn = URL("$url${sep}key=$apiKey").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Referer", REFERER)
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = conn.responseCode
            // 本文は読み捨てる（接続を再利用できるように）
            (if (status >= 400) conn.errorStream else conn.inputStream)?.use { it.readBytes() }
            return status
        } finally {
            conn.disconnect()
        }
    }
}
