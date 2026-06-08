# Phase 1 keeps minification disabled.
# Keep this file so the release build configuration has a stable path.

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-dontwarn rikka.shizuku.**
-dontwarn moe.shizuku.**
