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
