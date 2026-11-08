package com.dopagaki.detox.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.dopagaki.detox.R
import com.dopagaki.detox.domain.Category

/** カテゴリのアイコンと表示名（docs/design-system.md 2章・6章、仮決定 N1）。 */
@get:DrawableRes
val Category.iconRes: Int
    get() = when (this) {
        Category.READING -> R.drawable.ic_menu_book
        Category.EXERCISE -> R.drawable.ic_fitness_center
        Category.STUDY -> R.drawable.ic_edit
        Category.CHORES -> R.drawable.ic_cleaning_services
        Category.MIND -> R.drawable.ic_self_improvement
        Category.GAME -> R.drawable.ic_sports_esports
        Category.SNS -> R.drawable.ic_forum
    }

/** 一覧での表示名。ご褒美は game・sns を問わず「ご褒美」。 */
@get:StringRes
val Category.labelRes: Int
    get() = when (this) {
        Category.READING -> R.string.category_reading
        Category.EXERCISE -> R.string.category_exercise
        Category.STUDY -> R.string.category_study
        Category.CHORES -> R.string.category_chores
        Category.MIND -> R.string.category_mind
        Category.GAME, Category.SNS -> R.string.category_reward
    }

/** 編集画面のカテゴリの選択での名前（ゲームと SNS を選び分ける）。 */
@get:StringRes
val Category.editorLabelRes: Int
    get() = when (this) {
        Category.GAME -> R.string.category_game
        Category.SNS -> R.string.category_sns
        else -> labelRes
    }

/** 設定の行の丸の中の1文字。 */
@get:StringRes
val Category.letterRes: Int
    get() = when (this) {
        Category.READING -> R.string.category_letter_reading
        Category.EXERCISE -> R.string.category_letter_exercise
        Category.STUDY -> R.string.category_letter_study
        Category.CHORES -> R.string.category_letter_chores
        Category.MIND -> R.string.category_letter_mind
        Category.GAME, Category.SNS -> R.string.category_letter_reward
    }

val TASK_CATEGORIES = listOf(Category.READING, Category.EXERCISE, Category.STUDY, Category.CHORES, Category.MIND)
val REWARD_CATEGORIES = listOf(Category.GAME, Category.SNS)
