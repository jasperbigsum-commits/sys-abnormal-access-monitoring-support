# Javadoc 生成说明

本项目的公开 API、核心扩展点、MyBatis 仓储和 Starter 自动装配均通过 Maven Javadoc 插件生成 HTML 文档与可附加的 Javadoc JAR。普通构建不会自动执行该步骤；发布构件时使用 `javadoc` Profile。

## 生成 Javadoc JAR

```bash
mvn -Pjavadoc clean package
```

命令会在每个可发布模块的 `target/` 目录生成 `*-javadoc.jar`，可与主 JAR 一同部署到 Maven 仓库。

## 生成聚合 HTML

```bash
mvn -Pjavadoc clean install
mvn javadoc:aggregate
```

聚合插件需要从本地 Maven 仓库解析模块间的 SNAPSHOT 依赖，因此应先执行第一条命令。聚合 HTML 默认输出到根目录 `target/reports/apidocs/index.html`。在提交或发布前应打开首页，检查中文编码、模块导航和公开扩展点说明是否可读。

## 维护要求

- 为新增或修改的公共类、接口、枚举和宿主 SPI 补充 Javadoc。
- 注明安全边界、线程安全性、空值约束和宿主实现责任，而不是重复方法名。
- 不在 Javadoc 示例中出现真实令牌、Cookie、密码、IP 白名单或生产系统标识。
