# shared/

这个目录用于后续承接 Worker 通用基础函数。

当前这些函数大多还散落在 `index-orchestrator.js`、`index-attachments-gateway.js`、`index.js` 中。先建立目录边界，后续小步抽离。

## 未来文件规划

```txt
cors.js          CORS headers / OPTIONS response
json.js          JSON response helper
model-meta.js    provider/model/modelLabel/version 统一处理
errors.js        云端错误标准化
memory.js        memoryContext 清洗
strings.js       通用字符串清洗
```

## 迁移原则

- 只放无业务副作用的通用函数。
- shared 不能直接 import Orchestrator，避免循环依赖。
- shared 不负责判断 intent，也不负责调用模型。
- 抽离时保持函数签名简单，方便单独测试。

## 优先抽离候选

```txt
json(payload, status, headers)
cors(request, env)
modelMeta(provider, model, label)
appendRunLabel(version, label)
normalizeMemoryContext(input)
```
