package com.dopagaki.detox.domain.lock

/** ホームのイベントが来たときにすること（6.2、9.3）。 */
enum class HomeEventAction {
    /** ふつうに前面のアプリとして扱う。 */
    NORMAL,

    /** 無視する（前面のアプリもロック画面も変えない）。 */
    IGNORE,

    /** 端末のホーム操作を送ってからロック画面を消す。 */
    SEND_HOME_AND_HIDE,
}

/**
 * ホームのイベントの扱い。重いアプリを開くとホームのイベントが対象アプリのイベントより遅れて届き、
 * 実際には対象アプリが前面のままのことがある（6.2）。
 * - ロック画面を出している間：ホーム操作を送って対象アプリを確実に裏へ回してから消す
 *   ただし画面の固定中はホーム操作が効かないので、消さずに出し続ける
 * - 時間切れのロック画面を見せている間：決めた時間（Rules.EXPIRED_LOCK_SHOW）は見せる
 * - ご褒美中に時間切れを判定している間：判定を止めない
 */
object HomeEventRule {
    fun decide(overlayShown: Boolean, showingExpired: Boolean, watchingExpiry: Boolean, pinned: Boolean): HomeEventAction = when {
        showingExpired -> HomeEventAction.IGNORE
        overlayShown && pinned -> HomeEventAction.IGNORE
        overlayShown -> HomeEventAction.SEND_HOME_AND_HIDE
        watchingExpiry -> HomeEventAction.IGNORE
        else -> HomeEventAction.NORMAL
    }

    /** ホームとして扱うパッケージ。設定アプリは起動時用のホームの画面を持つので、ほかの除外アプリは除く（9.3）。 */
    fun homes(homeCapable: Set<String>, nonHomeExcluded: Set<String>): Set<String> = homeCapable - nonHomeExcluded
}
