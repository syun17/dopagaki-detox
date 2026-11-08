# ドパガキデトックス

SNS やゲームを開く「手グセ」を、ショート動画風スワイプやガチャで出るミッション（読書15分、筋トレ10分、ゲーム20分など）に置き換える Android アプリ。確定したミッションは時間が終わるまで抜けられず、ご褒美に設定したアプリはご褒美ミッションの間だけ開ける。MVP は端末内で完結（ログイン・サーバーなし）。

## 正本と優先順位
1. `設計書.md`：仕様の正本（第2版）。実装が設計書と食い違ってはいけない。設計書が曖昧・矛盾しているときは推測で埋めず AskUserQuestion で聞き、決まったら `/update-spec` で設計書に書く
2. `docs/design-system.md`：UI の正本（色・文字・余白・部品・決定の記録）
3. `mockups/*.html`：画面の見た目の参考（design-system.md と食い違うときは design-system.md が優先）

設計書は節番号で参照する（行番号は変わる）。

## 設計書の索引
| 節 | 内容 |
|---|---|
| 2 | コンセプト、設計方針（3タップ以内、確定したら戻れない、時間だけで区切る）、UI の方針 |
| 3 | 機能一覧（F01〜F12、MVP の範囲） |
| 4 | 画面一覧（S01〜S08）と遷移図 |
| 5.1〜5.5 | 各画面の仕様（ホーム、スワイプ、ガチャ、実行中、設定） |
| 5.6 | 有利になる設定変更の10分待ち（どの変更が待ち・即時かの表） |
| 6.1 | ミッション実行中のロック（確定のタイミング、ロック中にできること） |
| 6.2 | 報酬アプリの常時ロック（除外アプリ） |
| 6.3 | 強制解除（10分待機） |
| 6.4 | 信頼時刻、1日の切り替え（4時）、防げない抜け道 |
| 7.1〜7.3 | 抽選：レア度の出現率と倍率、重み、補正ルール、天井、枠が空のとき |
| 7.4 | ホーム・スワイプでのご褒美と更新回数（1時間に3回） |
| 7.5 | コイン（増える・減る、例、ガチャの価格の決め方） |
| 8 | データ設計（Mission、Session、LaterItem、Wallet、Settings、定数、PendingChange、ClockAnchor） |
| 9.1〜9.5 | 技術構成、権限と Play の申告、常時ロックの実装、タイマーと画面、成功指標の計測 |
| 10 | 健全性への配慮とリスク |
| 11 | ロードマップ、未決事項 |
| 12 | 変更履歴 |

## 用語とコード名
| 設計書 | コード |
|---|---|
| やること／ご褒美 | `Task`／`Reward`（`Mission.kind` = task／reward） |
| ミッション（マスタ）／実行履歴 | `Mission`／`Session` |
| カテゴリ | `Category`（READING, EXERCISE, STUDY, CHORES, MIND, GAME, SNS） |
| レア度 | `Rarity`（N, R, SR, SSR） |
| 出やすさ | `weight`（1〜5） |
| 天井、天井カウント、ご褒美確定の持ち越し | `Pity`、`pityCount`、`pityReady` |
| 信頼時刻 | `TrustedClock`、基準は `ClockAnchor` |
| 1日（4時区切り） | `DayKey`／`dayKey` |
| あとで | `LaterItem`（`Session.source` = later） |
| ホーム・スワイプ枠 | `FeedRewardSlot`（`feedRewardUsedDay`・`feedRewardUsedCount`） |
| おすすめフィード、更新 | `Feed`、`Refresh`（`refreshTimes`） |
| 10分待ち（有利な設定変更・強制解除） | `PendingChange` |
| 強制解除、一時停止 | `ForceUnlock`、`Pause` |
| 静かな時間帯 | `QuietHours`（`quietStart`・`quietEnd`） |
| 報酬アプリの常時ロック、対象アプリ | `RewardAppLock`、`linkedApps` |
| 完了報告の結果 | `Session.result`：running, done（できた）, partial（一部）, failed（できなかった）, aborted（強制解除） |
| 確定した場所 | `Session.source`：home, swipe, later, gacha_single, gacha_ten |

## 絶対に守ること（破ると設計の意味がなくなる）
- **時間の判定は必ず `TrustedClock`**。ロジックで `System.currentTimeMillis()`、`Instant.now()`、`LocalDate.now()`、`Date()`、`Calendar.getInstance()` を使わない。端末の時計は「○時○分に終了」の表示だけに使う（6.4）
- **数値は `domain/.../Rules.kt` に集約**（出現率・倍率・コイン単価・ガチャ価格・天井・上限・待ち時間）。直書きしない。値の正本は設計書 8章で、変えるときは先に設計書を直す
- **有利な設定変更は `PendingChange` 経由で10分待ち**、厳しくする変更は即時（5.6 の表）
- **ロック中は抜けられない**：ナビゲーションバーを出さない、`BackHandler` で戻る操作を止める、設定に入れない。`onBackPressed()` は使わない（targetSdk 36 の予測型「戻る」）。起動時に `result=running` かつ `endsAt` が未来の Session があれば実行画面から再開
- **コインは失敗で減らさない**。増えるのはやることの完了・連続日数・初回ボーナスだけ（7.5）
- **権限**：`QUERY_ALL_PACKAGES`、`SYSTEM_ALERT_WINDOW`、`INTERNET`、フォアグラウンドサービスを使わない。対象アプリの一覧は `ACTION_MAIN`＋`CATEGORY_LAUNCHER` の `<queries>` で取る（9.2）
- **ユーザー補助サービス**：`canRetrieveWindowContent="false"`、`isAccessibilityTool="false"`、イベントは `TYPE_WINDOW_STATE_CHANGED` のみ、ロック画面は `TYPE_ACCESSIBILITY_OVERLAY`。画面の内容を読まない。設定へ案内する前に独立した開示画面で同意を得る（9.2、9.3）
- **除外アプリ**（既定の電話・SMS・ホーム、端末の設定、システムUI、IME、自分自身）は対象にできない（6.2）
- **バックアップ**：実行中の Session・PendingChange・ClockAnchor は `runtime_state.db` に分けて保存し、`data_extraction_rules.xml`／`backup_rules.xml` で除外する（9.4）
- **edge-to-edge** 前提。インセットは Scaffold で処理する（9.4）
- 治療効果をうたう文言（「依存症を治す」など）を書かない（10章）

## 構成
```
:domain   純 Kotlin（JVM）。Android に依存しない。テストしやすいロジックはすべてここ
  domain/src/main/kotlin/com/dopagaki/detox/domain/
    Rules.kt            定数と Settings の初期値（設計書 8章）
    Rarity.kt, Category.kt
    （コインの倍率は整数の百分率 rarityMultiplierPercent。コイン = 単価 × 分 × 倍率% / 100）
    time/TrustedClock.kt  信頼時刻のインターフェース
    （これから）lottery/ coin/ pity/ refresh/ pending/ session/ day/
  domain/src/test/...   FakeTrustedClock、テスト
:app      Android アプリ（com.dopagaki.detox、minSdk 29・targetSdk 36・compileSdk 36）
  app/src/main/java/com/dopagaki/detox/
    MainActivity.kt
    ui/theme/           テーマ（Color, Type, Shape, Spacing, Sizes, Theme）。値は docs/design-system.md
    （これから）navigation/  ui/<画面>/  data/（Room・DataStore）  time/（TrustedClock の実装）
              lock/（AccessibilityService とロック画面）  notification/
```
画面 ID とパッケージ・mockup の対応は `/implement-screen` の skill にある。

UI のルール（詳しくは docs/design-system.md）：M3 の標準部品を使う。色・文字・形状・余白は `MaterialTheme.*` と `DetoxTheme.colors`／`textStyles`／`spacing`／`sizes` を経由し、画面に `Color(0x…)`・`dp`・`sp` を直書きしない。区切り線は `DetoxTheme.colors.divider`、残り時間・コインは等幅数字のスタイル（`timerLarge`、`coinLabel` など）。

## コマンド（リポジトリのルートで実行）
```
./gradlew :domain:test             ドメインのテスト（いちばんよく使う）
./gradlew :app:testDebugUnitTest   アプリの単体テスト
./gradlew assembleDebug            ビルド
./gradlew lint                     Android Lint
```
JDK 21、Android SDK は `local.properties` の `sdk.dir`（git 管理外）。

## 作業の流れ
- 画面を作る・変える：`/implement-screen <S-ID>`
- ロジックを作る・変える：`/implement-domain <対象>`（テスト先行。設計書から作ったテストケース一覧つき）
- 仕様を変える・未決事項を決める：`/update-spec <内容>`
- 実装のあと：`spec-reviewer` エージェントに、変更したファイルと機能ID・画面IDを列挙して頼む
- 権限・マニフェスト・ユーザー補助・通知・バックアップに触れたら：`play-policy-reviewer` エージェントにも頼む
- コミットは利用者に言われたときだけ

## UI のデザインについて（グローバルの指示の上書き）
グローバルの CLAUDE.md の「UI のデザイン」の規則（着手前とコミット前に designer、要決定事項は AskUserQuestion）はこのリポジトリでも守る。ただし次を読み替える。
- `designer` はこのリポジトリの `.claude/agents/designer.md`（docs/design-system.md を読む版）が使われる
- 決まったことの追記先は `~/.claude/design/design-system.md` ではなく **`docs/design-system.md`** の該当の節と「10. 決定の記録」
- docs/design-system.md の「未決」の表にある事項は、その画面を作るときに利用者に聞く
