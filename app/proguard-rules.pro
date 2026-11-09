# OkHttp / Conscrypt
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
# kotlinx/annotations
-dontwarn org.jetbrains.annotations.**
# BouncyCastle برای sshj لازم است (نصب پل از داخل اپ) — نگه‌داری کامل
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
# sshj + asn-one + eddsa — کلاس‌های الگوی کارخانه‌ای/بازتابی‌شان نباید حذف شوند
-keep class net.schmizz.sshj.** { *; }
-keep class com.hierynomus.** { *; }
-keep class net.i2p.crypto.eddsa.** { *; }
-dontwarn net.schmizz.sshj.**
-dontwarn com.hierynomus.**
-dontwarn net.i2p.crypto.eddsa.**
-dontwarn org.slf4j.**
-dontwarn java.nio.**

# ===== معماری بدنه ثابت + هسته (v0.5.0) =====
# کلاس‌های قرارداد و بنیاد باید با نام اصلی بمانند: هستهٔ خارجی (dex از کانال
# انتشار) در زمان کامپایل همین نام‌ها را می‌بیند و در runtime از کلاس‌لودر
# والد به آن‌ها می‌رسد. بیلدهای منتشرشده debug هستند (بدون R8)؛ این قواعد
# محافظِ بیلدهای release آینده‌اند.
-keep class network.selfevolving.parastoo.mobile.core.contract.** { *; }
-keep class network.selfevolving.parastoo.mobile.core.security.** { *; }
-keep class network.selfevolving.parastoo.mobile.core.config.** { *; }
-keep class network.selfevolving.parastoo.mobile.core.data.** { *; }
-keep class network.selfevolving.parastoo.mobile.core.model.** { *; }
