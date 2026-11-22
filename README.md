# ドパガキデトックス

SNS やゲームを開いてしまう「手グセ」を、ショート動画風のスワイプやガチャで出るミッション（読書15分、筋トレ10分、ゲーム20分など）に置き換える Android アプリです。

- ミッションを確定すると、時間が終わるまで実行画面から抜けられません
- ご褒美に設定したアプリ（ゲーム・SNS など）は普段ロックされ、ご褒美ミッションの間だけ開けます
- ログインもサーバーもなく、データはすべて端末の中に置きます

習慣づくりを手伝うアプリで、治療を目的としたものではありません。

## 状態

フェーズ1（MVP）を作っているところです。バージョンは 0.1.0 で、デバッグ APK をビルドできます。この先の予定は[設計書 11章](設計書.md#11-開発スコープとロードマップ未決事項)にあります。

## 主な機能（MVP）

| ID | 機能 |
| --- | --- |
| F01 | おすすめフィード：X のタイムラインと同じ形でミッションを並べる |
| F02 | 縦スワイプ：全画面のカードを上にスワイプして次を出し、「これにする」で確定 |
| F03 | ガチャ：コインで単発か10連を引き、レア度つきでミッションを抽選する |
| F04 | ミッション実行（ロック）：確定したらタイマー画面に固定し、終わるまで他の画面へ行けない |
| F05 | 完了報告・記録：できた／一部／できなかったを記録し、コインと連続日数をつける |
| F06 | ミッション設定：やること・ご褒美の追加と編集（時間、出やすさ、カテゴリ、レア度） |
| F07 | ロック設定：強制解除・一時停止を許すか、静かな時間帯、有利な変更の10分待ち |
| F09 | 報酬アプリの常時ロック：対象アプリが前面に来たらロック画面を重ね、ご褒美ミッションの間だけ通す |

画面の一覧（S01〜S08）と遷移図は[設計書 4章](設計書.md#4-画面一覧と画面遷移)にあります。

## しくみの要点

- 時間の判定は、端末の時計ではなく信頼時刻（`TrustedClock`）で行います。時計を進めてもロックは終わりません
- 自分に有利になる設定の変更は、10分待ってから反映されます。厳しくする変更はすぐ反映されます
- コインが増えるのは、やることを完了したとき、連続日数、初回ボーナスのときだけです。失敗してもコインは減りません
- 強制解除は、押してから10分待つと解除されます

## 必要な環境

- JDK 21
- Android SDK（パスはルートの `local.properties` に `sdk.dir=...` と書く。このファイルは git に入れない）
- Gradle 8.14.3（wrapper 同梱）、Android Gradle Plugin 8.13、Kotlin 2.2.20
- minSdk 29（Android 10）、targetSdk・compileSdk 36（Android 16）

## ビルドとテスト

リポジトリのルートで実行します。

```sh
./gradlew :domain:test             # ドメインのテスト（いちばんよく使う）
./gradlew :app:testDebugUnitTest   # アプリの単体テスト
./gradlew assembleDebug            # デバッグ APK をビルド
./gradlew lint                     # Android Lint
```

## APK の作り方と場所

### 作り方

リポジトリのルートで次を実行します（初回は Gradle と依存ライブラリのダウンロードがあるので時間がかかります）。

```sh
./gradlew assembleDebug        # macOS・Linux・Git Bash
gradlew.bat assembleDebug      # Windows のコマンドプロンプト・PowerShell
```

Android Studio を使う場合は、メニューの **Build > Generate App Bundles or APKs > Generate APKs** でも作れます。

### 場所

```
app/build/outputs/apk/debug/app-debug.apk
```

- デバッグ用の鍵で署名された APK です（30MB 前後）。`build/` の下にできるので git には入りません
- 作り直すと同じ名前で上書きされます。古いものを消してから作りたいときは `./gradlew clean assembleDebug`
- リリース用の署名はまだ設定していないので、配布用（release）の APK は作っていません

### 端末に入れる

USB デバッグを有効にした端末をつないで、次のどちらかで入れます。

```sh
./gradlew installDebug                                   # ビルドしてそのまま入れる
adb install -r app/build/outputs/apk/debug/app-debug.apk # 作った APK を入れる（-r は上書き）
```

APK を端末にコピーしてファイルアプリから開いても入れられます（「提供元不明のアプリ」の許可が必要）。

### 入れたあと

初めて開くと初回設定（S08）が出ます。ユーザー補助の説明を読んで同意し、端末の設定でユーザー補助を有効にすると、報酬アプリの常時ロックが動きます。通知の許可もここで求めます。

## 権限

| 使うもの | 用途 |
| --- | --- |
| ユーザー補助サービス | 報酬アプリの常時ロック。前面にあるアプリのパッケージ名だけを受け取り、画面の内容は読まない |
| `POST_NOTIFICATIONS` | 残り時間、終了、解除待ちの通知 |
| `RECEIVE_BOOT_COMPLETED` | 再起動したあとに通知と信頼時刻を戻す |
| `SCHEDULE_EXACT_ALARM`（任意） | ミッション終了の通知を時刻どおりに出す |

`INTERNET`、`QUERY_ALL_PACKAGES`、`SYSTEM_ALERT_WINDOW`、フォアグラウンドサービスは使いません。詳しくは[設計書 9.2](設計書.md#92-権限と申告必須)と[9.3](設計書.md#93-常時ロックの実装必須事項)にあります。

## ディレクトリ構成

```
domain/     純 Kotlin（Android に依存しない）。抽選・コイン・天井・信頼時刻などのロジックとテスト
  src/main/kotlin/com/dopagaki/detox/domain/
    Rules.kt  数値（出現率・倍率・コイン・価格・天井・待ち時間）をまとめる場所
app/        Android アプリ（Jetpack Compose＋Material 3）
  src/main/java/com/dopagaki/detox/
    ui/            画面とテーマ
    navigation/    画面遷移
    data/          Room・DataStore
    time/          信頼時刻の Android 実装
    lock/          ユーザー補助サービスとロック画面
    notification/  通知・アラーム・再起動後の復元
docs/       design-system.md（UI の決まり）
mockups/    画面の見た目の参考（HTML）
.claude/    Claude Code のエージェントと skill
```

## ドキュメント

食い違ったときは、上にあるものを優先します。

1. [設計書.md](設計書.md)：仕様の正本。実装は設計書と食い違ってはいけない
2. [docs/design-system.md](docs/design-system.md)：UI の正本（色・文字・余白・部品・決定の記録）
3. [mockups/](mockups/)：画面の見た目の参考

開発のルールと、破ってはいけない約束は [CLAUDE.md](CLAUDE.md) にまとめています。

## 開発の流れ

Claude Code の skill とエージェントを使います。

| やること | 使うもの |
| --- | --- |
| 画面を作る・変える | `/implement-screen <S-ID>` |
| ロジックを作る・変える（テスト先行） | `/implement-domain <対象>` |
| 仕様を変える・未決事項を決める | `/update-spec <内容>` |
| UI の設計案とレビュー | `designer` エージェント |
| 実装が仕様に合っているかの確認 | `spec-reviewer` エージェント |
| 権限・マニフェスト・ユーザー補助・通知・バックアップの確認 | `play-policy-reviewer` エージェント |

詳しい手順は [CLAUDE.md](CLAUDE.md) の「作業の流れ」にあります。
