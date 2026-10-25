# 企业协议适配器

主文档：[企业框架能力指南](../ENTERPRISE-CAPABILITIES.md)。

包含 Dubbo 泛化客户端、aafka 消息发送/观察/消费处理、XXL-JOB 管理端任务适配。通过根目录 Maven reactor 构建：

```powershell
mvn clean verify
mvn install
```

默认测试不连接公司的中间件；Dubbo 用本地真实协议服务，aafka 使用官方 MockConsumer/MockProducer，XXL-JOB 使用本地管理端契约服务。真实 aafka 和 XXL-JOB 测试分别通过 kafka-live、xxljob-live profile 显式启用，必需配置见主文档。

Dubbo 3.3.5、aafka 客户端 3.9.1 为本次编译/测试版本，依赖 optional，使用项目需自行显式声明。XXL-JOB 不绑定其 core JAR，通过 XxlJobDialect 隔离版本接口；内置 2.4.1/3.1.1 契约，其他版本实现该接口后运行契约及真实环境测试。
