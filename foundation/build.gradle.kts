// بنیاد مشترک بدنه/هسته — قرارداد AppCoreEntry + امنیت + پیکربندی هات + دیتابیس.
// کلاس‌های این ماژول در dex بدنه (APK) زندگی می‌کنند و هستهٔ خارجی از طریق
// کلاس‌لودرِ والد (parent-first) به آن‌ها می‌رسد؛ پس نسخه‌ها باید با بدنه پین
// بمانند (همین فایل، همان کامیت).
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "network.selfevolving.parastoo.mobile.foundation"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// همه به‌صورت api: هستهٔ خارجی (:coreimpl به‌صورت dex جدا) در زمان کامپایل
// دقیقاً همین نمادها را می‌بیند و در زمان اجرا از والد می‌گیرد.
dependencies {
    api(platform("androidx.compose:compose-bom:2024.09.03"))
    api("androidx.compose.runtime:runtime")
    api("androidx.core:core-ktx:1.13.1")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // گاوصندوق (SecretVault): Keystore سخت‌افزاری + EncryptedSharedPreferences
    api("androidx.security:security-crypto:1.1.0-alpha06")
    // پین گواهی (PinnedTrust): کلاینت HTTPS پین‌شده
    api("com.squareup.okhttp3:okhttp:4.12.0")
    // BC کامل — CryptoFix/BcFix (درس میدانی X25519)
    api("org.bouncycastle:bcprov-jdk18on:1.78.1")
    // دیتابیس محلی پروفایل‌ها (بدون هیچ راز)
    api("androidx.room:room-runtime:2.6.1")
    api("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
}
