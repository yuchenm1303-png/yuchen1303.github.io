# JavaScript bridge methods are invoked from WebView by name.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ShizukuProvider is instantiated from the merged manifest. The API surface used directly by
# Kotlin remains reachable through normal R8 analysis; only the legacy newProcess entry is invoked
# reflectively and therefore needs an explicit member rule.
-keep,allowoptimization class rikka.shizuku.ShizukuProvider { *; }
-keepclassmembers class rikka.shizuku.Shizuku {
    public static java.lang.Process newProcess(java.lang.String[], java.lang.String[], java.lang.String);
}

-dontwarn rikka.shizuku.**
-dontwarn moe.shizuku.**
