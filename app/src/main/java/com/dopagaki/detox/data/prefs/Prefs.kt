package com.dopagaki.detox.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dopagaki.detox.domain.model.Settings
import kotlinx.coroutines.flow.first
import java.time.LocalTime

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
private val Context.metaStore: DataStore<Preferences> by preferencesDataStore(name = "meta")

/**
 * リセットしても消さない記録（設計書 5.6、8章「初回の記録」）。
 * a11yConsent：初回設定（S08）でユーザー補助の利用に同意したか（null は未回答）。
 */
data class Meta(
    val firstLaunchBonusGiven: Boolean = false,
    val onboardingDone: Boolean = false,
    val a11yConsent: Boolean? = null,
)

class PrefsStore(private val context: Context) {
    private object K {
        val rewardAppLock = booleanPreferencesKey("rewardAppLock")
        val allowForceUnlock = booleanPreferencesKey("allowForceUnlock")
        val allowPause = booleanPreferencesKey("allowPause")
        val quietEnabled = booleanPreferencesKey("quietEnabled")
        val quietStart = stringPreferencesKey("quietStart")
        val quietEnd = stringPreferencesKey("quietEnd")
        val dailyGoalMin = intPreferencesKey("dailyGoalMin")
        val feedRewardDailyLimit = intPreferencesKey("feedRewardDailyLimit")

        val firstLaunchBonusGiven = booleanPreferencesKey("firstLaunchBonusGiven")
        val onboardingDone = booleanPreferencesKey("onboardingDone")
        val a11yConsent = stringPreferencesKey("a11yConsent")
    }

    suspend fun loadSettings(): Settings {
        val p = context.settingsStore.data.first()
        val d = Settings()
        return Settings(
            rewardAppLock = p[K.rewardAppLock] ?: d.rewardAppLock,
            allowForceUnlock = p[K.allowForceUnlock] ?: d.allowForceUnlock,
            allowPause = p[K.allowPause] ?: d.allowPause,
            quietEnabled = p[K.quietEnabled] ?: d.quietEnabled,
            quietStart = p[K.quietStart]?.let(LocalTime::parse) ?: d.quietStart,
            quietEnd = p[K.quietEnd]?.let(LocalTime::parse) ?: d.quietEnd,
            dailyGoalMin = p[K.dailyGoalMin] ?: d.dailyGoalMin,
            feedRewardDailyLimit = p[K.feedRewardDailyLimit] ?: d.feedRewardDailyLimit,
        )
    }

    suspend fun saveSettings(s: Settings) {
        context.settingsStore.edit { p ->
            p[K.rewardAppLock] = s.rewardAppLock
            p[K.allowForceUnlock] = s.allowForceUnlock
            p[K.allowPause] = s.allowPause
            p[K.quietEnabled] = s.quietEnabled
            p[K.quietStart] = s.quietStart.toString()
            p[K.quietEnd] = s.quietEnd.toString()
            p[K.dailyGoalMin] = s.dailyGoalMin
            p[K.feedRewardDailyLimit] = s.feedRewardDailyLimit
        }
    }

    suspend fun loadMeta(): Meta {
        val p = context.metaStore.data.first()
        return Meta(
            firstLaunchBonusGiven = p[K.firstLaunchBonusGiven] ?: false,
            onboardingDone = p[K.onboardingDone] ?: false,
            a11yConsent = p[K.a11yConsent]?.toBooleanStrictOrNull(),
        )
    }

    suspend fun saveMeta(m: Meta) {
        context.metaStore.edit { p ->
            p[K.firstLaunchBonusGiven] = m.firstLaunchBonusGiven
            p[K.onboardingDone] = m.onboardingDone
            if (m.a11yConsent == null) p.remove(K.a11yConsent) else p[K.a11yConsent] = m.a11yConsent.toString()
        }
    }
}
