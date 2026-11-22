# デザインシステム（ドパガキデトックス）

このアプリ（Android・Jetpack Compose・Material 3）の UI の正本。designer エージェントとメインの会話の両方がこの文書を使う。
グローバルの `~/.claude/design/design-system.md`（Web 向け）とは値が違う。**このリポジトリではこの文書を優先する**。
ここにないことは決めずに利用者に聞き、決まったら該当の節と「10. 決定の記録」に追記する。

値の出どころは `mockups/` の HTML（2026-11-08 時点）、`設計書.md` 2章「UIの方針」、利用者の決定（10章）。
Compose では `app/src/main/java/com/dopagaki/detox/ui/theme/` のトークン（`MaterialTheme.colorScheme`／`typography`／`shapes` と、拡張の `DetoxTheme.colors`／`textStyles`／`spacing`／`sizes`）を経由して使い、値を画面に直書きしない。

## 1. 原則
グローバル design-system.md の「1. 原則」を継承する。加えて設計書 2章の方針に従う。
- Material Design 3 の標準部品（TopAppBar、NavigationBar、ModalBottomSheet、Switch、RadioButton、Button 系、TabRow など）を使う。自作の部品は標準部品で足りないときだけ
- 白ベース。色はカテゴリとレア度の識別だけに使い、面の塗りや装飾は最小限にする
- グラデーション、発光、枠付きカードの多用など「作り込みすぎた見た目」を避ける（例外：ガチャの SSR の虹。9章）
- ホームは X のタイムラインの形（行と細い線、ボタンなし）
- 左揃えを基本にする。中央揃えにするもの：ホームのトップアプリバーのタイトル、実行中・解除待ち・ロック画面のタイマーと見出し、スワイプの時間と「上にスワイプで次へ」、ボタンの下の補足、ガチャの出現率の並び、空の状態
- 空・読み込み中・エラー・権限なし（ユーザー補助がオフ、通知が不許可）・上限到達（コイン不足、枠の使い切り、更新上限）の状態を必ず設計する
- ライトのみ。dynamic color（壁紙の色）は使わない

## 2. 色
コントラスト比は WCAG の式で計算した値。

### M3 のカラーロール
| ロール | 値 | 用途（mockups の例） | 比 |
|---|---|---|---|
| primary | `#1E6B45` | 主ボタンの塗り、リンク文字、進捗の値、設定の区分見出し、タブの選択中、スイッチのオン | 白と 6.47 |
| onPrimary | `#FFFFFF` | primary の上の文字・アイコン | |
| primaryContainer | `#D5E8D9` | （近い値で埋めたもの。secondaryContainer と同じ） | |
| onPrimaryContainer | `#0F1F15` | 同上 | 13.34 |
| secondary／tertiary | `#1E6B45` | （近い値で埋めたもの。primary と同じ） | |
| onSecondary／onTertiary | `#FFFFFF` | | |
| secondaryContainer／tertiaryContainer | `#D5E8D9` | ナビの選択中インジケーター、トーナルボタン（「一時停止」） | |
| onSecondaryContainer／onTertiaryContainer | `#0F1F15` | 上の文字・アイコン | 13.34 |
| background／surface／surfaceBright／surfaceContainerLowest | `#FFFFFF` | 画面の背景、詳細のボトムシート | |
| surfaceContainerLow | `#F7F8F6` | 解除待ちのダイアログ・シート | |
| surfaceContainer | `#F2F4F1` | ナビゲーションバー、注意書きの箱、「ロック中」ラベル | |
| surfaceContainerHigh／surfaceVariant | `#E6E8E4` | N のレア度の丸（近い値で埋めたもの：surfaceContainerHigh） | |
| surfaceContainerHighest／surfaceDim | `#E1E3DF` | 進捗の溝、スイッチのオフの溝 | primary と 5.01 |
| onSurface／onBackground | `#191C1A` | 本文、見出し | 白と 17.18 |
| onSurfaceVariant | `#42493F` | 色のある面の上の補足、アイコン、無効な項目の文字 | 白と 9.31 |
| outline | `#72796F` | アウトラインボタンの枠、ラジオの未選択、スイッチのオフの枠（文字には使わない） | 白と 4.49 |
| outlineVariant | `#C2C9BD` | チップの枠、ボトムシートのハンドル | |
| inverseSurface | `#2E3330` | Snackbar の面 | |
| inverseOnSurface | `#F2F4F1` | Snackbar の文字 | 11.63 |
| inversePrimary | `#D5E8D9` | Snackbar のアクション | 10.03 |
| error | `#C2362F` | エラーの文字・アイコン | 白と 5.44 |
| onError | `#FFFFFF` | | |
| errorContainer | `#FBEDEC` | エラーの箱 | error と 4.77 |
| onErrorContainer | `#C2362F` | | 4.77 |
| surfaceTint | `#1E6B45` | | |
| primaryFixed／secondaryFixed／tertiaryFixed | `#D5E8D9` | （近い値で埋めたもの） | |
| primaryFixedDim／secondaryFixedDim／tertiaryFixedDim | `#E1E3DF` | 同上 | |
| onPrimaryFixed など（on…Fixed） | `#0F1F15` | 同上 | |
| onPrimaryFixedVariant など（on…FixedVariant） | `#1E6B45` | 同上 | |
| scrim | `#000000`（不透明度は M3 の既定 32%） | ボトムシートの背面 | |

「近い値で埋めたもの」は mockups に出てこないロール。M3 既定の紫系を残さないために、文書にある近い値を入れている（10章）。

### 拡張トークン（`DetoxTheme.colors`）
| トークン | 値 | 用途 |
|---|---|---|
| textSecondary | `#5C6159` | **白の面の上**の補足文字（タイムラインの「· 読書 · 20分」と最下段、注記、N のレア度の文字） |
| textBody | `#2E3330` | スワイプ画面の説明文 |
| divider | `#E6E8E4` | 区切り線（タイムラインの行の間、ホームのトップバーの下、設定の区分の間）。HorizontalDivider には必ずこれを渡す |
| selectedRow | `#EEF6F0` | 選択中の行の背景（ガチャ結果） |
| onTintedSurface | `#FFFFFF` | スワイプ画面の色付き背景の上に置く丸・チップ |
| onTintedSurfaceTranslucent | `#FFFFFF` の不透明度 80%（`#CCFFFFFF`） | スワイプ画面の上部のページ表示 |
| warning | `#7A5600` | 待機中の表示（「9:42 後にオフになります」） |
| coin | `#7A5600` | コインのアイコン |

補足の文字色の使い分け：白の面の上は textSecondary、色のある面（surfaceContainer、カテゴリ背景、スワイプ背景など）の上は onSurfaceVariant。
無効な項目の文字は onSurfaceVariant にし、「無効」の文言やスイッチで状態を示す（色の薄さだけで示さない）。

### カテゴリ色（`DetoxTheme.colors.category(...)`）
| カテゴリ（`category`） | 表示名 | 前景 | 背景（丸） | スワイプ背景 | 前景と背景の比 |
|---|---|---|---|---|---|
| reading | 読書 | `#2152B8` | `#DCE6FB` | `#EEF2FD` | 5.65 |
| exercise | 運動 | `#146C2E` | `#D7F0DC` | `#EBF8EE` | 5.41 |
| study | 学習 | `#7B3A9E` | `#EEDFF7` | `#F6EFFB` | 5.62 |
| chores | 家事 | `#9A4A0A` | `#FDE5D2` | `#FEF2E8` | 5.16 |
| mind | こころ | `#55680C` | `#E8F0CD` | `#F4F8E6` | 5.27 |
| game／sns（ご褒美） | ご褒美 | `#7A5600` | `#FCEFC7` | `#FEF7E3` | 5.80 |

- ご褒美は game・sns を問わず同じ色（mockups の 01・08・09）
- スワイプ背景は「背景（丸）と白を半分ずつ混ぜた色」で決める（読書の mockup の `#EDF2FC` とほぼ同じ）。スワイプ背景の上で onSurfaceVariant は 8.27 以上、前景は 5.54 以上

### レア度の色（`DetoxTheme.colors.rarity(...)`）
| レア度 | 文字（白の上） | 丸の背景 | 丸の中の文字 |
|---|---|---|---|
| N | textSecondary `#5C6159` | `#E6E8E4` | onSurfaceVariant `#42493F` |
| R | `#2152B8` | `#DCE6FB` | `#2152B8` |
| SR | `#7A5600` | `#FCEFC7` | `#7A5600` |
| SSR | `#A8326E` | `#FBE1EC` | `#A8326E`（5.11） |

色だけで意味を伝えない。レア度は必ず文字（N／R／SR／SSR）を添え、カテゴリは表示名を添える。

### ガチャの演出の光（設計書 5.3：白→青→金→虹）
- N＝白、R＝青、SR＝金は単色の光。SSR＝虹だけは多色のグラデーションを許す（ガチャの演出に限る例外。9章）
- 光の具体的な色の値、動き、「アニメーションを削除」がオンのときの扱いは未決（ガチャ画面を作るときに決める）

## 3. タイポグラフィ
- フォント：端末標準（`FontFamily.Default`。英数は Roboto、日本語は Noto Sans CJK）。フォントファイルは同梱しない
- M3 の type scale（既定値）を使う。mockups との対応：

| 用途 | サイズ | M3 のスタイル |
|---|---|---|
| 実行中の残り時間「12:34」、スワイプの「15分」 | 57 | displayLarge |
| 解除待ちの「9:42」 | 45 | displayMedium |
| ロック画面の解除待ち「9:42」 | 36 | displaySmall |
| 画面の大見出し（実行中のミッション名、「1つ選んでスタート」、ロック画面の見出し） | 28 | headlineMedium |
| ボトムシート・スワイプのミッション名、解除待ちの見出し | 24 | headlineSmall |
| トップアプリバーのタイトル、スワイプの「分」 | 22 | titleLarge |
| 本文（詳細の説明、実行中の応援） | 16 | bodyLarge |
| 補足・注意書き | 14 | bodyMedium |
| ボタン、チップ、区分見出し | 14・500 | labelLarge（Button の既定のまま） |
| ナビゲーションバーのラベル、小さな注記 | 12 | labelMedium／bodySmall |

- **M3 から外れる拡張スタイル**（`DetoxTheme.textStyles`。X のタイムラインに合わせたもの）
  - timelineBody：15sp／20sp・400（タイムラインの1・2行目、ガチャ結果の行）
  - timelineMeta：13sp／18sp・400（タイムラインの最下段）
  - metaLabel：13sp／18sp・500（スワイプのチップと上部ラベル）
  - swipeBody：15sp／22sp・400（スワイプの説明。仮決定 U9）
- 太さ：400（本文・大見出し）、500（タイトル・ボタン・チップ）、700（ミッション名の太字、レア度、選択中のナビのラベル）
- 数字（残り時間、コイン）は等幅数字（tnum）の拡張スタイルを使う：timerLarge／timerMedium／timerSmall（displayLarge／Medium／Small＋tnum）、coinLabel（labelLarge＋tnum）、coinMeta（timelineMeta＋tnum）、timerInline（bodyMedium＋tnum。仮決定 N6）。M3 の type scale そのものは変えない

## 4. 余白とレイアウト
- 余白の段階：4 / 8 / 12 / 16 / 24 / 32dp（`DetoxTheme.spacing`）。mockups の 6・20・40 などは近い段階に寄せる
- 画面の左右の余白：一覧（タイムライン、設定の行）とトップアプリバーは 16dp、それ以外（ボトムシート、ガチャ、ガチャ結果、実行中、ダイアログ、ロック画面）は 24dp
- タイムラインの行は上下 12dp・左右 16dp、アイコンと文の間 12dp
- 部品の寸法は `DetoxTheme.sizes` に置く：丸（タイムライン 40dp、ボトムシート 48dp、ガチャ結果 36dp）、ボタンの高さ（48dp、ダイアログの中は 40dp）、チップの高さ 32dp、進捗（直線 4dp、円 260dp・線 8dp）、アイコン（16／20／24、丸の中 22、ボトムシート 26、スワイプ 112dp）、スワイプの右側アクション（56／48dp）、区切り線 1dp、スワイプの白い丸 220dp、ガチャマシンの絵 240×270dp、ロック画面のタイル 72dp・中のアイコン 36dp・バッジ 32dp・バッジの縁 2dp（仮決定 N7）
- 起動直後のウィンドウの背景は background（`#FFFFFF`）に合わせる（`res/values/themes.xml`）
- edge-to-edge 前提。Scaffold の `contentWindowInsets` でシステムバーの余白を処理する
- 縦向きのスマホのみを想定（タブレット・横向きは対応しない）

## 5. 形状・境界線・影
M3 の既定の shape と同じ値を使う。
| M3 shape | 値 | 用途 |
|---|---|---|
| extraSmall | 4dp | （未使用） |
| small | 8dp | チップ、スワイプ上部のラベル |
| medium | 12dp | 注意書きの箱 |
| large | 16dp | スワイプの「これにする」ボタン（FAB） |
| extraLarge | 28dp | ボトムシートの上端、ダイアログ・解除待ちのシート |
| 全丸（`CircleShape`） | 50% | ボタン、カテゴリ・レア度の丸、ナビの選択中インジケーター |

- 区切りは 1dp の線（divider）か、面の色の差で表す。行を枠付きカードで包まない
- 影（elevation）はボトムシート・ダイアログなど浮いている要素だけ
- 円形プログレス：直径 260dp、線 8dp、端は丸、溝は surfaceContainerHighest

## 6. アイコン
- **Material Symbols Rounded**（weight 400、grade 0、optical size 24）。Google Fonts の Material Symbols から Android 用の Vector Drawable（`res/drawable/ic_*.xml`）として取り込む
- 普段は線（Outlined の形）、ナビゲーションバーの選択中は塗り（Fill）
- 大きさ：16 / 20 / 24dp を基本。カテゴリの丸の中は 22dp、詳細のボトムシートでは 26dp、スワイプの大きなカテゴリアイコンは 112dp
- 意味のあるところだけに使う。見出しの飾りには使わない

## 7. 文言
グローバル design-system.md の「7. 文言」を継承する（文はです・ます、ボタンは体言止め、エラーは何が起きたかとどうすればよいか）。
- 例外：ミッションのひとこと・応援文は親しみのある言い切りを許す（「紙の本で目を休めよう。」）
- 数字は3桁区切り（「12,000コイン」）、時間は「20分」「12:34」
- 区切りは中黒の前後に半角スペース（「読書 · 20分」）
- 設計書に書かれた文言（「今日の枠は使い切りました」「本当に解除しますか？」「つらいときは休んでOK」など）はそのまま使う

## 8. コンポーネント
mockups にあるもの。新しい部品が必要になったら、designer が要決定事項として返す。

### トップアプリバー
- ホーム：CenterAlignedTopAppBar（アプリ名だけ）＋下に divider
- ガチャ・設定：TopAppBar（左揃え）。右端にコインのチップ（ガチャ）、追加のアイコンボタン（設定）。区切り線なし

### タイムラインの行（S01）
- 左に 40dp のカテゴリの丸、右に3段：1行目「**ミッション名** · カテゴリ · 時間」（timelineBody、カテゴリ以降は textSecondary）、2行目にひとこと（timelineBody）、最下段にメタ（timelineMeta、textSecondary、上に 4dp か 8dp）
- 最下段：やること「レア度 · これまで○回 · ○日前」、ご褒美「レア度 · ○コイン · 今日あと○回」
- 行全体がタップ領域。行の中にボタンを置かない。行の下に divider
- 末尾に「引っ張って更新 · あと○回（1時間に3回まで）」（timelineMeta、中央）

### ミッション詳細のボトムシート（S01-2）
- ModalBottomSheet（上端 28dp、ハンドル outlineVariant）
- 情報のチップ（高さ 32dp、small、outlineVariant の枠）：カテゴリ、時間、レア度（レア度色の文字）、コイン
- 注意書きの箱（surfaceContainer、medium、アイコン＋文）
- 主ボタンは「始める」の1つだけ（塗り、全幅、高さ 48dp）

### ボタン
- 塗り（primary）：主な操作1つ
- アウトライン（outline の枠、primary の文字）：並ぶ副の操作（「単発 · 600コイン」）
- トーナル（secondaryContainer）：「一時停止」
- テキスト（primary の文字）：「強制解除」（「このまま待つ」は置かない。仮決定 N11）
- 文字は M3 の既定（labelLarge 14・500）

### ナビゲーションバー
- 4項目（ホーム・スワイプ・ガチャ・設定）、surfaceContainer、選択中は secondaryContainer のインジケーター
- ラベルは選択中だけ 700、ほかは 500
- ロック中は表示しない

### 進捗
- 直線：高さ 4dp、溝 surfaceContainerHighest、値 primary（天井の進み、解除待ち）
- 円形：5章のとおり

### その他（mockups にあるもの。形は各画面を作るときに designer が詳しく決める）
- タブ（設定の「やること／ご褒美」）：M3 PrimaryTabRow
- ラジオの選択リスト（ガチャ結果）：行全体を選べ、選択中は selectedRow の背景
- 設定の行：先頭にカテゴリの丸（中は1文字「読」「運」など）、2行の ListItem、Switch は M3 の既定の配色
- 設定の区分見出し：labelLarge・primary。どの群も SectionHeader で始め、見出しのない群を作らない
- 設定の行の末尾のアイコン：アプリ内の画面に進む行は `ic_chevron_right`、アプリの外（端末の設定・ブラウザ）に出る行は `ic_open_in_new`。どちらも ListItem の既定の大きさと色
- 設定の「このアプリについて」（一番下の節）：ヘルプ・プライバシーポリシー・バージョンの順。先頭アイコンはつけず、プライバシーポリシーだけ末尾に `ic_open_in_new`、読み上げは `onClickLabel`「ブラウザで開く」。バージョンは押せない2行の行（補足は textSecondary、等幅にしない）。ブラウザが見つからないときは Snackbar（`SnackbarDuration.Long`。アクションつきの Snackbar は、押すまでの時間を取るため Long にする）で知らせ、アクション「URLをコピー」をつける（`actionOnNewLine` で文の下の行に出す）。コピーしたあとは、Android 12 以前だけ Snackbar「URLをコピーしました」を出す（13 以降は端末がコピーを知らせるので重ねない）
- 「ロック中」ラベル（実行中の上部）、スワイプの右側アクション（FAB 56dp と 48dp の丸）、スワイプのページ表示、ロック画面のアプリのタイルとバッジ、ガチャの出現率の並び

## 9. 禁止事項
グローバル design-system.md の「9. AI 感を避けるための禁止事項」を継承し、レビューでは重大度「高」として指摘する。
ただし次は許す：
- M3 の標準：ボタンの全丸、ボトムシート・ダイアログの 28dp の角丸、ナビゲーションバーの選択中インジケーター
- ガチャの演出の SSR（虹）の多色グラデーション。ガチャの演出以外では使わない

加えて次も禁止する：
- ガチャ以外の画面での演出（光、紙吹雪、跳ねる動き）
- タイムラインの行をカードにする、行にボタンを置く
- `lightColorScheme()` のロールを省いて M3 既定の紫系を残す

## 10. 決定の記録
| 日付 | 決定 | 理由 |
|---|---|---|
| 2026-11-08 | このアプリの UI の正本はリポジトリ内の docs/design-system.md とし、グローバルの文書より優先する | 利用者の指定。Android・M3 のアプリで、Web 向けのグローバル文書と値が合わないため |
| 2026-11-08 | 色・文字・形状は mockups の値を正とする（primary `#1E6B45`、M3 既定の shape、端末標準フォント） | mockups が設計書 2章の方針に沿って作られているため |
| 2026-11-08 | 原則・文言・禁止事項はグローバルの 1・7・9 節を継承する | 自作アプリ共通の方針を保つため |
| 2026-11-08 | タイムラインは M3 外の 15sp／13sp を使う | X のタイムラインの形に合わせる（設計書 2章） |
| 2026-11-08 | mockups にない M3 のロールは、文書にある近い値で埋める。error はグローバルの `#C2362F`／`#FBEDEC` を流用 | 利用者の指定。M3 既定の紫系を残さないため |
| 2026-11-08 | ライトのみ。dynamic color は使わない | 利用者の指定。mockups の色を固定で使う。トークン経由にして後からダークを足せるようにする |
| 2026-11-08 | 「こころ」は黄緑〜オリーブ系（`#55680C`／`#E8F0CD`） | 利用者の指定。既存の色相のいちばん広い空き（42°〜138°）に置ける（色相 72°） |
| 2026-11-08 | アイコンは Material Symbols Rounded | 利用者の指定 |
| 2026-11-08 | ガチャの SSR の虹だけ多色グラデーションを許す（ガチャの演出に限る） | 利用者の指定。設計書 5.3 の「虹」を残す |
| 2026-11-08 | トップアプリバーはホームだけ中央、ほかは左揃え | 利用者の指定（mockups のとおり） |
| 2026-11-08 | 余白は段階（4〜32）に寄せ、画面の左右は一覧とトップバー 16dp・それ以外 24dp | 利用者の指定 |
| 2026-11-08 | ボタンの文字は M3 の labelLarge（14・500） | 利用者の指定。Button の既定のままで指定忘れが起きない |
| 2026-11-08 | 区切り線は拡張トークン divider（`#E6E8E4`）を必ず渡す | 利用者の指定。HorizontalDivider の既定（outlineVariant）では mockups より濃くなるため |
| 2026-11-08 | 補足の文字は、白の面の上は textSecondary、色のある面の上は onSurfaceVariant | 利用者の指定。mockups の大部分がこの使い分け |
| 2026-11-08 | 無効な項目の文字は onSurfaceVariant にし、文言・スイッチで状態を示す | 利用者の指定。mockups の `#72796F` は白の上で 4.49 で 4.5 に届かない |
| 2026-11-08 | スワイプ背景はカテゴリの丸の背景と白を半分ずつ混ぜた色 | 利用者の指定。読書の mockup の値とほぼ同じになる |
| 2026-11-08 | 部品の寸法は拡張トークン `DetoxTheme.sizes` に置く | 利用者の指定。画面に dp を直書きせず、複数の画面で値をそろえる |
| 2026-11-08 | 等幅数字は拡張スタイル（timer 系、coin 系）で用意する | 利用者の指定。M3 の type scale は既定のまま保つ |
| 2026-11-08 | 白 80% の面は拡張トークン onTintedSurfaceTranslucent | 利用者の指定。画面で alpha を直書きしない |
| 2026-11-08 | 起動直後のウィンドウの背景を白（background）に合わせる | 利用者の指定。プラットフォームの既定値を決定なしに残さない |
| 2026-11-08 | M3 の fixed 系のロールも既存の緑・グレーで埋める | 利用者の指定。M3 既定の紫系を残さない |
| 2026-11-08 | 未決の11件と、画面を作るときに出た19件を designer の推奨で仮決定（下の「仮決定」の表） | 利用者の方針（推奨で進め、最後にまとめて確認） |
| 2026-11-22 | 設定（S04）の一番下に節「このアプリについて」を作り、ヘルプ（見出しのない群から移す）・プライバシーポリシー・バージョンを並べる。「アプリについて」にしない | 利用者の指定（designer の推奨）。ほかの群はすべて見出しで始まる。「アプリ」は対象アプリの意味でも使うので読み違えを避ける |
| 2026-11-22 | この節の行には先頭アイコンをつけない（ヘルプの ic_help も外す）。アプリの外に出る行は末尾 ic_open_in_new、アプリ内の画面に進む行は末尾 ic_chevron_right（ダイアログを開くヘルプの行は末尾アイコンなし） | 利用者の指定（designer の推奨）。アイコンは意味のあるところだけ（6章）、通知の行と同じ印で外部を示す |
| 2026-11-22 | バージョンを押せない行で出す（versionName から取る） | 利用者の指定（designer の推奨）。問い合わせのときに版を伝えられる |
| 2026-11-22 | プライバシーポリシーを開けないときは Snackbar（Long）に「URLをコピー」のアクションをつけ、文の下の行に出す（actionOnNewLine）。コピーの知らせは Android 12 以前だけアプリが出す | 利用者の指定（designer の推奨は文だけ）。ブラウザがなくても URL を別の手段で開けるようにする |
| 2026-11-22 | ブラウザが報酬アプリの対象でも、プライバシーポリシーを開くときの例外は作らず、補足文も出さない | 利用者の指定（designer の推奨）。例外はブラウザを自由に使える抜け道になる（設計書 6.2） |

### 仮決定（2026-11-08。designer の推奨で進めたもの。利用者の確認待ち）
利用者の方針（「推奨で進め、最後にまとめて確認」）により、次は推奨案で実装した。確認が済んだら上の表に移す。

| 番号 | 事項 | 仮決定 | 関係する画面 |
|---|---|---|---|
| U1 | ナビの選択中アイコン | 4項目とも選択中は塗り（`_fill`） | 全タブ |
| U2 | ボトムシート・スワイプのミッション名の太さ | 両方 400（headlineSmall の既定） | S01-2、S02 |
| U3 | 解除待ちの見出しの形 | 07 の形（AlertDialog の既定：ic_lock_open、中央、headlineSmall）。中身は共通の UnlockWaitContent。数字は S05-2 が timerMedium、S07 が timerSmall | S05-2、S07 |
| U4 | 詳細のボトムシートの面 | 白（surface） | S01-2 |
| U5 | 情報のチップ | 押せない表示用の部品（Surface＋outlineVariant の枠、高さ chipHeight、左右 lg） | S01-2、S03、S07 |
| U6 | 「反映待ち」の場所 | 設定の上部に一覧と取り消し。行の2行目は状態の表示だけ（「9:42 後にオフになります」） | S04 |
| U7 | フォーカスの表示 | M3 の既定 | 全画面 |
| U8 | ロック画面のタイルの角丸 | large（16） | S07 |
| U9 | 15/22 の文字 | S02 の説明だけ拡張スタイル swipeBody（15/22・400）。S03-2 は timelineBody | S02、S03-2 |
| U10 | ガチャの光 | 白い面のまま、単色の円を広げる（N・R・SR はレア度の丸の背景色、SSR はカテゴリの丸の背景6色と SSR の丸の背景の sweepGradient）。レバー→光の順で 2.4 秒、タップで飛ばせる。「アニメーションを削除」がオンなら動かさずに結果へ | S03 |
| U11 | 押せないボタン | M3 の既定の無効表示と、ボタンの下に理由の文（onSurface）。S02 の FAB は押せる見た目のまま、押したら Snackbar で理由を出し、コインのチップも理由に差し替える | S01-2、S02、S03 |
| N1 | カテゴリのアイコン | 読書 menu_book、運動 fitness_center、学習 edit、家事 cleaning_services、こころ self_improvement、ゲーム sports_esports、SNS forum。1文字は 読・運・学・家・心・褒。編集画面だけ GAME「ゲーム」・SNS「SNS」 | 全画面 |
| N2 | 更新の上限中の引っ張る操作 | 止める（末尾の文で次の更新までの時間を出す） | S01 |
| N3 | ユーザー補助オフの知らせ | 注意書きの箱（surfaceContainer）全体を押せる形にし、開示の画面へ | S01 |
| N4 | ホームの反映待ち | 出さない | S01 |
| N5 | あとでの保存・解除 | S02 の「あとで」だけ（押すたびに切り替え）。詳細には置かない。始めたらあとでから消える | S01-2、S02 |
| N6 | 本文サイズの等幅数字 | 拡張スタイル timerInline（bodyMedium＋tnum） | S01、S04、S05 |
| N7 | 新しい寸法 | sizes に swipeHeroCircle 220、gachaMachineWidth 240・gachaMachineHeight 270、lockTile 72・lockTileIcon 36・lockBadge 32・lockBadgeBorder 2 を足す | S02、S03、S07 |
| N8 | mockup の端数 | 段階に寄せる（タイムラインの1・2行目の間 0、最下段の上 sm、末尾の上下 lg。S02 のチップの高さ chipHeight。S03-2 の行の上下 md。上の余白 40 は xxl。18dp のアイコンは iconMedium。空の状態の見出しは titleMedium） | 全画面 |
| N9 | 左右の余白 | S02 と編集画面は 16（screenEdgeList） | S02、S04 |
| N10 | 実行中の応援文 | ミッションのひとこと（note）。空なら出さない。白い面の上の中央揃えの説明文は onSurfaceVariant | S05、S07 |
| N11 | 「このまま待つ」 | ボタンを置かない（解除待ちは閉じられないので） | S05-2、S07 |
| N12 | 完了報告 | やることはラジオの3択と「報告」。ご褒美は選ぶ段階を省き「できた」で記録 | S06 |
| N13 | 単発ガチャの結果 | ナビを出さない全画面（どの操作でも実行画面へ） | S03 |
| N14 | 10連の初期選択 | 選べる行のうちレア度のいちばん高い行を選んでおく | S03-2 |
| N15 | 設定の項目 | 「ホーム・スワイプのご褒美」0〜3回、1日の目標 10〜120分の10分刻み、時間の Slider は5分刻み、「ヘルプ」の行（医療機関・相談窓口の案内） | S04 |
| N16 | 連絡アプリの判定 | パッケージ名の一覧（LINE、WhatsApp、Messenger、Telegram、Discord、Slack、Signal、Google メッセージ、Viber、KakaoTalk、Teams、Gmail、Outlook、Skype、Google Chat）で判定する（designer は判断を保留。実装の都合で仮に決めた） | S04 |
| N17 | ロック画面 | 見出しは「ゲームAはロック中です」（アプリ名を入れる）、タイルは本物のアプリのアイコン、チップは所持コインだけ | S07 |
| N18 | 初回設定のあと | ユーザー補助がオンになっていれば常時ロックを自動でオンにする | S08 |
| N19 | ご褒美ミッションを強制解除したときのコイン | 戻らない（「使ったコインは戻りません」と書く）（designer は判断を保留。仮に決めた） | S05、S05-2 |
| R1 | 10連のご褒美の行の「今日あと○回」 | そのご褒美のガチャでの1日の上限の残り | S03-2 |
| R2 | スワイプで押せない理由 | チップには短い理由（「あと3,000コイン」「今日の枠は使い切りました」「静かな時間帯です」「次はやることから」）、全文は Snackbar | S02 |
| R3 | 見出しの大きさのコイン（S06 の「+2,500コイン」） | 今は headlineMedium のまま（tnum の拡張スタイルは未対応） | S06 |
| R4 | ナビゲーションバーのガチャのアイコン | casino（6章に記録） | 全タブ |
| R5 | 8章にない部品 | SegmentedButton（レア度・出やすさ・ホーム・スワイプのご褒美の回数）、FilterChip（カテゴリ・時間帯）、Slider、AlertDialog の中の TimePicker、OutlinedTextField、Checkbox（対象アプリ）、S05 の対象アプリを選ぶボトムシート（ハンドルは outlineVariant）を M3 の標準のまま使う | S04、S05 |
