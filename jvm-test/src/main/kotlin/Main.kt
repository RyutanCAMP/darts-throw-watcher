import jp.ryutancamp.throwwatcher.FirestoreRest
import jp.ryutancamp.throwwatcher.FirestoreRest.Outcome
var fails = 0
fun chk(c: Boolean, m: String) { println((if (c) "PASS " else "FAIL ") + m); if (!c) fails++ }
fun main(args: Array<String>) {
    val code = args[0]
    val base = "http://127.0.0.1:8085/v1/projects/darts-training-lab/databases/(default)/documents"
    val r = FirestoreRest(base, "fake-key")
    if (args.size > 1) { args[1].split(',').forEachIndexed { i, s -> Thread.sleep(250); val tt = System.currentTimeMillis(); println("send $s " + r.postThrow(code, "${tt}_${100+i}_dev", s, tt, (100 + i).toLong())) }; println("ALL PASS"); return }
    chk(FirestoreRest.normalizeCode("abcd-ef23") == "ABCDEF23", "normalize lower+hyphen")
    chk(FirestoreRest.normalizeCode("ABCD-EF2I") == null, "normalize rejects I")
    chk(FirestoreRest.normalizeCode("ABC") == null, "normalize rejects short")
    chk(r.checkLink("ZZZZZZZZ") == Outcome.NOT_FOUND, "unknown link -> NOT_FOUND")
    chk(r.checkLink(code) == Outcome.OK, "existing link -> OK")
    val t = System.currentTimeMillis()
    chk(r.postThrow(code, "${t}_1_dev", "T20", t, 1) == Outcome.OK, "post T20")
    chk(r.postThrow(code, "${t}_1_dev", "T20", t, 1) == Outcome.OK, "re-post same id -> OK (409, no dup)")
    chk(r.postThrow(code, "${t+1}_2_dev", "S20I", t + 1, 2) == Outcome.OK, "post S20I")
    chk(r.postThrow(code, "${t+2}_3_dev", "START", t + 2, 3) == Outcome.OK, "post START")
    chk(r.postThrow(code, "${t+3}_4_dev", "X99", t + 3, 4) == Outcome.REJECTED, "bad seg rejected by rules")
    chk(r.postThrow("ZZZZZZZZ", "${t}_5_dev", "T20", t, 5) != Outcome.OK, "post to non-existing link rejected")
    if (args.size > 1) { // live sender for browser test
        val segs = args[1].split(',')
        segs.forEachIndexed { i, s -> Thread.sleep(250); val tt = System.currentTimeMillis(); println("send $s " + r.postThrow(code, "${tt}_${100+i}_dev", s, tt, (100 + i).toLong())) }
    }
    println(if (fails == 0) "ALL PASS" else "FAILURES $fails")
}
