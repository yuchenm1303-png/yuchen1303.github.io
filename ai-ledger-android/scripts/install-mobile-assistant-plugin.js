const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');
const androidDir = path.join(root, 'android');
const mainDir = path.join(androidDir, 'app', 'src', 'main');
const javaDir = path.join(mainDir, 'java', 'com', 'yuchen', 'ailedger');
const mainActivity = path.join(javaDir, 'MainActivity.java');
const pluginFile = path.join(javaDir, 'MobileAssistantPlugin.java');
const manifestFile = path.join(mainDir, 'AndroidManifest.xml');
const gradlePropertiesFile = path.join(androidDir, 'gradle.properties');
const gradleWrapperFile = path.join(androidDir, 'gradle', 'wrapper', 'gradle-wrapper.properties');
const localPropertiesFile = path.join(androidDir, 'local.properties');

function ensureAndroidProject() {
  if (!fs.existsSync(androidDir) || !fs.existsSync(mainDir)) {
    console.error('Android project was not found. Run: npm run android:init');
    process.exit(1);
  }
  fs.mkdirSync(javaDir, { recursive: true });
}

function writePlugin() {
  const source = `package com.yuchen.ailedger;

import android.content.Intent;
import android.provider.AlarmClock;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@CapacitorPlugin(name = "MobileAssistant")
public class MobileAssistantPlugin extends Plugin {
  private static final Map<String, String> COMMON_APPS = new HashMap<>();

  static {
    COMMON_APPS.put("微信", "com.tencent.mm");
    COMMON_APPS.put("WeChat", "com.tencent.mm");
    COMMON_APPS.put("支付宝", "com.eg.android.AlipayGphone");
    COMMON_APPS.put("淘宝", "com.taobao.taobao");
    COMMON_APPS.put("QQ", "com.tencent.mobileqq");
    COMMON_APPS.put("高德地图", "com.autonavi.minimap");
    COMMON_APPS.put("百度地图", "com.baidu.BaiduMap");
    COMMON_APPS.put("浏览器", "com.android.browser");
  }

  @PluginMethod
  public void setAlarm(PluginCall call) {
    Integer hour = call.getInt("hour");
    Integer minute = call.getInt("minute");
    String label = call.getString("label", "AI助手提醒");

    if (hour == null || minute == null || hour < 0 || hour > 23 || minute < 0 || minute > 59) {
      call.reject("Invalid alarm time");
      return;
    }

    Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM)
      .putExtra(AlarmClock.EXTRA_HOUR, hour)
      .putExtra(AlarmClock.EXTRA_MINUTES, minute)
      .putExtra(AlarmClock.EXTRA_MESSAGE, label)
      .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

    try {
      getContext().startActivity(intent);
      JSObject ret = new JSObject();
      ret.put("ok", true);
      ret.put("message", String.format(Locale.CHINA, "已打开系统闹钟，时间 %02d:%02d。部分手机需要你再点一次保存。", hour, minute));
      call.resolve(ret);
    } catch (Exception error) {
      call.reject("Cannot open alarm app: " + error.getMessage());
    }
  }

  @PluginMethod
  public void openApp(PluginCall call) {
    String appName = call.getString("appName", "").trim();
    String packageName = call.getString("packageName", "").trim();

    if (packageName.isEmpty()) {
      packageName = COMMON_APPS.get(appName);
    }

    if (packageName == null || packageName.isEmpty()) {
      packageName = findPackageByLabel(appName);
    }

    if (packageName == null || packageName.isEmpty()) {
      JSObject ret = new JSObject();
      ret.put("ok", false);
      ret.put("message", "没有找到“" + appName + "”，可以后续给它补充包名映射。");
      call.resolve(ret);
      return;
    }

    Intent launchIntent = getContext().getPackageManager().getLaunchIntentForPackage(packageName);
    if (launchIntent == null) {
      JSObject ret = new JSObject();
      ret.put("ok", false);
      ret.put("message", "找到了包名，但无法启动：" + packageName);
      call.resolve(ret);
      return;
    }

    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    try {
      getContext().startActivity(launchIntent);
      JSObject ret = new JSObject();
      ret.put("ok", true);
      ret.put("message", "已尝试打开“" + appName + "”。");
      call.resolve(ret);
    } catch (Exception error) {
      call.reject("Cannot open app: " + error.getMessage());
    }
  }

  private String findPackageByLabel(String appName) {
    if (appName == null || appName.isEmpty()) return null;
    PackageManager pm = getContext().getPackageManager();
    Intent intent = new Intent(Intent.ACTION_MAIN, null);
    intent.addCategory(Intent.CATEGORY_LAUNCHER);
    List<ResolveInfo> apps = pm.queryIntentActivities(intent, 0);
    for (ResolveInfo info : apps) {
      CharSequence label = info.loadLabel(pm);
      if (label != null && label.toString().contains(appName)) {
        return info.activityInfo.packageName;
      }
    }
    return null;
  }
}
`;
  fs.writeFileSync(pluginFile, source, 'utf8');
  console.log(`Wrote ${pluginFile}`);
}

function patchMainActivity() {
  if (!fs.existsSync(mainActivity)) {
    const source = `package com.yuchen.ailedger;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    registerPlugin(MobileAssistantPlugin.class);
    super.onCreate(savedInstanceState);
  }
}
`;
    fs.writeFileSync(mainActivity, source, 'utf8');
    console.log(`Created ${mainActivity}`);
    return;
  }

  let source = fs.readFileSync(mainActivity, 'utf8');
  if (source.includes('MobileAssistantPlugin.class')) {
    console.log('MainActivity already registers MobileAssistantPlugin.');
    return;
  }

  if (!source.includes('import android.os.Bundle;')) {
    source = source.replace(/import com\.getcapacitor\.BridgeActivity;\n/, 'import android.os.Bundle;\nimport com.getcapacitor.BridgeActivity;\n');
  }

  source = source.replace(/public class MainActivity extends BridgeActivity \{\s*\}/s, `public class MainActivity extends BridgeActivity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    registerPlugin(MobileAssistantPlugin.class);
    super.onCreate(savedInstanceState);
  }
}`);

  if (!source.includes('MobileAssistantPlugin.class')) {
    console.warn('Could not automatically patch MainActivity. Please register MobileAssistantPlugin manually.');
  } else {
    fs.writeFileSync(mainActivity, source, 'utf8');
    console.log('Patched MainActivity.');
  }
}

function patchManifest() {
  if (!fs.existsSync(manifestFile)) {
    console.warn('AndroidManifest.xml not found, skipped manifest patch.');
    return;
  }
  let source = fs.readFileSync(manifestFile, 'utf8');
  if (!source.includes('<package android:name="com.tencent.mm" />')) {
    const queries = `    <queries>
        <package android:name="com.tencent.mm" />
        <package android:name="com.eg.android.AlipayGphone" />
        <package android:name="com.taobao.taobao" />
        <package android:name="com.tencent.mobileqq" />
        <package android:name="com.autonavi.minimap" />
        <package android:name="com.baidu.BaiduMap" />
        <intent>
            <action android:name="android.intent.action.SET_ALARM" />
        </intent>
    </queries>

`;
    source = source.replace(/(<manifest\b[^>]*>\s*)/, `$1${queries}`);
  }
  if (!source.includes('com.android.alarm.permission.SET_ALARM')) {
    source = source.replace(/<application\b/, '    <uses-permission android:name="com.android.alarm.permission.SET_ALARM" />\n\n    <application');
  }
  fs.writeFileSync(manifestFile, source, 'utf8');
  console.log('Patched AndroidManifest.xml with MobileAssistant permissions and package queries.');
}

function patchGradleProperties() {
  if (!fs.existsSync(gradlePropertiesFile)) {
    console.warn('gradle.properties not found, skipped Gradle path patch.');
    return;
  }
  const flag = 'android.overridePathCheck=true';
  let source = fs.readFileSync(gradlePropertiesFile, 'utf8');
  if (source.includes(flag)) {
    console.log('gradle.properties already allows non-ASCII Windows paths.');
    return;
  }
  if (source.length && !source.endsWith('\n')) source += '\n';
  source += `\n# Allow local Windows builds from user paths that contain non-ASCII characters.\n${flag}\n`;
  fs.writeFileSync(gradlePropertiesFile, source, 'utf8');
  console.log('Patched gradle.properties for non-ASCII Windows paths.');
}

function patchGradleWrapper() {
  if (!fs.existsSync(gradleWrapperFile)) {
    console.warn('gradle-wrapper.properties not found, skipped Gradle mirror patch.');
    return;
  }

  let source = fs.readFileSync(gradleWrapperFile, 'utf8');
  if (!source.includes('services.gradle.org/distributions/')) {
    console.log('gradle-wrapper.properties already uses a non-default distribution host.');
    return;
  }

  source = source.replace(
    /https\\:\/\/services\.gradle\.org\/distributions\//g,
    'https\\://mirrors.cloud.tencent.com/gradle/'
  );
  fs.writeFileSync(gradleWrapperFile, source, 'utf8');
  console.log('Patched gradle-wrapper.properties to use Tencent Cloud Gradle mirror.');
}

function patchLocalProperties() {
  const defaultSdk = process.env.LOCALAPPDATA
    ? path.join(process.env.LOCALAPPDATA, 'Android', 'Sdk')
    : '';
  const sdkDir = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT || defaultSdk;

  if (!sdkDir || !fs.existsSync(sdkDir)) {
    console.warn('Android SDK not found, skipped local.properties patch.');
    return;
  }

  const normalizedSdkDir = sdkDir.replace(/\\/g, '/');
  let source = fs.existsSync(localPropertiesFile)
    ? fs.readFileSync(localPropertiesFile, 'utf8')
    : '';

  if (/^sdk\.dir=/m.test(source)) {
    console.log('local.properties already defines sdk.dir.');
    return;
  }

  if (source.length && !source.endsWith('\n')) source += '\n';
  source += `sdk.dir=${normalizedSdkDir}\n`;
  fs.writeFileSync(localPropertiesFile, source, 'utf8');
  console.log('Patched local.properties with Android SDK path.');
}

ensureAndroidProject();
writePlugin();
patchMainActivity();
patchManifest();
patchGradleProperties();
patchGradleWrapper();
patchLocalProperties();
console.log('MobileAssistant plugin installed. Run: npm run android:sync');
