# Android Quick AI Tile 接入方案

这个仓库当前主要是 Web 前端代码。`ai-ledger/index.html` 已经支持快速 AI 入口：

```text
ai-ledger/index.html?mode=quick_ai
ai-ledger/index.html?quick=ai
ai-ledger/index.html#ai-chat
ai-ledger/index.html#quick-ai
```

安卓原生壳里只需要在快捷设置磁贴点击后打开一个透明/半屏 Activity，并让 WebView 加载 `index.html?mode=quick_ai`，就能得到“下滑通知栏 → 点 AI 助手 → 弹出 AI 对话小窗”的体验。

## 1. AndroidManifest.xml 示例

```xml
<uses-permission android:name="android.permission.INTERNET" />

<application
    android:theme="@style/AppTheme"
    android:usesCleartextTraffic="true">

    <activity
        android:name=".QuickAiActivity"
        android:exported="false"
        android:theme="@style/Theme.AiLedger.QuickAi"
        android:launchMode="singleTop"
        android:excludeFromRecents="true" />

    <service
        android:name=".QuickAiTileService"
        android:label="AI助手"
        android:icon="@drawable/ic_ai_tile"
        android:permission="android.permission.BIND_QUICK_SETTINGS_TILE"
        android:exported="true">
        <intent-filter>
            <action android:name="android.service.quicksettings.action.QS_TILE" />
        </intent-filter>
        <meta-data
            android:name="android.service.quicksettings.TOGGLEABLE_TILE"
            android:value="false" />
    </service>
</application>
```

## 2. styles.xml 示例

```xml
<style name="Theme.AiLedger.QuickAi" parent="Theme.MaterialComponents.DayNight.NoActionBar">
    <item name="android:windowIsTranslucent">true</item>
    <item name="android:windowBackground">@android:color/transparent</item>
    <item name="android:backgroundDimEnabled">true</item>
    <item name="android:windowDimAmount">0.28</item>
    <item name="android:windowNoTitle">true</item>
    <item name="android:windowActionBar">false</item>
    <item name="android:colorAccent">#8EA7FF</item>
</style>
```

## 3. QuickAiTileService.kt 示例

```kotlin
package your.package.name

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class QuickAiTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            label = "AI助手"
            subtitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) "一点即聊" else null
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, QuickAiActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(QuickAiActivity.EXTRA_QUICK_AI, true)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(intent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
```

## 4. QuickAiActivity.kt 示例

```kotlin
package your.package.name

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

class QuickAiActivity : Activity() {
    companion object {
        const val EXTRA_QUICK_AI = "extra_quick_ai"
        private const val QUICK_AI_URL = "file:///android_asset/ai-ledger/index.html?mode=quick_ai"
        private const val FULL_APP_URL = "file:///android_asset/ai-ledger/index.html"
    }

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureWindow()

        webView = WebView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            overScrollMode = WebView.OVER_SCROLL_NEVER
            webViewClient = WebViewClient()
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.mediaPlaybackRequiresUserGesture = false
            addJavascriptInterface(QuickAiBridge(), "AndroidQuickAi")
        }

        val container = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            addView(webView, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }
        setContentView(container)
        webView.loadUrl(QUICK_AI_URL)
    }

    private fun configureWindow() {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(0.28f)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val params = window.attributes
        params.gravity = Gravity.CENTER
        params.width = (resources.displayMetrics.widthPixels * 0.94f).toInt()
        params.height = (resources.displayMetrics.heightPixels * 0.76f).toInt()
        window.attributes = params
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        webView.loadUrl(QUICK_AI_URL)
    }

    override fun onBackPressed() {
        finish()
        overridePendingTransition(0, android.R.anim.fade_out)
    }

    inner class QuickAiBridge {
        @JavascriptInterface
        fun postMessage(message: String) {
            if (message.contains("\"type\":\"close\"")) {
                runOnUiThread { finish() }
            }
            if (message.contains("\"type\":\"expand\"")) {
                runOnUiThread {
                    webView.loadUrl(FULL_APP_URL)
                    val params = window.attributes
                    params.gravity = Gravity.CENTER
                    params.width = ViewGroup.LayoutParams.MATCH_PARENT
                    params.height = ViewGroup.LayoutParams.MATCH_PARENT
                    window.attributes = params
                }
            }
        }

        @JavascriptInterface
        fun closeQuickAi() {
            runOnUiThread { finish() }
        }

        @JavascriptInterface
        fun openFullApp() {
            runOnUiThread { webView.loadUrl(FULL_APP_URL) }
        }
    }
}
```

## 5. 使用效果

用户第一次需要手动把磁贴添加到快捷设置栏：

```text
下滑通知栏 → 编辑快捷设置 → 添加“AI助手”磁贴
```

以后就可以：

```text
下滑通知栏 → 点“AI助手” → 弹出 AI 小窗 → 直接输入对话
```

注意：普通安卓应用不能监听“下滑通知栏”这个手势本身，所以无法做到只要下滑就自动弹窗；最稳的系统级做法就是 Quick Settings Tile。