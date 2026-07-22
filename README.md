# 自建系统异常访问监测与控制组件

将方案 A 的事件采集、规则判定、告警、控制和审计封装为 Maven Reactor 组件。宿主系统保留认证、会话、数据权限和实际阻断能力；本组件提供统一事件模型、14 条基线规则、告警闭环、MyBatis 审计仓储和前端采集契约，避免多个系统重复实现。

适用于 Spring Boot 2.7.x（`javax.servlet`）和 Spring Boot 3.x（`jakarta.servlet`）。这里的“Spring 2/3”指 Spring Boot 主版本，不支持传统 Spring Framework 2.x/3.x。

## 模块一览

| 模块 | 责任 |
| --- | --- |
| `api` | 框架无关的数据模型与宿主 SPI |
| `core` | 规则、告警、控制、授权记录和内存仓储 |
| `web-contract` | 浏览器信号模型、校验器与 JSON Schema |
| `mybatis` | MyBatis 仓储、Mapper 与数据库迁移脚本 |
| `spring-support` | 通用 Spring 支持类 |
| `spring2-starter` / `spring3-starter` | Boot 2 / Boot 3 自动装配 |
| `maven-plugin` | `initialize` 初始化模板目标 |
| `bom` | 组件版本管理 |

## 快速接入

先通过 BOM 管理版本，再按宿主 Boot 大版本引入**一个** Starter：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.jasperbigsum-commits</groupId>
            <artifactId>sys-abnormal-access-monitoring-bom</artifactId>
            <version>${abnormal-access-monitoring.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<!-- Spring Boot 2.7.x；Boot 3.x 改为 spring3-starter -->
<dependency>
    <groupId>io.github.jasperbigsum-commits</groupId>
    <artifactId>sys-abnormal-access-monitoring-spring2-starter</artifactId>
</dependency>
```

执行 `mybatis/src/main/resources/db/monitoring-schema.sql` 的受控数据库迁移，注册宿主 SPI，并以观察模式启动：

```yaml
abnormal:
  access:
    monitor:
      system-id: order-service
      mode: OBSERVE
      frontend:
        enabled: true
      trusted-proxies: [10.0.0.0/8]
```

仅在控制处理器、规则阈值和故障回退完成验收后改为 `ENFORCE`。该模式至少需要一个宿主 `ControlHandler`，否则应用启动失败，防止出现“已开启阻断”但没有实际执行能力的假象。

## 宿主接入边界

必须实现并注册 `IdentityContextProvider`、`ResourceScopeAuthorizer` 和 `TrustedProxyResolver`；可选实现 `EventEnricher`。业务资源访问通过 `ResourceAccessGuard` 调用宿主授权器，组件只记录允许或拒绝结果，绝不把拒绝变为允许。未配置资源授权器时默认拒绝。

生产环境配置数据源和 `SqlSessionFactory` 后自动使用 `MyBatisMonitoringRepository`；没有它时会回退内存仓储，仅适用于本地开发或测试。组件不自动建表，也不发布业务端点。前端信号必须由宿主端点完成认证、限流和 JSON 校验后，再传入 `FrontendSignalRecorder`。

不要写入密码、令牌、Cookie、密钥、未经批准的请求体或响应体。用户、角色、源 IP、会话和最终授权结论均由服务端建立；前端数据只能作为补充证据。

## 初始化模板

在宿主 Maven 工程中生成安全默认配置、SPI、控制器模板和前端示例。已存在文件不会被覆盖：

```powershell
mvn io.github.jasperbigsum-commits:sys-abnormal-access-monitoring-maven-plugin:${abnormal-access-monitoring.version}:initialize `
  '-Dabnormal.access.monitor.outputDirectory=src/main/resources/abnormal-access-monitoring' `
  '-Dabnormal.access.monitor.packageName=com.example.orders.monitoring' `
  '-Dabnormal.access.monitor.systemId=order-service'
```

生成的 Java 模板需要移入宿主 `src/main/java` 并注册为 Spring Bean；模板的匿名身份、默认拒绝和不执行控制均是安全占位，不能直接用于生产。

## 文档与验证

- [集成指南](docs/集成指南.md)：依赖、SPI、MyBatis、前端契约和插件参数。
- [架构与运维说明](docs/架构与运维说明.md)：运行时边界、上线步骤、迁移和巡检。
- [Javadoc 生成说明](docs/Javadoc生成说明.md)：发布 Javadoc JAR 和聚合 HTML 的命令。
- 前端契约：`web-contract/src/main/resources/frontend-signal.schema.json`。

完整构建与测试：

```bash
mvn clean verify -DskipTests=false
```
