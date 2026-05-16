# shared/

这个目录用于承接 Worker 通用基础函数。

当前已经开始抽离第一批低风险 helper，但 `index-orchestrator.js` 暂时仍保留原函数副本。下一步再小步切换 import，避免一次性大改导致 Worker 部署失败。

## 当前文件

```txt
response.js      JSON response / CORS / OPTIONS helper
model-meta.js    provider/model/modelLabel/version/模型选择规范化 helper
```

## 后续文件规划

```txt
errors.js        云端错误标准化
memory.js        memoryContext 清洗
strings.js       通用字符串清洗
```

## 已抽离函数

`response.js`：

```txt
json(payload, status, headers)
cors(request, env)
optionsResponse(headers)
JSON_HEADERS
```

`model-meta.js`：

```txt
modelMeta(provider, model, label)
appendRunLabel(version, label)
normalizeModelPreference(value)
```

## 迁移原则

- 只放无业务副作用的通用函数。
- shared 不能直接 import Orchestrator，避免循环依赖。
- shared 不负责判断 intent，也不负责调用模型。
- 抽离时保持函数签名简单，方便单独测试。
- 先新建 shared 文件，再切换一个入口文件 import，测试通过后再删旧副本。

## 下一步建议

第一步只让 `index-orchestrator.js` import：

```js
import { json, cors } from "./shared/response.js";
import { modelMeta, appendRunLabel, normalizeModelPreference } from "./shared/model-meta.js";
```

然后删除 `index-orchestrator.js` 底部对应重复函数。

第二步再处理 `index-attachments-gateway.js`。

第三步最后处理 `index.js` command protocol。
