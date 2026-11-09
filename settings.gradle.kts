pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "parastoo-mobile"
// سه‌پاره‌سازی معماری (فرمان مالک 2026-11-09: «بدنه ثابت یک بار نصب می‌شود،
// هستهٔ اصلی توسط بدنه بروز می‌شود — بدون نصب مجدد APK»):
//  :app        = بدنه — لودر هسته + امنیت دستگاه + فونت/تم؛ به‌ندرت آپدیت می‌شود
//  :foundation = بنیاد مشترک بدنه/هسته — قرارداد، گاوصندوق، پین، دیتابیس
//  :coreimpl   = هستهٔ کد — همهٔ اسکرین‌ها و منطق؛ به‌صورت dex از کانال انتشار OTA می‌شود
include(":app")
include(":foundation")
include(":coreimpl")
