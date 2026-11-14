plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "network.selfevolving.parastoo.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "network.selfevolving.parastoo.mobile"
        minSdk = 26
        targetSdk = 35
        // معماری «بدنه ثابت + هسته» (فرمان مالک 2026-11-09): این APK فقط بدنه
        // است (لودر هسته + امنیت + فونت/تم) و به‌ندرت آپدیت می‌شود؛ همهٔ رابط
        // و منطق در :coreimpl است که بی‌نصب از کانال انتشار تازه می‌شود.
        versionCode = 25
        versionName = "0.5.7"
    }

    // امضای پایدار CI: همهٔ بیلدها با یک کلید (Secrets گیت‌هاب) تا هر نسخه
    // مستقیم روی قبلی نصب شود. روی دستگاه محلی (بدون env) امضای debug عادی.
    signingConfigs {
        create("ci") {
            val ksFile = System.getenv("CI_KEYSTORE_FILE")
            val ksPass = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            val ksAlias = System.getenv("ANDROID_KEY_ALIAS")
            val ksKeyPass = System.getenv("ANDROID_KEY_PASSWORD")
            if (ksFile != null && ksPass != null && ksAlias != null && ksKeyPass != null) {
                storeFile = file(ksFile)
                storePassword = ksPass
                keyAlias = ksAlias
                keyPassword = ksKeyPass
            }
        }
    }

    buildTypes {
        debug {
            if (signingConfigs.getByName("ci").storeFile != null) {
                signingConfig = signingConfigs.getByName("ci")
            }
        }
        release {
            // نکتهٔ معماری: بیلدهای منتشرشده debug هستند (بدون R8) تا کلاس‌لودر
            // هسته همیشه نام‌های اصلی را ببیند؛ اگر روزی release منتشر شد،
            // قواعد keep در proguard-rules.pro نمادهای بدنه/هسته را حفظ می‌کنند.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signingConfigs.getByName("ci").storeFile != null) {
                signingConfig = signingConfigs.getByName("ci")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    // بنیاد مشترک: قرارداد بدنه↔هسته + گاوصندوق + پین + دیتابیس + پیکربندی هات
    // (کلاس‌هایش در dex همین APK زندگی می‌کنند و هستهٔ خارجی از والد می‌بیند)
    implementation(project(":foundation"))

    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    // قفل گوشی (بیومتریک/PIN) برای ورود — قرارداد مالک: ورودهای بعدی فقط با قفل دستگاه
    implementation("androidx.biometric:biometric:1.1.0")
    // قاعدهٔ معماری (درس rev31): بدنه باید «همهٔ» کلاس‌هایی را در dex خود داشته
    // باشد که dex هسته ارجاع می‌دهد (والد-اول: هسته از بدنه مصرف می‌کند).
    // navigation-compose و sshj در :coreimpl هم هستند — نسخه‌ها باید یکی بمانند.
    // گیت CI: scripts/dex_forensics.py --gate بعد از هر بیلد این را می‌سنجد.
    implementation("androidx.navigation:navigation-compose:2.8.2")
    implementation("com.hierynomus:sshj:0.38.0")
    // بقیهٔ کتابخانه‌ها (okhttp/BC/room/security-crypto) از :foundation می‌آیند —
    // هستهٔ خارجی هم در runtime از همین کلاس‌لودر والد مصرفشان می‌کند.

    debugImplementation("androidx.compose.ui:ui-tooling")
}
