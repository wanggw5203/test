# 依赖版本与公开公告审查

核查日期：2026-10-15。范围为本次CycloneDX SBOM中的公共Maven库，以及框架固定的主要直接依赖。使用OSV版本查询定位公告，再核对维护者发布说明；公告匹配不自动证明本框架的调用路径可被利用，未匹配也不证明没有漏洞。构建插件、公司私有依赖、许可证、镜像和源码安全分析仍需公司扫描系统覆盖。

## 本轮版本调整

| 组件 | 原组合 | 当前固定组合 | 调整依据 |
|---|---|---|---|
| Jackson核心、数据绑定、YAML和JavaTime | 2.17.2 | 2.18.11 | 采用2.x维护分支，处理旧版公开公告；相关模块集中对齐 |
| Kafka客户端 | 3.9.1 | 3.9.2 | 同分支修复生产者缓冲竞争导致错误投递和DEBUG敏感信息公告 |
| Netty组件 | Dubbo传递引入4.2.1.Final | 通过netty-bom统一4.2.19.Final | 官方2026-10-06安全维护版，避免各组件各自漂移 |
| LZ4 Java | Kafka传递引入1.10.1 | at.yawk.lz4:lz4-java:1.12.0 | 与新Netty上游选择对齐，包含安全修复及额外输入校验 |

框架自身仍为未发布的1.1.0-SNAPSHOT，不覆盖旧1.0.0。没有为“追最新”更换TestNG执行引擎、升级Kafka主版本或迁移Jackson3。BOM集中管理用于明确经过验证的组合，业务仓库自行覆盖版本后必须重新验证。

上游依据：[Jackson数据绑定公告](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-3pjw-73gf-8qr5)、[Jackson解析约束公告](https://github.com/FasterXML/jackson-core/security/advisories/GHSA-72hv-8253-57qq)、[Kafka官方公告列表](https://kafka.apache.org/community/cve-list/)、[Netty4.2.19发布说明](https://netty.io/news/2026/10/06/4-2-19-Final.html)、[LZ4维护者发布说明](https://github.com/yawkat/lz4-java/releases/tag/v1.12.0)。

## 验证与限制

采用维护者说明和Maven Central可解析的精确版本；完整入口重新验证全部框架回归、本地Dubbo网络调用和订单/团队样例，并重新生成包含测试依赖的SBOM。Kafka的MockConsumer/MockProducer回归不能证明真实broker、所有压缩算法、原生库加载和所有网络编解码场景均已验证。

OSV查询是本轮公开记录快照。当前选定的Jackson core/databind、Kafka客户端、Netty候选坐标和LZ4候选版本已做版本匹配复核；最终SBOM的补充查询结果保存在生成目录 `target/dependency-advisory-review.json`。该文件不作为签名或公司漏洞门禁，也不允许把空匹配写成“安全认证通过”。最终测试计数和源码/制品证据见根验收记录及target目录。

最终SBOM去重后实际查询61个公共Maven组件，响应条目数量完整，本次无匹配公告。记录日期为2026-10-15；这个结果不覆盖未来披露，也不代替可达性、许可证、构建插件或公司私有组件扫描。123次框架回归和81个门禁场景在选定组合下再次全部通过。

LZ4 1.12.0收紧了部分异常输入校验；如公司确有历史异常载荷，需要复现并修正数据/协议。维护者提供1.11.4作为较小行为变化的安全版本选择，但任何兼容回退都必须重新核查公告和测试，不能直接回退至1.10.x掩盖错误。

## 后续进入正式发布门禁

接入公司批准的SCA，规定数据源更新时间、扫描失败处理、风险阈值、可达性分析、许可证策略和有到期日的例外；覆盖测试/运行依赖及构建插件。验证已知不合规依赖、数据库过期或扫描异常会阻断发布，再接入签名、来源证明和消费端验签，详细条件见 [技术路线图](TECHNOLOGY-ROADMAP.md)。
