package com.dopagaki.detox.domain

import java.time.Duration
import java.time.LocalTime

/**
 * 設計書 8章「定数（MVPでは設定画面に出さない）」と、7章・5.4 の数値。
 * 値の正本は設計書。ここを変えるときは先に設計書を直す（/update-spec）。
 * 出現率・倍率・コイン・時間をほかの場所に直書きしない。
 */
object Rules {
    /** ガチャでのレア度の出現率（%）。7.1 */
    val rarityRatePercent: Map<Rarity, Int> = mapOf(
        Rarity.N to 50,
        Rarity.R to 35,
        Rarity.SR to 12,
        Rarity.SSR to 3,
    )

    /**
     * コインの倍率（%。125 = 1.25倍）。やることの獲得コインとご褒美の必要コインの両方にかける。7.1、7.5
     * 浮動小数の誤差を出さないよう整数で持つ。コイン = 単価 × 分 × 倍率% / 100（整数演算、切り捨て）
     */
    val rarityMultiplierPercent: Map<Rarity, Int> = mapOf(
        Rarity.N to 100,
        Rarity.R to 125,
        Rarity.SR to 150,
        Rarity.SSR to 200,
    )

    /** 種類ごとに選べるレア度。5.5、8章 Mission */
    val TASK_RARITIES: Set<Rarity> = setOf(Rarity.N, Rarity.R, Rarity.SR)
    val REWARD_RARITIES: Set<Rarity> = setOf(Rarity.SR, Rarity.SSR)

    /**
     * 時間からレア度の初期値を入れるときの境界（分）。5.5
     * やること：5〜14分は N、15〜29分は R、30分以上は SR／ご褒美：15分以下は SR、16分以上は SSR
     */
    const val TASK_R_MIN_DURATION_MIN = 15
    const val TASK_SR_MIN_DURATION_MIN = 30
    const val REWARD_SSR_MIN_DURATION_MIN = 16

    /** やること1分あたりの獲得コイン。7.5 */
    const val TASK_COIN_PER_MIN = 100

    /** ご褒美1分あたりの必要コイン。7.5 */
    const val REWARD_COIN_PER_MIN = 300

    /** ガチャ単発の価格。7.5 */
    const val GACHA_SINGLE_COST = 600

    /** ガチャ10連の価格。7.5 */
    const val GACHA_TEN_COST = 4_500

    /** 10連で出る件数。5.3 */
    const val GACHA_TEN_COUNT = 10

    /** 天井：この回数だけ続けて「できた」で完了すると次のガチャはご褒美確定。7.3 */
    const val PITY_THRESHOLD = 5

    /** 天井で数えるやることの最短時間（分）。7.3 */
    const val PITY_MIN_DURATION_MIN = 15

    /** 連続日数ボーナス：日数 × この値（その日最初の完了時）。7.5 */
    const val STREAK_BONUS_PER_DAY = 100

    /** 連続日数ボーナスの上限。7.5 */
    const val STREAK_BONUS_MAX = 1_000

    /** 初回起動ボーナス。7.5 */
    const val FIRST_LAUNCH_BONUS = 3_000

    /** 1回の更新で出すおすすめの件数（ホーム・スワイプ）。5.1、5.2、7.4 */
    const val FEED_SIZE = 20

    /** 重み w = p × (1 + DAY_FACTOR × min(d, DAYS_CAP)) × c。7.2 */
    const val WEIGHT_DAY_FACTOR = 0.1
    const val WEIGHT_DAYS_CAP = 7

    /** 直前補正 c：直前と同じミッション。ご褒美直後のご褒美は 0。7.2 */
    const val WEIGHT_SAME_AS_PREVIOUS = 0.2
    const val WEIGHT_REWARD_AFTER_REWARD = 0.0

    /** 一時停止の回数と最大時間。5.4 */
    const val PAUSE_MAX_COUNT = 1
    val PAUSE_MAX: Duration = Duration.ofMinutes(5)

    /** 強制解除のボタンを出す残り時間の下限（この時間以上なら出す）。5.4 */
    val FORCE_UNLOCK_MIN_REMAINING: Duration = Duration.ofMinutes(10)

    /** やること・ご褒美の時間（分）と出やすさの範囲。5.5、8章 Mission */
    val TASK_DURATION_MIN_RANGE = 5..120
    val REWARD_DURATION_MIN_RANGE = 5..60
    val WEIGHT_RANGE = 1..5

    /** ご褒美のガチャでの1日上限の初期値。7.3 */
    const val REWARD_DAILY_LIMIT_DEFAULT = 2

    /** ご褒美ミッションの終了を知らせる、終了前の時間。6.2 */
    val REWARD_END_NOTICE_BEFORE: Duration = Duration.ofMinutes(1)

    /** 信頼時刻と起動回数・elapsedRealtime を保存する間隔（操作のたびにも保存する）。6.4 */
    val TRUSTED_CLOCK_SAVE_INTERVAL: Duration = Duration.ofMinutes(1)

    /** 対象アプリが前面にある間、ご褒美の時間切れを判定する間隔。9.3 */
    val REWARD_EXPIRY_CHECK_INTERVAL: Duration = Duration.ofSeconds(1)
}

/**
 * 設定（8章 Settings）の初期値のうち、設計書で値が決まっているもの。
 * 常時ロック・強制解除・一時停止の可否の初期値は設計書で未定のため、ここには置かない。
 */
object SettingsDefaults {
    /** 強制解除・有利な設定変更の待ち時間（unlockWaitMin）。5.6、6.3 */
    val UNLOCK_WAIT: Duration = Duration.ofMinutes(10)

    /** 常時ロックを強制解除したあと対象アプリを開けておく時間（unlockGraceMin）。6.3 */
    val UNLOCK_GRACE: Duration = Duration.ofMinutes(30)

    /** 静かな時間帯（quietStart〜quietEnd）。日をまたぐ。7.3 */
    val QUIET_START: LocalTime = LocalTime.of(23, 0)
    val QUIET_END: LocalTime = LocalTime.of(6, 0)

    /** ホーム・スワイプでのご褒美の出現率の倍率（homeRewardRatio）。7.4 */
    const val HOME_REWARD_RATIO = 0.1

    /** ホーム・スワイプ枠：1日に始められるご褒美の回数（feedRewardDailyLimit）。7.4 */
    const val FEED_REWARD_DAILY_LIMIT = 1

    /** 更新回数の上限と数える時間（refreshLimit、refreshWindowMin）。7.4 */
    const val REFRESH_LIMIT = 3
    val REFRESH_WINDOW: Duration = Duration.ofMinutes(60)

    /** 1日の切り替えの時刻（dayStartHour）。6.4 */
    const val DAY_START_HOUR = 4
}
