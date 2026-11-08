package com.dopagaki.detox.domain.preset

import com.dopagaki.detox.domain.Category
import com.dopagaki.detox.domain.Rarity
import com.dopagaki.detox.domain.Rules
import com.dopagaki.detox.domain.model.Mission
import com.dopagaki.detox.domain.model.MissionKind
import com.dopagaki.detox.domain.model.TimeTag

/** 初回起動時に入れるミッション（設計書 8章「プリセット」）。ご褒美の対象アプリは空。 */
object Presets {
    fun missions(newId: () -> String): List<Mission> = listOf(
        task(newId(), "机の上を片付ける", Category.CHORES, 5, Rarity.N, setOf(TimeTag.MORNING, TimeTag.NIGHT), "目に入るところから5分だけ。"),
        task(newId(), "肩と首のストレッチ", Category.EXERCISE, 10, Rarity.N, setOf(TimeTag.DAY, TimeTag.NIGHT), "ゆっくり伸ばそう。"),
        task(newId(), "紙の本を読む", Category.READING, 20, Rarity.R, setOf(TimeTag.DAY, TimeTag.NIGHT), "紙の本で目を休めよう。"),
        task(newId(), "英単語を覚える", Category.STUDY, 15, Rarity.R, setOf(TimeTag.MORNING), "少しずつ毎日。"),
        task(newId(), "英語の長文を読む", Category.STUDY, 40, Rarity.SR, setOf(TimeTag.DAY), "じっくり1本読み切ろう。"),
        reward(newId(), "SNSを見る", Category.SNS, 10, Rarity.SR, setOf(TimeTag.DAY, TimeTag.NIGHT), "時間を決めて楽しもう。"),
        reward(newId(), "好きなゲーム", Category.GAME, 20, Rarity.SSR, setOf(TimeTag.NIGHT), "思い切り楽しもう。"),
    )

    private fun task(id: String, title: String, c: Category, min: Int, r: Rarity, tags: Set<TimeTag>, note: String) =
        Mission(id, title, MissionKind.TASK, c, min, r, Rules.PRESET_WEIGHT, null, emptySet(), tags, true, null, note)

    private fun reward(id: String, title: String, c: Category, min: Int, r: Rarity, tags: Set<TimeTag>, note: String) =
        Mission(id, title, MissionKind.REWARD, c, min, r, Rules.PRESET_WEIGHT, Rules.REWARD_DAILY_LIMIT_DEFAULT, emptySet(), tags, true, null, note)

    /** 時間からレア度の初期値を入れる（5.5）。 */
    fun defaultRarity(kind: MissionKind, durationMin: Int): Rarity = when (kind) {
        MissionKind.TASK -> when {
            durationMin >= Rules.TASK_SR_MIN_DURATION_MIN -> Rarity.SR
            durationMin >= Rules.TASK_R_MIN_DURATION_MIN -> Rarity.R
            else -> Rarity.N
        }
        MissionKind.REWARD -> if (durationMin >= Rules.REWARD_SSR_MIN_DURATION_MIN) Rarity.SSR else Rarity.SR
    }
}
