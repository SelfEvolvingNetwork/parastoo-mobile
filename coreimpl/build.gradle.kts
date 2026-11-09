// هستهٔ کد پرستو موبایل — همهٔ اسکرین‌ها، ناوبری، شبکه و منطق.
// این ماژول هیچ‌وقت به‌صورت وابستهٔ گرِیدلی داخل APK بدنه نیست؛ خروجی آن
// (classes.jar از AAR) با d8 به classes.dex تبدیل و در بستهٔ هسته
// (parastoo-core.zip) از کانال انتشار گیت‌هاب منتشر می‌شود تا لودر بدنه با
// DexClassLoader لودش کند — به‌روزرسانی بی‌نصب (فرمان مالک 2026-11-09).
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "network.selfevolving.parastoo.mobile.coreimpl"
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

dependencies {
    implementation(project(":foundation"))
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    // کلاینت SSH برای نصب/ارتقای پل از داخل اپ (سناریوی مالک)
    implementation("com.hierynomus:sshj:0.38.0")
    // کلاس‌های پین/گاوصندوق/دیتابیس از :foundation می‌آیند (api)
}
