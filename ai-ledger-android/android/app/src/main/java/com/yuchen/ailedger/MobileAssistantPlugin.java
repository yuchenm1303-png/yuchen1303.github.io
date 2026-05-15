package com.yuchen.ailedger;

import android.content.Intent;
import android.net.Uri;
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

  @PluginMethod
  public void navigate(PluginCall call) {
    String destination = call.getString("destination", "").trim();
    String mode = call.getString("mode", "driving").trim();

    if (destination.isEmpty()) {
      call.reject("Navigation destination is required");
      return;
    }

    Intent launchIntent = getContext().getPackageManager().getLaunchIntentForPackage("com.baidu.BaiduMap");
    if (launchIntent == null) {
      JSObject ret = new JSObject();
      ret.put("ok", false);
      ret.put("message", "没有找到百度地图，请先安装百度地图后再试。");
      call.resolve(ret);
      return;
    }

    Uri uri = new Uri.Builder()
      .scheme("baidumap")
      .authority("map")
      .path("direction")
      .appendQueryParameter("destination", destination)
      .appendQueryParameter("coord_type", "bd09ll")
      .appendQueryParameter("mode", normalizeNavigationMode(mode))
      .appendQueryParameter("src", "andr.yuchen.aiassistant")
      .build();

    Intent intent = new Intent(Intent.ACTION_VIEW, uri)
      .setPackage("com.baidu.BaiduMap")
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

    try {
      getContext().startActivity(intent);
      JSObject ret = new JSObject();
      ret.put("ok", true);
      ret.put("message", "已尝试用百度地图导航到“" + destination + "”。");
      call.resolve(ret);
    } catch (Exception error) {
      call.reject("Cannot open Baidu Map navigation: " + error.getMessage());
    }
  }

  private String normalizeNavigationMode(String mode) {
    if ("walking".equals(mode) || "riding".equals(mode) || "driving".equals(mode)) {
      return mode;
    }
    return "driving";
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
