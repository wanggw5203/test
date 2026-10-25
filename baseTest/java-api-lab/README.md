# Java 接口自动化练习项目

这是配套 `../base-test-toolkit` 的真实 HTTP 被测系统。JDK 17+、Maven 3.9+；使用 JDK HttpServer + Jackson，免数据库、免 Docker。测试通过 Maven 引用原 TestNG 工具包，没有复制框架实现。依赖版本与现有框架对齐。

## 1. 先运行

在当前工作区根目录打开 PowerShell：

```powershell
cd E:\AITest\test\baseTest
powershell -ExecutionPolicy Bypass -File .\java-api-lab\build.ps1
```

脚本通过根Maven reactor构建父POM、工具包和接口项目，无需提前安装父POM。首次构建需要联网下载 Maven 依赖。接口项目19条测试，核心数量见框架验证汇总。接口测试默认自动启动随机端口的临时服务并关闭，无需提前启动应用。

独立部署（当前终端保持运行，Ctrl+C 停止）：

```powershell
powershell -ExecutionPolicy Bypass -File .\java-api-lab\start.ps1
# 或直接运行可执行 JAR
java -jar .\java-api-lab\target\java-api-lab-1.1.0-SNAPSHOT.jar
```

另开终端测试这个独立进程：

```powershell
mvn -f .\java-api-lab\pom.xml test "-Dtest.attr.base.url=http://127.0.0.1:8080"
```

`OrderApiTest` 使用指定服务；`TokenExpiryTest` 始终启动独立临时服务，以控制时钟。用例删除自身创建的订单、注销自身会话，可重复执行。不要将地址指向其他业务系统。

端口被占用时：

```powershell
powershell -ExecutionPolicy Bypass -File .\java-api-lab\start.ps1 -Port 8081
mvn -f .\java-api-lab\pom.xml test "-Dtest.attr.base.url=http://127.0.0.1:8081"
```

报告：`target/surefire-reports/index.html`、`emailable-report.html`、`TEST-*.xml`。SLF4J 无日志实现的提示不影响测试结果。

## 2. 账号与鉴权

| 用户 | 密码 | 角色 |
|---|---|---|
| alice | alice123 | 普通用户 |
| bob | bob123 | 普通用户 |
| admin | admin123 | 管理员 |

登录返回 256 位安全随机 opaque token（不透明令牌，并非 JWT）。后续请求携带 `Authorization: Bearer <accessToken>`。服务端保存会话、检查有效期；默认 1800 秒，注销立即失效。使用 `-Dlab.tokenTtlSeconds=60` 或启动脚本的 `-TokenTtlSeconds 60` 调整。

本项目专用于本机学习：固定演示账号、内存订单及会话、重启清空，仅监听 `127.0.0.1`。没有数据库、注册、密码哈希存储、限流或 HTTPS，不作为生产认证服务。

## 3. 接口契约

| 方法 | 路径 | 权限 | 成功响应 |
|---|---|---|---|
| GET | `/health` | 公开 | 200 `{"status":"UP"}` |
| POST | `/api/auth/login` | 公开 | 200 accessToken、tokenType、expiresIn |
| POST | `/api/auth/logout` | 已登录 | 200 `{"code":"LOGGED_OUT"}` |
| GET | `/api/me` | 已登录 | 200 username、role |
| POST | `/api/orders` | 已登录 | 201 完整订单 |
| GET | `/api/orders/{id}` | 本人或管理员 | 200 完整订单 |
| DELETE | `/api/orders/{id}` | 本人或管理员 | 200 `{"code":"DELETED"}` |
| GET | `/api/admin/stats` | 管理员 | 200 `{"orderCount":数字}` |

登录请求：`{"username":"alice","password":"alice123"}`。

创建请求：`{"productId":"BOOK","quantity":2,"couponCode":"SAVE20"}`。

成功订单：`{"orderId":"动态UUID","owner":"alice","productId":"BOOK","quantity":2,"totalPrice":180}`。

商品仅 BOOK，单价 100 分；quantity 必须为 1～100 的 JSON 整数，字符串数字无效。couponCode 可省略、null、空字符串或 SAVE20；SAVE20 在订单总价中减 20 分。价格和订单归属由服务端计算，客户端不能指定。

错误统一为 `{"code":"错误码"}`：

| HTTP | code | 触发条件 |
|---|---|---|
| 400 | INVALID_JSON | 非对象、错误 JSON、多个 JSON 值 |
| 400 | INVALID_QUANTITY / INVALID_PRODUCT / INVALID_COUPON | 参数不合法 |
| 401 | INVALID_CREDENTIALS | 用户名或密码错误 |
| 401 | UNAUTHORIZED | 缺少 Bearer 头或为空 |
| 401 | INVALID_TOKEN | 伪造、已注销、已清理的令牌 |
| 401 | TOKEN_EXPIRED | 首次使用仍在会话表内的过期令牌，并清理该令牌 |
| 403 | FORBIDDEN | 访问他人订单或普通用户访问管理接口 |
| 404 | ORDER_NOT_FOUND / NOT_FOUND | 订单或路径不存在 |
| 405 | METHOD_NOT_ALLOWED | 路由不支持该方法 |
| 413 | BODY_TOO_LARGE | JSON 请求体大于 64 KiB |
| 415 | JSON_REQUIRED | 登录或创建请求不是 application/json |

除了公开接口，鉴权先于业务路由；无令牌访问未知路径也返回 401。登录会清除所有过期会话，因此过期令牌清除后返回 INVALID_TOKEN。

## 4. 手动完整体验

```powershell
$base = 'http://127.0.0.1:8080'
Invoke-RestMethod "$base/health"
$login = Invoke-RestMethod "$base/api/auth/login" -Method Post -ContentType 'application/json' -Body '{"username":"alice","password":"alice123"}'
$headers = @{ Authorization = "Bearer $($login.accessToken)" }
Invoke-RestMethod "$base/api/me" -Headers $headers
$order = Invoke-RestMethod "$base/api/orders" -Method Post -Headers $headers -ContentType 'application/json' -Body '{"productId":"BOOK","quantity":2,"couponCode":"SAVE20"}'
Invoke-RestMethod "$base/api/orders/$($order.orderId)" -Headers $headers
Invoke-RestMethod "$base/api/orders/$($order.orderId)" -Method Delete -Headers $headers
Invoke-RestMethod "$base/api/auth/logout" -Method Post -Headers $headers
```

## 5. 从哪里读

- `src/main/java/io/testkit/lab/LabServer.java`：被测业务、HTTP 路由、鉴权。
- `src/test/java/io/testkit/lab/OrderApiTest.java`：框架与真实接口的连接点。
- `src/test/java/io/testkit/lab/ApiClient.java`：HTTP 传输层，明确返回状态码和 JSON。
- `src/test/java/io/testkit/lab/LoginIdentityProvider.java`：真实登录/注销适配器。
- `src/test/resources/io/testkit/lab/orders.yaml`：数据驱动入口。
- [带练手册](STUDY-WORKBOOK.md)：源码路线、断点、练习、验收和第一课。

当前自动化覆盖：3 条 YAML 订单闭环、9 条参数/JSON 异常、缺失/伪造令牌、错误密码、注销、角色/对象权限、管理员统计、监听器结果/上下文、过期边界。未把接口契约中的每个分支都作为已覆盖项；405、413、415 等留作扩展练习。

框架升级后，本项目的 ApiClient 复用工具包 HTTP 能力；OrderApiTest 另输出 target/lab-results.jsonl（调用指标、18 条测试记录及汇总）。详见 ../base-test-toolkit/FRAMEWORK-EXTENSIONS.md。
