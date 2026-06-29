# 长期记忆第一阶段部署说明

目标：Android 管理、账号开关与云端召回统一使用 V4。高权威稳定事实进入 Anchor 候选池，再和动态候选交给同一个云端重排器判断；Anchor 不代表机械注入。

## 部署顺序

1. 确认已执行 `001_memory_foundation.sql`。
2. 执行 `002_memory_v4_single_source.sql`。
3. 执行 `003_memory_v4_archived_edit.sql`。
4. 执行 `004_memory_anchor_candidates.sql`。
5. 执行 `005_memory_hybrid_search.sql`。
6. 部署云端后端 `v171-anchor-first-unified-cloud-rerank`。
7. 安装 `dev-update-1` 最新 APK并登录确认长期记忆总开关。

不要部署已经作废的 v170 姓名关键词版本。不要删除旧 `assistant_memories` 表；它只用于回滚，不再参与运行。

## 架构验收

- Android 不构建记忆候选，不做关键词、领域、类别或正文语义判断。
- `explicit_core`、置顶项、高权威 `profile`、高权威 `preference` 和当前项目的高权威约束进入 Anchor 候选。
- Need Gate 只决定动态召回预算，不得否决 Anchor。
- Anchor 只是候选；统一重排器没有选择时不得注入。
- 动态长尾记忆走向量、全文和 trigram 混合召回。
- 所有记忆层级只经过一个云端重排协议。
- usage 只由云端按真实 `model_injected` 与 `answer_completed` 阶段记录。

## 功能验收

1. 新增、编辑、归档、恢复与删除都直接作用于 V4。
2. 换设备登录后读取同一个账号级开关。
3. 关闭长期记忆后 Custom Instructions 仍独立生效。
4. 高权威 profile 与 preference 面对不同自然表达，都有机会进入统一重排，不依赖固定问法。
5. 无关请求中，统一重排器可以排除 Anchor，不应机械注入个人资料。
6. Gate 失败或返回低预算时，Anchor 仍可进入统一重排。
7. 一次真实命中应能查询到 `model_injected` 与 `answer_completed`。
