package com.dopagaki.detox.domain

/**
 * ミッションのカテゴリ（設計書 8章 Mission.category）。
 * やることかご褒美かはカテゴリではなく Mission.kind で判定する（カテゴリから種類を決めない）。
 */
enum class Category { READING, EXERCISE, STUDY, CHORES, MIND, GAME, SNS }
