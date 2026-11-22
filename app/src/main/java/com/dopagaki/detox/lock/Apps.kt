package com.dopagaki.detox.lock

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import android.accessibilityservice.AccessibilityServiceInfo
import com.dopagaki.detox.domain.lock.HomeEventRule

/** 対象アプリを選ぶ一覧の1行。 */
data class InstalledApp(val packageName: String, val label: String, val isMessaging: Boolean)

/**
 * 除外アプリ（設計書 6.2、9.3）：既定の電話・SMS・ホーム、端末の設定、システムUI、キーボード（IME）、ドパガキデトックス自体。
 * 対象アプリの一覧は ACTION_MAIN＋CATEGORY_LAUNCHER の &lt;queries&gt; で取る（QUERY_ALL_PACKAGES は使わない。9.2）。
 */
object Apps {
    private const val SYSTEM_UI = "com.android.systemui"

    /**
     * 連絡に使うアプリ（選ぶときに「ロック中は連絡を受け取れても開けません」と警告する。6.2）。
     * パッケージ名で判定する。一覧にない連絡アプリは警告しない。
     */
    private val MESSAGING = setOf(
        "jp.naver.line.android", "com.whatsapp", "com.facebook.orca", "org.telegram.messenger",
        "com.discord", "com.Slack", "org.thoughtcrime.securesms", "com.google.android.apps.messaging",
        "com.viber.voip", "com.kakao.talk", "com.microsoft.teams", "com.google.android.gm",
        "com.microsoft.office.outlook", "com.skype.raider", "com.google.android.apps.dynamite",
    )

    fun excluded(context: Context): Set<String> =
        nonHomeExcluded(context) + SYSTEM_UI + homeCapablePackages(context) + imePackages(context)

    /** 除外アプリのうち、ホームのイベントとして扱ってはいけないもの：自分自身、既定の電話・SMS、端末の設定（9.3）。 */
    fun nonHomeExcluded(context: Context): Set<String> {
        val result = mutableSetOf(context.packageName)
        context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let(result::add)
        Telephony.Sms.getDefaultSmsPackage(context)?.let(result::add)
        resolvePackage(context.packageManager, Intent(Settings.ACTION_SETTINGS))?.let(result::add)
        return result
    }

    /** ホームになれるアプリ（既定のホームが決まっていない端末もあるので、なれるものをすべて）。設定アプリも含みうる。 */
    private fun homeCapablePackages(context: Context): Set<String> =
        context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { it.activityInfo.packageName }
            .filter { it != "android" }
            .toSet()

    /** ホームのイベントとして扱うパッケージ（6.2、9.3）。 */
    fun homePackages(context: Context): Set<String> =
        HomeEventRule.homes(homeCapablePackages(context), nonHomeExcluded(context))

    fun imePackages(context: Context): Set<String> =
        context.getSystemService(InputMethodManager::class.java)?.enabledInputMethodList
            ?.map { it.packageName }?.toSet() ?: emptySet()

    private fun resolvePackage(pm: PackageManager, intent: Intent): String? =
        pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            ?.takeIf { it != "android" }

    /** ランチャーに出るアプリから、除外アプリを除いた一覧（名前順）。 */
    fun launchable(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val excluded = excluded(context)
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName !in excluded }
            .map { InstalledApp(it.packageName, it.loadLabel(pm).toString(), it.packageName in MESSAGING) }
            .sortedBy { it.label }
    }

    fun label(context: Context, packageName: String): String = try {
        val pm = context.packageManager
        pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        packageName
    }

    fun icon(context: Context, packageName: String): Drawable? = try {
        context.packageManager.getApplicationIcon(packageName)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    fun isMessaging(packageName: String): Boolean = packageName in MESSAGING

    /** このアプリのユーザー補助サービスがオンか。 */
    fun isAccessibilityEnabled(context: Context): Boolean {
        val am = context.getSystemService(AccessibilityManager::class.java) ?: return false
        val mine = ComponentName(context, LockAccessibilityService::class.java)
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.let { s -> ComponentName(s.packageName, s.name) } == mine }
    }

    /** 端末のユーザー補助の設定を開く（開示と同意のあとだけ。9.2）。 */
    fun accessibilitySettingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
