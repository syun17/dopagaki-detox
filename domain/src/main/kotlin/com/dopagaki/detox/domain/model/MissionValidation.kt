package com.dopagaki.detox.domain.model

import com.dopagaki.detox.domain.Rules

/** ミッションの入力の誤り（5.5、8章 Mission）。 */
enum class MissionError { TITLE_EMPTY, DURATION_OUT_OF_RANGE, WEIGHT_OUT_OF_RANGE, RARITY_NOT_ALLOWED, DAILY_LIMIT_OUT_OF_RANGE, TASK_HAS_REWARD_FIELDS }

object MissionValidation {
    fun errors(m: Mission): Set<MissionError> {
        val e = mutableSetOf<MissionError>()
        if (m.title.isBlank()) e += MissionError.TITLE_EMPTY
        if (m.weight !in Rules.WEIGHT_RANGE) e += MissionError.WEIGHT_OUT_OF_RANGE
        when (m.kind) {
            MissionKind.TASK -> {
                if (m.durationMin !in Rules.TASK_DURATION_MIN_RANGE) e += MissionError.DURATION_OUT_OF_RANGE
                if (m.rarity !in Rules.TASK_RARITIES) e += MissionError.RARITY_NOT_ALLOWED
                if (m.dailyLimit != null || m.linkedApps.isNotEmpty()) e += MissionError.TASK_HAS_REWARD_FIELDS
            }
            MissionKind.REWARD -> {
                if (m.durationMin !in Rules.REWARD_DURATION_MIN_RANGE) e += MissionError.DURATION_OUT_OF_RANGE
                if (m.rarity !in Rules.REWARD_RARITIES) e += MissionError.RARITY_NOT_ALLOWED
                if (m.dailyLimit == null || m.dailyLimit !in Rules.REWARD_DAILY_LIMIT_RANGE) e += MissionError.DAILY_LIMIT_OUT_OF_RANGE
            }
        }
        return e
    }

    fun isValid(m: Mission): Boolean = errors(m).isEmpty()
}
