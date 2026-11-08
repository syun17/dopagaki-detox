package com.dopagaki.detox.domain

import com.dopagaki.detox.domain.model.Mission
import com.dopagaki.detox.domain.model.MissionKind
import com.dopagaki.detox.domain.model.TimeTag
import java.time.Instant
import java.time.ZoneId

val TOKYO: ZoneId = ZoneId.of("Asia/Tokyo")

/** 東京の日時を Instant にする。 */
fun tokyo(text: String): Instant = java.time.LocalDateTime.parse(text).atZone(TOKYO).toInstant()

fun task(
    id: String,
    min: Int = 20,
    rarity: Rarity = Rarity.R,
    weight: Int = 3,
    tags: Set<TimeTag> = emptySet(),
    enabled: Boolean = true,
    lastDoneAt: Instant? = null,
) = Mission(id, id, MissionKind.TASK, Category.READING, min, rarity, weight, null, emptySet(), tags, enabled, lastDoneAt)

fun reward(
    id: String,
    min: Int = 20,
    rarity: Rarity = Rarity.SSR,
    weight: Int = 3,
    dailyLimit: Int = 2,
    apps: Set<String> = setOf("com.example.game"),
    tags: Set<TimeTag> = emptySet(),
    enabled: Boolean = true,
) = Mission(id, id, MissionKind.REWARD, Category.GAME, min, rarity, weight, dailyLimit, apps, tags, enabled, null)
