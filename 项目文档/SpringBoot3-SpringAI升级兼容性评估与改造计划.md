# 苍穹外卖 Spring Boot 3 / Spring AI 升级兼容性评估与改造计划

## 1. 目标与范围

本计划用于将现有苍穹外卖后端从 Spring Boot 2.7.18 迁移到能够稳定接入 Spring AI 的技术基线。

本轮实施范围仅包含阶段 0 到阶段 4：

1. 统一 JDK 与构建环境。
2. 升级 Spring Boot 和第三方依赖。
3. 完成 Jakarta 与 OpenAPI 3 迁移。
4. 适配配置和基础设施。
5. 对原有系统进行编译、测试和核心业务回归。

本轮不引入 Spring AI、不创建聊天表、不开发聊天接口或前端页面。

## 2. 目标技术基线

| 项目 | 当前版本 | 目标版本 |
| --- | --- | --- |
| JDK | 项目未显式锁定，当前机器为 21.0.6 | JDK 21 LTS |
| Maven | 3.9.4 | 3.9.4 或更高 3.9.x |
| Spring Boot | 2.7.18 | 3.5.16 |
| Spring Framework | 5.3.x | 6.x（由 Boot 管理） |
| Spring AI | 未引入 | 后续阶段采用 1.1.8 |
| MyBatis Starter | 2.2.0 | 3.0.5 |
| PageHelper Starter | 1.4.7 | 2.1.1 |
| Druid Starter | Boot 2 Starter 1.2.20 | Boot 3 Starter 1.2.28 |
| Knife4j | Springfox Starter 3.0.3 | OpenAPI 3 Jakarta Starter 4.5.0 |

Spring AI 1.1.x 官方支持 Spring Boot 3.4.x 和 3.5.x。当前不直接跨到 Spring Boot 4 / Spring AI 2，避免一次迁移跨越过多主版本。

## 3. JDK 21 统一要求

JDK 必须在以下环境中同步统一为 21：

- 本地命令行和 IDEA Project SDK。
- IDEA Maven Runner。
- CI/CD 构建节点。
- 测试服务器和生产服务器。
- 后续 Docker 构建及运行镜像。

根 POM 使用以下配置锁定编译版本：

```xml
<java.version>21</java.version>
<maven.compiler.release>21</maven.compiler.release>
```

使用 `release=21`，避免只设置 `source/target` 时误用更高版本 JDK API。

验收命令：

```text
java -version
mvn -version
mvn clean test
```

## 4. 当前项目评估

后端模块：

- `sky-common`
- `sky-pojo`
- `sky-server`

当前约有 141 个 Java 文件。迁移难度为中等，主要影响依赖管理、Servlet/WebSocket 包名、Swagger 文档体系和测试基线。

原则上无需调整：

- 数据库表结构。
- MyBatis Mapper XML 和现有 SQL。
- 核心实体类及大部分 DTO/VO。
- Vue 2 管理端业务接口。
- Nginx 的现有 HTTP 代理路径。
- 现有接口 URL 和 JSON 字段。

## 5. 阶段 0：冻结基线并统一构建环境

### 工作内容

1. 确认 Git 工作区状态并记录当前分支。
2. 在根 `pom.xml` 中锁定 JDK 21。
3. 修复当前 Redis 测试未加载 Spring 上下文而必然空指针的问题。
4. 将依赖外部 Redis 的测试调整为集成测试，不阻塞默认单元测试。
5. 增加最小 Spring Boot 上下文启动测试和测试环境配置。

### 涉及文件

- `pom.xml`
- `sky-server/pom.xml`
- `sky-server/src/test/java/com/sky/test/SpringDataRedisTest.java`
- `sky-server/src/test/java/com/sky/test/SpringDataRedisIT.java`（重命名后）
- `sky-server/src/test/java/com/sky/SkyApplicationTests.java`（新增）
- `sky-server/src/test/resources/application-test.yml`（新增）

### 验收标准

- 开发机使用 JDK 21。
- Maven 使用同一个 JDK 21。
- 默认 `mvn test` 不依赖外部 Redis。
- 升级前后均有明确测试结果可对比。

## 6. 阶段 1：升级构建和第三方依赖

### 涉及文件

- `pom.xml`
- `sky-common/pom.xml`
- `sky-pojo/pom.xml`
- `sky-server/pom.xml`

### 工作内容

1. Spring Boot 升级到 3.5.16。
2. 使用 Boot 3 兼容的 MyBatis、PageHelper 和 Druid Starter。
3. MySQL 驱动坐标迁移到 `com.mysql:mysql-connector-j`。
4. Knife4j迁移到OpenAPI 3 Jakarta Starter。
5. 删除 `sky-pojo` 中硬编码的旧 Jackson 2.13.5，交由 Boot BOM 管理。
6. 评估并移除不再需要的旧 `javax.xml.bind` 显式依赖。
7. 第一轮保留 JJWT、OSS、微信支付、POI 和 Fastjson 的业务版本，降低迁移变量。

### 验收标准

- Maven依赖解析成功。
- 不存在Spring Framework 5与6混用。
- 不再包含Springfox。
- 不再使用旧MySQL驱动坐标。

## 7. 阶段 2：Jakarta 与 OpenAPI 3 迁移

### Servlet迁移文件

- `JwtTokenAdminInterceptor.java`
- `JwtTokenUserInterceptor.java`
- `controller/admin/ReportController.java`
- `controller/notify/PayNotifyController.java`
- `service/ReportService.java`
- `service/impl/ReportServiceImpl.java`

将 `javax.servlet.*` 改为 `jakarta.servlet.*`。

### WebSocket迁移文件

- `websocket/WebSocketServer.java`

将 `javax.websocket.*` 改为 `jakarta.websocket.*`。

### MVC与接口文档

- `config/WebMvcConfiguration.java`
- 9个管理端控制器。
- 8个用户端控制器。
- `EmployeeLoginDTO.java`
- `EmployeeLoginVO.java`

迁移规则：

| Swagger 2 | OpenAPI 3 |
| --- | --- |
| `@Api` | `@Tag` |
| `@ApiOperation` | `@Operation` |
| `@ApiModel` | `@Schema` |
| `@ApiModelProperty` | `@Schema` |

`WebMvcConfiguration`改为实现`WebMvcConfigurer`，并使用`GroupedOpenApi`分别生成admin和user文档组。

### 验收标准

- Controller接口路径和响应结构不变。
- JWT拦截器正常工作。
- `/doc.html`可以访问。
- admin和user接口文档分组正常。
- WebSocket端点可以注册。

## 8. 阶段 3：配置和基础设施适配

### 涉及文件

- `application.yml`
- `application-dev.yml`
- `RedisConfiguration.java`
- `WebSocketConfiguration.java`

### 工作内容

1. 将 `spring.redis` 迁移为 `spring.data.redis`。
2. 验证 Druid 数据源配置绑定。
3. 验证 MyBatis Mapper 扫描和分页插件。
4. 验证 Redis 序列化和缓存管理器。
5. 验证 WebSocket 端点注册和消息发送。
6. 验证 Excel 报表导出使用 Jakarta Servlet 响应流。

如旧Redis缓存与新版Jackson不兼容，只允许清理开发环境中本项目专用的Redis DB 10，不得清理其他数据库。

## 9. 阶段 4：原有系统稳定性验证

### 自动化检查

```text
mvn clean test
mvn package -DskipTests
mvn dependency:tree
```

### 核心回归清单

- 应用启动和Spring上下文加载。
- 管理员登录。
- 用户登录。
- 菜品和套餐查询。
- 购物车增删改查。
- 用户下单。
- 管理端订单状态流转。
- 模拟支付及支付通知接口加载。
- Excel报表导出。
- Redis缓存。
- WebSocket通知。
- Knife4j/OpenAPI文档。
- JWT未登录响应和跨域配置。

依赖MySQL、Redis、微信环境或真实前端交互的项目，如果当前环境无法执行，必须明确记录为“待联调”，不能用编译通过代替业务验证。

## 10. 风险与回退原则

### 高风险

- Springfox整体迁移。
- Spring 5和Spring 6依赖混用。
- Redis历史缓存反序列化。

### 中风险

- Druid Boot 3 Starter配置。
- Jakarta WebSocket端点注册。
- 微信支付和OSS旧SDK在JDK 21下的运行兼容性。
- Excel导出响应流。

### 回退原则

- 每个阶段独立验证，禁止在基础迁移未稳定前引入Spring AI。
- 不修改数据库结构。
- 不改变现有接口路径和JSON协议。
- 不使用破坏性Git命令覆盖用户改动。

## 11. 本轮明确不包含

- Spring AI依赖和模型配置。
- AI聊天接口。
- SSE流式响应。
- 聊天记录数据表。
- AI工具调用。
- 前端聊天页面。
- 微服务拆分。

## 12. 阶段0到阶段4执行记录（2026-08-08）

### 已完成

- JDK 21与Maven编译目标已统一。
- Spring Boot已升级到3.5.16。
- MyBatis、PageHelper、Druid、MySQL驱动和Knife4j已切换到Boot 3兼容版本。
- Servlet和WebSocket已迁移到Jakarta命名空间。
- 17个控制器以及相关DTO/VO已迁移到OpenAPI 3注解。
- Springfox已移除，admin和user文档分组已建立。
- Redis配置已迁移到`spring.data.redis`。
- 默认测试不再依赖外部Redis，新增了应用启动、OpenAPI、Knife4j、JWT拦截和Redis序列化测试。
- 项目已完成可执行Jar打包。

### 自动化验证结果

- `mvn clean test`：通过。
- `mvn package -DskipTests`：通过。
- 自动化测试：4项通过。
- Redis集成测试：3项通过，并已清理测试Key。
- 未发现Spring Framework 5或Springfox残留。
- Knife4j 4.5.0已使用Springdoc 2.8.17。

### 真实开发环境验证结果

- Spring Boot 3.5.16应用在Java 21下正常启动。
- MySQL连接正常。
- 管理员登录正常。
- 工作台业务数据查询正常。
- 分类查询正常，共返回8条当前数据。
- WebSocket连接、握手和关闭正常。
- `/doc.html`访问正常。
- `/v3/api-docs/admin`访问正常。
- Excel运营数据报表导出正常。
- 定时订单任务可以正常执行数据库查询。
- Redis真实网络连接和读写正常。
- 店铺营业状态Redis读取正常。
- 菜品缓存中的`DishVO`可以正常反序列化，连续两次查询均命中Redis缓存。
- 用户端分类查询正常，共返回8条当前数据。
- 用户端菜品查询正常，共返回3条当前数据。
- 用户端购物车列表和历史订单查询正常。

### 外部服务待联调项

以下能力依赖当前未配置的真实第三方账号，不影响本轮Boot 3升级编译、启动和本地核心业务验收，但上线前仍需使用有效配置联调：

- 阿里云OSS真实文件上传。
- 微信登录真实授权流程。
- 微信真实支付回调；当前开发环境使用模拟支付。
