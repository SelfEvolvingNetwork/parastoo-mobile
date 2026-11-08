package network.selfevolving.parastoo.mobile.core.model

import org.json.JSONObject

/** پروفایل سرور — هیچ رازی اینجا نیست؛ توکن ادمین فقط در SecretVault رمزنگاری می‌شود */
data class ServerProfile(
    val id: Long,
    val name: String,
    val url: String,
    val certSha256: String,
    val createdAtMs: Long,
)

data class HostStatus(
    val ok: Boolean,
    val version: String,
    val uptimeS: Long,
    val hostname: String,
    val kernel: String,
    val diskPercent: Double?,
    val diskFreeGb: Double?,
    val memTotalMb: Long?,
    val memAvailMb: Long?,
    val parastooInstallPath: String,
    val githubTokenStored: Boolean,
    val agentRealmEnabled: Boolean,
    val agentRealmPaused: Boolean,
) {
    companion object {
        fun from(o: JSONObject): HostStatus = HostStatus(
            ok = o.optBoolean("ok", false),
            version = o.optString("version"),
            uptimeS = o.optLong("uptime_s", 0),
            hostname = o.optString("hostname"),
            kernel = o.optString("kernel"),
            diskPercent = if (o.has("disk_percent") && !o.isNull("disk_percent")) o.getDouble("disk_percent") else null,
            diskFreeGb = if (o.has("disk_free_gb") && !o.isNull("disk_free_gb")) o.getDouble("disk_free_gb") else null,
            memTotalMb = if (o.has("mem_total_mb") && !o.isNull("mem_total_mb")) o.getLong("mem_total_mb") else null,
            memAvailMb = if (o.has("mem_avail_mb") && !o.isNull("mem_avail_mb")) o.getLong("mem_avail_mb") else null,
            parastooInstallPath = o.optString("parastoo_install_path"),
            githubTokenStored = o.optBoolean("github_token_stored", false),
            agentRealmEnabled = o.optBoolean("agent_realm_enabled", false),
            agentRealmPaused = o.optBoolean("agent_realm_paused", false),
        )
    }
}

data class AgentTokenMeta(
    val set: Boolean,
    val label: String,
    val rotatedAt: String,
) {
    companion object {
        fun from(o: JSONObject): AgentTokenMeta {
            val meta = o.optJSONObject("meta") ?: JSONObject()
            return AgentTokenMeta(
                set = o.optBoolean("set", false),
                label = meta.optString("label"),
                rotatedAt = meta.optString("rotated_at"),
            )
        }
    }
}

/** یک رویداد audit — همان خط JSONL پل؛ اپ فقط نمایش می‌دهد */
data class AuditEvent(
    val ts: String,
    val ip: String,
    val actor: String,
    val action: String,
    val exitCode: Int?,
    val timedOut: Boolean,
    val durationMs: Long?,
    val cmdPreview: String,
    val stdoutPreview: String,
    val stderrPreview: String,
    val error: String,
    val raw: String,
) {
    val isExec: Boolean get() = action == "exec" || action == "exec_rejected"
    val isError: Boolean
        get() = action.endsWith("_fail") || action.endsWith("_forbidden") ||
            action == "exec_rejected" || (exitCode != null && exitCode != 0) || timedOut

    val category: String
        get() = when {
            isExec -> "exec"
            action.startsWith("admin_") -> "admin"
            action.contains("github") || action == "disclose" -> "github"
            action.startsWith("auth") -> "auth"
            else -> "other"
        }

    /** عنوان فارسی دوستانه — قرارداد مالک: کاربر نباید متن خام فنی بخواند */
    val friendlyTitle: String
        get() = when {
            action == "status" -> "بررسی وضعیت"
            action == "exec" -> "اجرای فرمان روی سرور"
            action == "exec_rejected" -> "فرمانِ ردشده"
            action == "auth_fail" -> "تلاش ورودِ ردشده"
            action.endsWith("_forbidden") -> "درخواستِ خارج از صلاحیت"
            action.endsWith("_fail") -> "اقدام ناموفق"
            action == "admin_audit_stream" -> "جریان زندهٔ رویدادها"
            action == "admin_parastoo_status" -> "وضعیت پرستو"
            action.startsWith("admin_parastoo_") -> {
                val op = action.removePrefix("admin_parastoo_").substringBefore('_')
                "پرستو: " + when (op) {
                    "install" -> "نصب"
                    "uninstall" -> "حذف کامل"
                    "reinstall" -> "نصب مجدد"
                    "update" -> "به‌روزرسانی"
                    else -> op
                }
            }
            action.startsWith("admin_") -> "مدیریت: " + action.removePrefix("admin_").replace('_', ' ')
            action == "disclose" -> "درخواست توکن گیت"
            else -> action
        }

    /** چرا قرمز است؟ یک جملهٔ آرام‌بخش/توضیحی — فقط برای رویدادهای خطا */
    val friendlyReason: String?
        get() = when {
            !isError -> null
            timedOut -> "فرمان بیش از حد طول کشید و قطع شد"
            action == "auth_fail" || action.endsWith("_forbidden") || action == "exec_rejected" ->
                "پل جلوی آن ایستاد — خطری وارد نشد"
            exitCode != null && exitCode != 0 -> "فرمان با کد خطای $exitCode تمام شد (عادی در کار توسعه)"
            else -> "خطای گذرا — در صورت تکرار بررسی شود"
        }

    /** نمایش دوستانهٔ بازیگر رویداد */
    val friendlyActor: String
        get() = when (actor) {
            "admin" -> "ادمین (اپ شما)"
            "agent" -> "عامل توسعه"
            "anon" -> "ناشناس"
            "" -> "نامشخص"
            else -> actor
        }

    companion object {
        fun from(line: String): AuditEvent? = try {
            val o = JSONObject(line)
            AuditEvent(
                ts = o.optString("ts"),
                ip = o.optString("ip"),
                actor = o.optString("actor"),
                action = o.optString("action"),
                exitCode = if (o.has("exit_code") && !o.isNull("exit_code")) o.getInt("exit_code") else null,
                timedOut = o.optBoolean("timed_out", false),
                durationMs = if (o.has("duration_ms") && !o.isNull("duration_ms")) o.getLong("duration_ms") else null,
                cmdPreview = o.optString("cmd_preview"),
                stdoutPreview = o.optString("stdout_preview"),
                stderrPreview = o.optString("stderr_preview"),
                error = o.optString("error"),
                raw = line,
            )
        } catch (e: Exception) {
            null
        }
    }
}

/** وضعیت چرخهٔ عمر پرستو روی سرور (پل v0.5) — نصب/حذف/به‌روزرسانی از موبایل */
data class LifecycleStatus(
    val installed: Boolean,
    val installPath: String,
    val version: String,
    val busy: Boolean,
    val action: String,
    val exitCode: Int?,
    val okLast: Boolean?,
    val durationS: Long?,
    val logTail: List<String>,
) {
    val logText: String get() = logTail.joinToString("\n")

    companion object {
        fun from(o: JSONObject): LifecycleStatus = LifecycleStatus(
            installed = o.optBoolean("installed", false),
            installPath = o.optString("install_path"),
            version = o.optString("version"),
            busy = o.optBoolean("busy", false),
            action = o.optString("action"),
            exitCode = if (o.has("exit_code") && !o.isNull("exit_code")) o.getInt("exit_code") else null,
            okLast = if (o.has("ok_last") && !o.isNull("ok_last")) o.getBoolean("ok_last") else null,
            durationS = if (o.has("duration_s") && !o.isNull("duration_s")) o.getLong("duration_s") else null,
            logTail = buildList {
                val arr = o.optJSONArray("log_tail") ?: return@buildList
                for (i in 0 until arr.length()) add(arr.optString(i))
            },
        )
    }
}
