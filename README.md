# 投擲ウォッチャー（テスト版）

DARTSLIVE-200S を公式アプリで遊びながら、刺さった場所を Android の「ユーザー補助」で
**見るだけ**拾い、PC の darts-training-lab へ送るアプリです。キーは書き換えず、そのまま公式アプリに渡します。

- 区画の割当は darts-training-lab の `data/200s-keymap.json` と同じ（82区画＋START/CHANGE）
- PC連携（v0.2.0）：PC の PLAY →「PHONE」で出た8文字のコードを入力 → 1投ずつ Firestore 経由で PC に届く
  - 送るのは区画（例 T20）・時刻・連番だけ。それ以外のキー入力は送らない
  - 通信できないときはスマホに貯めて、つながったら自動で再送（二重登録なし）
- APK は GitHub Actions で自動ビルドされ、Releases の「test-latest」に置かれます
- 送信部分のテスト：`jvm-test/README.md`（Firestore エミュレーター相手）

## 確認したいこと
1. 公式アプリでプレイ中に、このアプリが刺さった場所を拾えるか
2. 公式アプリの入力が遅れたり化けたりしないか

## 状況（2026-11-26）
- 実機テスト合格：公式アプリでプレイ中に、刺さった場所を正しく拾えた
- 詳細：`docs/2026-11-26_検討と実機テスト.md`
- 次：Firestore 経由で darts-training-lab（PC）へ送る → v0.2.0 で実装（`docs/2026-11-26_PC連携の実装.md`）
