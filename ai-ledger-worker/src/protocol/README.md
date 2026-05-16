# protocol/

这个目录用于后续承接 command protocol 和 mobileCommand schema。

当前本地动作协议主要还在 `src/index.js` 里，Orchestrator 会把动作类 intent 委托给它。

## 未来文件规划

```txt
mobile-command-schema.js      mobileCommand 字段规范和校验
navigation-command.js         导航 command 结构化处理
alarm-command.js              闹钟/提醒 command 结构化处理
ledger-command.js             记账 command 结构化处理
app-command.js                打开 App command 结构化处理
command-worker-adapter.js     Orchestrator 委托 commandWorker 的适配层
```

## 协议原则

- 云端只能返回结构化 JSON，不能直接执行本地手机动作。
- 本地 App 必须验证 mobileCommand，再生成动作卡片。
- 用户确认后才执行。
- command 字段要稳定，避免前端每次都靠补丁猜字段。

## 当前重点 command

```txt
navigation.start
navigation.modify
navigation.preference.set
alarm.set
app.open
ledger.create
chat
```

## mobileCommand 推荐结构

```js
{
  type: 'navigation.start',
  title: '导航到重庆大学',
  payload: {
    destination: '重庆大学',
    mode: 'transit',
    mapProvider: 'amap'
  },
  confirmRequired: true
}
```

## 注意

导航执行兼容仍在前端 `navigation-execution-compat.js`，Worker 只负责结构化意图和参数，不负责拼安卓 Deep Link。
