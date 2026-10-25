# 黄金集 Demo：创建订单

> 合成演练材料，仅验证资产生成流程，黄金集版本 order-create-v1。

用户填写商品编码和数量后提交订单。

- BR-001：商品 SKU-DEMO、数量 1 时创建成功，订单号 ORDER-DEMO-001。
- BR-002：数量为 0 时拒绝创建，错误码 INVALID_QUANTITY。
- BR-003：缺少商品编码时拒绝创建，错误码 SKU_REQUIRED。

HTTP：POST /demo/orders。成功返回 HTTP 201，业务体 {code: OK, orderId: ORDER-DEMO-001}；参数错误返回 HTTP 400。
Dubbo：demo.golden.OrderService#create(java.util.Map)，返回同一业务体。

这是固定黄金集，不依赖真实业务项目、服务或注册中心。测试执行反馈应为 blocked，直到提供真实运行证据。
