# 本地 HTTP / Dubbo 订单服务需求

同一套规则分别以 HTTP URL 和真实 Dubbo RPC 发布，供自动化工具进行完整链路验收。不连接公司环境，不使用数据库或注册中心。

## 规则

- BR-001：sku=SKU-DEMO、quantity=1 时成功，业务响应 {"code":"OK","orderId":"ORDER-DEMO-001"}。
- BR-002：sku=SKU-DEMO、quantity=0 时失败，业务响应 {"code":"INVALID_QUANTITY"}。
- BR-003：不传 sku、quantity=1 时失败，业务响应 {"code":"SKU_REQUIRED"}。

## HTTP

- URL：http://127.0.0.1:18081/demo/orders，POST，JSON 请求体。
- 成功返回 201；上述参数错误返回 400。
- 健康检查：GET http://127.0.0.1:18081/health。

## Dubbo

- 真实 Dubbo TCP 协议，不是 HTTP 代理或进程内直接调用。
- 直连地址：dubbo://[::1]:20891，无注册中心，只监听本机 IPv6 回环。
- 接口：demo.golden.OrderService。
- 方法：create(java.util.Map)，返回 java.util.Map。
- 无 group/version；泛化客户端与提供者处于不同 JVM 进程。

## 可观察与故障验证

两种协议均保存服务端调用审计。POST /admin/fault，body={"enabled":true} 时，数量为零分支故意返回错误码 BUG_ZERO_ACCEPTED，HTTP 也返回错误状态 200；恢复 false 后重跑应通过。
该控制口仅绑定本机回环，用于验证断言有效，不用于生产。

## 验收

分别初始化 HTTP/Dubbo 自动化工程。从本需求生成 TAD、用例、接口规格和适配 TestNG 框架的脚手架，实际编译运行两种协议。
基线、故障注入、恢复复跑均保留报告；反馈区分本地真实协议执行与默认黄金集固定文本。
本需求的三条规则与现有 order-create-v1 黄金集一致；本轮不证明远程模型的分析质量。
