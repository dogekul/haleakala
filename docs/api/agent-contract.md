# 智鹿交付平台 Agent HTTP 契约

版本：`v1`。外部 Agent 团队只需实现本文件中的三个接口，不依赖平台内部数据库或代码。

## 安全与幂等

双方请求均携带：

- `X-Agent-Timestamp`：Unix 秒时间戳；接收方拒绝与当前时间相差超过 300 秒的请求。
- `X-Agent-Signature`：`hex(HMAC-SHA256(sharedSecret, timestamp + "." + rawBody))`。
- 回调的 `eventId` 在全局范围唯一；平台对重复事件直接返回成功但不重复应用。
- 浏览器提交平台接口时使用 `Idempotency-Key`，同一项目内重复键返回同一平台任务。
- 平台向 Agent 提交时同样携带稳定的 `Idempotency-Key: platform-job-{平台任务ID}`；Agent 必须对该键去重，并为重复请求返回同一个 `externalJobId`。

## Agent 提交任务

`POST /v1/jobs`

```json
{
  "skill": "deliver-init",
  "scenario": "normal",
  "callbackUrl": "http://backend:8080/api/v1/integrations/agent/events",
  "context": {
    "code": "PRJ-001",
    "name": "华东银行交付",
    "customer_name": "华东银行",
    "current_stage": "START"
  }
}
```

返回 HTTP `202`：

```json
{ "externalJobId": "agent-job-uuid", "status": "QUEUED" }
```

支持的 Skill：`deliver-init`、`deliver-require`、`deliver-dev`、`deliver-transition`、`deliver-standardize`、`deliver-close`。`scenario` 仅供 Mock 和验收使用，可为 `normal`、`failure`、`timeout`。

## 查询与取消

- `GET /v1/jobs/{externalJobId}`：返回任务状态、进度、错误与产出物。
- `POST /v1/jobs/{externalJobId}/cancel`：取消非终态任务，返回 HTTP `202`。

状态只允许按 `QUEUED → RUNNING → SUCCEEDED` 前进；终态还包括 `FAILED`、`TIMED_OUT`、`CANCELLED`。终态不可回退。

## 平台回调

`POST /api/v1/integrations/agent/events`

```json
{
  "eventId": "event-uuid",
  "externalJobId": "agent-job-uuid",
  "status": "SUCCEEDED",
  "progress": 100,
  "error": null,
  "artifacts": [{
    "name": "deliver-init-result.md",
    "mimeType": "text/markdown",
    "artifactType": "AGENT_OUTPUT",
    "content": "# 项目初始化结果"
  }]
}
```

平台返回 HTTP `202`。回调签名无效或过期返回 `401`；未知任务返回 `404`；终态回退返回 `409`。成功产出物由平台写入 MinIO，并在 MySQL 中绑定为项目 Artifact。

## 超时与重试

平台先在 MySQL 中持久化排队任务，再由补偿扫描投递；网络错误和 HTTP 5xx 会进行有限重试，业务 4xx 不重试。进程在远端受理后异常退出时，稳定幂等键可避免重复创建远端任务。平台还会主动查询非终态远端任务，因此即使回调短暂丢失，状态与产出物也会最终收敛。超过任务 `timeoutAt` 的排队或运行任务由补偿扫描标记为 `TIMED_OUT`。

本地 Mock：`node mock-agent/server.mjs`，默认端口 `8090`，共享密钥默认 `change-me`。
