# 黄金集 Demo：创建订单 TAD

> 黄金集 order-create-v1；演练模式产物，未分析真实项目实现。

# 业务背景

用户通过商品编码和数量创建订单。目标是验证需求、测试分析、用例和自动化资产的追溯流程。

# TRD 测试需求分析文档

## 业务规则

- BR-001：SKU-DEMO 与数量 1 创建成功，返回 ORDER-DEMO-001。
- BR-002：数量 0 返回 INVALID_QUANTITY，不能创建订单。
- BR-003：缺少 sku 返回 SKU_REQUIRED，不能创建订单。

## 测试范围与覆盖

GOLD-001 覆盖 BR-001 正常路径；GOLD-002 覆盖 BR-002 数量边界；GOLD-003 覆盖 BR-003 必填校验。

# STD 系统测试分析文档

## 合成接口契约

- HTTP POST /demo/orders，请求 sku、quantity；成功 HTTP 201，参数错误 HTTP 400。
- Dubbo demo.golden.OrderService#create(java.util.Map)，返回业务对象。
- 请求/响应均使用固定黄金数据；没有真实 BO/VO 或调用链证据，类型资产和源码证据留空。

## 期望及风险

成功业务体为 {code: OK, orderId: ORDER-DEMO-001}。错误体分别为 {code: INVALID_QUANTITY}、{code: SKU_REQUIRED}。
数据库、库存扣减、鉴权与真实项目实现均不在本黄金集范围内。

## 执行边界

本次只生成资产。真实服务、浏览器和 TestNG 未执行，反馈必须保留 blocked/unknown。
