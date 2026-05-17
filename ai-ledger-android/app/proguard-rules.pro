# Phase 1 keeps minification disabled.
# Keep this file so the release build configuration has a stable path.

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
