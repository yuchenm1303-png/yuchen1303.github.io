const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');
const javaDir = path.join(root, 'android', 'app', 'src', 'main', 'java', 'com', 'yuchen', 'ailedger');
const pluginFile = path.join(javaDir, 'MobileAssistantPlugin.java');

if (!fs.existsSync(pluginFile)) {
  console.error(`MobileAssistantPlugin.java not found: ${pluginFile}`);
  process.exit(1);
}

let source = fs.readFileSync(pluginFile, 'utf8');

const newNavigateMethod = `  @PluginMethod
  public void navigate(PluginCall call) {
    String destination = call.getString("destination", "").trim();
    String mode = call.getString("mode", "driving").trim();
    String mapProvider = call.getString("mapProvider", "baidu").trim();
    String appName = call.getString("appName", "").trim();
    boolean useAmap = "amap".equals(mapProvider) || "高德地图".equals(appName);
    String packageName = useAmap ? "com.autonavi.minimap" : "com.baidu.BaiduMap";
    String mapName = useAmap ? "高德地图" : "百度地图";

    if (destination.isEmpty()) {
      call.reject("Navigation destination is required");
      return;
    }

    Intent launchIntent = getContext().getPackageManager().getLaunchIntentForPackage(packageName);
    if (launchIntent == null) {
      JSObject ret = new JSObject();
      ret.put("ok", false);
      ret.put("message", "没有找到" + mapName + "，请先安装" + mapName + "后再试。");
      call.resolve(ret);
      return;
    }

    Uri uri;
    Intent intent;
    if (useAmap) {
      uri = new Uri.Builder()
        .scheme("androidamap")
        .authority("route")
        .path("plan")
        .appendQueryParameter("sourceApplication", "AI助手")
        .appendQueryParameter("dname", destination)
        .appendQueryParameter("dev", "0")
        .appendQueryParameter("t", normalizeAmapNavigationMode(mode))
        .build();
      intent = new Intent(Intent.ACTION_VIEW, uri)
        .setPackage(packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    } else {
      uri = new Uri.Builder()
        .scheme("baidumap")
        .authority("map")
        .path("direction")
        .appendQueryParameter("destination", destination)
        .appendQueryParameter("coord_type", "bd09ll")
        .appendQueryParameter("mode", normalizeNavigationMode(mode))
        .appendQueryParameter("src", "andr.yuchen.aiassistant")
        .build();
      intent = new Intent(Intent.ACTION_VIEW, uri)
        .setPackage(packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    try {
      getContext().startActivity(intent);
      JSObject ret = new JSObject();
      ret.put("ok", true);
      ret.put("message", "已尝试用" + mapName + "导航到“" + destination + "”。");
      call.resolve(ret);
    } catch (Exception error) {
      call.reject("Cannot open map navigation: " + error.getMessage());
    }
  }

`;

const navigatePattern = /  @PluginMethod\n  public void navigate\(PluginCall call\) \{[\s\S]*?\n  \}\n\n  private String normalizeNavigationMode/;
if (!navigatePattern.test(source)) {
  console.error('Could not find navigate method to patch.');
  process.exit(1);
}
source = source.replace(navigatePattern, `${newNavigateMethod}  private String normalizeNavigationMode`);

if (!source.includes('private String normalizeAmapNavigationMode')) {
  const helper = `
  private String normalizeAmapNavigationMode(String mode) {
    if ("walking".equals(mode)) return "2";
    if ("riding".equals(mode)) return "3";
    return "0";
  }

`;
  source = source.replace(/\n  private String findPackageByLabel\(String appName\) \{/, `${helper}  private String findPackageByLabel(String appName) {`);
}

fs.writeFileSync(pluginFile, source, 'utf8');
console.log('Patched MobileAssistantPlugin navigation provider support.');
