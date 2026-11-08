package network.selfevolving.parastoo.mobile.core.security

import java.net.InetAddress
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * پین گواهی خودامضا — قرارداد access-agent (AGENT_PROTOCOL.fa.md §۲):
 * «اعتبارسنجی یعنی هش گواهی دریافتی == هش اعلامی مالک».
 *
 * کل زنجیرهٔ CA و hostname نادیده گرفته می‌شود و فقط یک چیز حاکم است:
 * SHA-256 گواهی برگ باید دقیقاً با اثرانگشتی که ادمین هنگام افزودن سرور وارد
 * کرده برابر باشد. این همان مدل اعتماد نصب access-agent است — نه اعتماد کورکورانه.
 */
object PinnedTrust {

    fun normalizeFingerprint(input: String): String =
        input.replace(":", "").replace(" ", "").replace("\n", "").uppercase()

    fun isValidFingerprint(input: String): Boolean {
        val s = normalizeFingerprint(input)
        return s.length == 64 && s.all { it.isDigit() || it in 'A'..'F' }
    }

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02X".format(it) }

    fun buildClient(baseUrl: String, certSha256: String): OkHttpClient {
        val pin = normalizeFingerprint(certSha256)
        require(isValidFingerprint(pin)) { "CERT_SHA256 نامعتبر است (۶۴ کاراکتر hex لازم است)" }

        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                // سمت سرور برای ما مهم است؛ سمت کلاینت گواهی نمی‌فرستیم
            }

            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                val leaf = chain.firstOrNull()
                    ?: throw javax.net.ssl.SSLPeerUnverifiedException("زنجیرهٔ گواهی خالی بود")
                val got = sha256Hex(leaf.encoded)
                if (got != pin) {
                    throw javax.net.ssl.SSLPeerUnverifiedException(
                        "پین گواهی رد شد: انتظار ${pin.take(16)}…، دریافتی ${got.take(16)}…"
                    )
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }

        val sslCtx = SSLContext.getInstance("TLS")
        sslCtx.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())

        return OkHttpClient.Builder()
            .sslSocketFactory(sslCtx.socketFactory, trustManager)
            // اعتماد فقط از پین گواهی می‌آید؛ هاست معمولاً IP خام است (قرارداد پل)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * واکشی خودکار اثرانگشت — مدل SSH/TOFU (دستور مالک: «باید خودش بسازد،
     * از ادمین نخواهد»): اولین اتصال، گواهی برگ سرور را می‌گیرد و SHA-256 آن
     * را برمی‌گرداند. ادمین هیچ چیزی تایپ نمی‌کند؛ همین مقدار یک بار به‌عنوان
     * پین ثبت می‌شود و از آن پس هر تغییر گواهی، اتصال را رد می‌کند
     * (همان رفتار known_hosts در SSH).
     */
    fun fetchFingerprint(baseUrl: String): String {
        var leaf: X509Certificate? = null
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}

            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                leaf = chain.firstOrNull()
                    ?: throw javax.net.ssl.SSLPeerUnverifiedException("زنجیرهٔ گواهی خالی بود")
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val sslCtx = SSLContext.getInstance("TLS")
        sslCtx.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
        val client = OkHttpClient.Builder()
            .sslSocketFactory(sslCtx.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
        val url = baseUrl.trim().trimEnd('/') + "/healthz"
        // هدف فقط تکمیل دست‌دادن TLS و گرفتن گواهی است؛ کد وضعیت HTTP مهم نیست
        client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
            resp.body?.string()
        }
        val cert = leaf ?: throw IllegalStateException("گواهی سرور دریافت نشد")
        return sha256Hex(cert.encoded)
    }
}
