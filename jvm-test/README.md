# jvm-test：PC 送信部分のテスト（手動）

`app/src/main/java/jp/ryutancamp/throwwatcher/rest/FirestoreRest.kt` は Android の API を使わないので、
PC の JVM で動かして Firestore エミュレーター（darts-training-lab の `firestore.rules` 入り）に対して確かめられます。

1. darts-training-lab で エミュレーターを起動（手順は darts-training-lab の `tools/emulator/README.md`）
2. 連携コード `K7M2PQ9X` を作る（同 README の curl）
3. `cd jvm-test && gradle -q installDist && ./build/install/throw-watcher-jvm-test/bin/throw-watcher-jvm-test K7M2PQ9X`
   → 最後に `ALL PASS`
4. 区画を送るだけのとき：`... K7M2PQ9X T20,IB,S20I`
