---
name: implement-screen
description: ドパガキデトックスの画面（S01〜S08）を Jetpack Compose＋Material 3 で実装・変更するときの手順。設計書と mockup を読み、designer の設計案→実装→designer と spec-reviewer のレビューまでを行う。「ホーム画面を作って」「S03 のガチャ画面」「実行中の画面を直して」など、画面の UI を作る・変えるときに使う。
argument-hint: "<画面ID 例: S01>"
---

# 画面の実装（S01〜S08）

対象：$ARGUMENTS（画面IDがなければ、依頼から下の表で特定する。特定できなければ AskUserQuestion で聞く）

## 画面の対応表
| 画面ID | 画面 | 設計書 | mockup | パッケージ（`app/src/main/java/com/dopagaki/detox/`） |
|---|---|---|---|---|
| S01 | ホーム（おすすめ） | 5.1、7.3、7.4 | 01_home.html | `ui/home` |
| S01-2 | ミッション詳細（ボトムシート） | 5.1 | 02_mission_detail.html | `ui/home` |
| S02 | 縦スワイプ | 5.2、7.4 | 03_swipe.html | `ui/swipe` |
| S03 | ガチャ | 5.3、7.1、7.3 | 04_gacha.html | `ui/gacha` |
| S03-2 | ガチャ結果（10連） | 5.3 | 05_gacha_result.html | `ui/gacha` |
| S04 | 設定 | 5.5、5.6 | 08_settings.html | `ui/settings` |
| S05 | ミッション実行中 | 5.4、6.1 | 06_mission_running.html | `ui/running` |
| S05-2 | 強制解除の待機 | 5.4、6.3 | 07_unlock_wait.html | `ui/running` |
| S06 | 完了報告 | 6.1「終了後」、7.5 | なし | `ui/report` |
| S07 | アプリのロック画面（重ね表示） | 6.2、6.3、9.3 | 09_app_locked.html | `lock` |
| S08 | 初回設定（開示と同意） | 9.2 | なし | `ui/onboarding` |

画面遷移は設計書 4章の図に従い、`navigation/` にまとめる。

## 手順
1. **仕様を読む**：設計書の 4章（遷移）、上の表の節、6.1「ロック中にできること・できないこと」。文言は設計書に書かれたものを使う
2. **見た目を読む**：対応する mockup と `docs/design-system.md`。mockup がない画面（S06・S08）は、近い画面の部品を流用する前提で designer に設計してもらう
3. **designer に設計案を依頼する**（Agent、subagent_type: designer）。依頼文に画面ID、設計書の節、mockup のパス、使えるドメインの状態を書く。
   返ってきた「要決定事項」は自分で決めず AskUserQuestion で利用者に聞き、決まったことを `docs/design-system.md` の該当節と「10. 決定の記録」に追記する
4. **ロジックが足りなければ先に `/implement-domain` で作る**。画面の中で抽選・コイン計算・時間判定を書かない
5. **実装する**
   - 画面は「状態を受け取って描くだけの Composable」と ViewModel（`StateFlow<UiState>`）に分ける。UiState に空・読み込み中・エラー・権限なし・上限到達（コイン不足、枠の使い切り、更新上限）を持たせる
   - M3 の標準部品（TopAppBar、NavigationBar、ModalBottomSheet、Button 系、Switch、ListItem、AssistChip など）を使う
   - 色・文字・形状は `MaterialTheme.colorScheme`／`typography`／`shapes` と `DetoxTheme` の拡張トークンだけ。`Color(0x…)`・`.sp`・`.dp` の値を画面に直書きしない（余白の段階は docs/design-system.md 4章の値を使う）
   - 文字列は `res/values/strings.xml`（数・コインは `plurals` ではなく書式付き文字列でよい）
   - edge-to-edge：Scaffold のインセットを使う
   - ロック中の画面（S05・S05-2・S06 の前）：ナビゲーションバーを出さず、`BackHandler(enabled = true) {}` で戻る操作を止める。`onBackPressed()` は使わない
   - 残り時間は ViewModel が `TrustedClock` と `endsAt` から求めた値を表示する。「○時○分に終了」だけ壁時計で表示してよい
   - 各画面に `@Preview` を状態ごとに用意する（通常・空・上限到達など）
6. **確かめる**：`./gradlew :app:assembleDebug`、ViewModel のテストがあれば `./gradlew :app:testDebugUnitTest`。可能なら `/run` でエミュレーターに出して mockup と見比べる
7. **レビュー**：designer（レビュー、変更したファイルを列挙）→ spec-reviewer（ファイルと画面IDを列挙）。権限・マニフェストに触れたら play-policy-reviewer も。指摘の高・中を直してから終える

## 完了の報告
作った・変えたファイル、確かめた内容（ビルド、テスト、画面で見たか）、残った要決定事項を短く書く。見ていないものは「見ていない」と書く。
