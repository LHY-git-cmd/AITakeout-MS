# Task 2 实施报告：正式手机号账号与安全会话

## 交付摘要

本任务在不改变既有 `/user/user/login`、`/user/user/login/web` 与 `/user/user/register/web` 行为的前提下，新增正式手机号账号认证链路：

- `POST /user/auth/sms/send`
- `POST /user/auth/register`
- `POST /user/auth/login`
- `POST /user/auth/refresh`
- `POST /user/auth/logout`
- `POST /user/auth/logout-all`

注册使用用户选择的密码进行 BCrypt 编码，并在同一事务中调用 `AccountService.openUserAccount(userId, 50000L)`。登录签发 15 分钟 Access JWT；Refresh Token 使用 256 位安全随机数，数据库只保存 SHA-256 摘要，有效期 30 天，并在刷新时原子撤销旧会话后轮换。

## TDD 记录

首先新增 `UserAuthServiceTest` 的注册/会话契约测试，并运行：

```powershell
mvn -pl sky-server -Dtest=UserAuthServiceTest test
```

RED 结果符合预期：`UserAuthService`、`SmsGateway`、DTO、VO 与三个 Mapper 均不存在，测试编译失败。随后实现最小认证链路，并逐步补齐验证码过期/重复使用、重复手机号、第五次失败锁定、锁定期拒绝、Refresh 轮换、摘要存储、全设备退出及 Controller Cookie 安全测试。

GREEN 验证覆盖：

- 注册采用请求密码，并创建 50000 分初始账户。
- 短信验证码 5 分钟过期、60 秒发送冷却、数据库一次性消费。
- 开发/测试环境固定码为 `246810`，Controller 响应不返回验证码。
- 连续第五次密码失败写入持久化 `LOGIN_LOCKED` 审计，锁定 15 分钟；成功登录会截断连续失败计数窗口。
- Access JWT 有效期固定 15 分钟。
- Refresh Token 只以 SHA-256 摘要落库，30 天过期，刷新后旧 Token 立即撤销。
- 当前设备退出与全部设备退出分别撤销一条/全部活动会话。
- Refresh Cookie 设置 `HttpOnly; Secure; SameSite=Lax`，路径为 `/user/auth`；响应 JSON 通过 `@JsonIgnore` 不暴露原始 Refresh Token。

## 持久化与表结构兼容性

本任务复用 Task 1 的 `V20260918_01__add_user_auth_payment_ledger.sql`，未增加重复迁移：

| 表 | 本任务使用字段 | 兼容性结论 |
| --- | --- | --- |
| `user_session` | `user_id`、`refresh_token_hash`、`device_id`、`expires_at`、`revoked_at`、`create_time` | Mapper 与既有唯一摘要索引、用户/过期索引一致 |
| `sms_verification` | `phone`、`code_hash`、`purpose`、`expires_at`、`used_at`、`attempt_count`、`create_time` | Mapper 与既有手机号/用途/过期索引一致；验证码只存 SHA-256 摘要 |
| `user_security_audit` | `user_id`、`event_type`、`detail_json`、`ip_address`、`user_agent`、`create_time` | 新增 Mapper/XML，字段长度可容纳全部认证事件；复用用户/时间索引统计 15 分钟失败窗口 |
| `user` | `phone`、`password`、`name`、`create_time` | 复用现有手机号唯一约束和 UserMapper 插入/查询能力 |

锁定状态没有存放在进程内存中，而是由 `user_security_audit` 的 `LOGIN_FAILURE`、`LOGIN_SUCCESS`、`LOGIN_LOCKED` 事件推导，因此服务重启后仍然有效。

## 关键实现说明

- `AuthClientContext` 传递 IP、User-Agent 与设备标识，安全事件持久化请求来源。
- `MockSmsGateway` 只在 `dev`/`test` Profile 启用；测试可以直接从注入的 Mock 适配器读取固定验证码。
- 验证码必须存在对应数据库记录，禁止仅凭 Mock 网关内存状态绕过过期、冷却或一次性约束。
- 注册、验证码消费、账户开户和会话创建受事务保护。
- 所有正式认证路径加入用户 JWT 拦截器公开路径；退出接口由 HttpOnly Refresh Cookie 自行鉴权，不依赖可过期的 Access JWT。

## 验证结果

聚焦认证与兼容性命令：

```powershell
mvn -pl sky-server -am '-Dtest=UserAuthServiceTest,UserAuthControllerTest,RequestValidationTest,UserServiceImplTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

结果：19 个测试通过，0 失败，0 错误。

全量命令：

```powershell
mvn -pl sky-server -am test
```

结果：92 个测试通过，0 失败，0 错误。

## 已知边界

- 生产环境没有在本任务范围内指定具体短信供应商，因此不提供生产 `SmsGateway` Bean；生产部署必须接入真实供应商实现，避免固定开发验证码进入生产。
- 当前锁定维度为已存在的用户账号；未知手机号统一返回“手机号或密码错误”，不会创建无主体的安全审计记录，避免违反现有 `user_security_audit.user_id NOT NULL` 约束。

## 评审修复补充（2026-09-19）

针对事务回滚和多实例并发评审，追加以下加固；本节覆盖上文中关于锁定与冷却实现的早期描述：

- 新增 `UserSecurityAuditService`，失败、锁定及其统计查询使用 `REQUIRES_NEW` 独立事务。即使外层 `login` 按预期抛出 `LoginFailedException` 并回滚，失败与锁定事件仍会提交。
- 正式登录通过 `select ... for update` 锁定用户行，将同一账号的密码校验、失败计数和锁定创建串行化。连续失败的边界改为按审计自增 `id` 相对最近成功事件计算，不依赖 `DATETIME` 的秒/微秒精度。
- 新增前向迁移 `V20260919_02__harden_auth_concurrency.sql` 和 `auth_sms_cooldown` 表。`SmsCooldownService` 在调用外部短信网关之前以独立事务执行“首次插入或到期条件更新”，多实例并发时只有一个请求能取得发送资格。
- Refresh 与 Logout 先按 Token 找到稳定用户主体，再锁定用户行。Refresh 在锁内轮换；当前设备 Logout 在同一锁内撤销该用户/设备的所有会话。因此即使 Refresh 先完成，随后成功的 Logout 仍会撤销刚轮换的会话；Logout 先完成时 Refresh 会发现旧 Token 已撤销。
- `/user/user/login/web` 是仅在 `dev` Profile 启用、默认关闭的演示入口。为满足保留旧接口的明确要求，它不接入正式账号锁定状态；生产环境必须保持 `sky.web-login.enabled=false`，正式账号只能使用 `/user/auth/login`。

新增数据库支持测试 `UserAuthPersistenceTest`，在真实 Spring 事务代理、MyBatis Mapper 和 H2 数据库上验证：

1. 五次抛异常的登录请求仍持久化五条失败和一条锁定，第六次正确密码也被拒绝。
2. 五个同秒并发失败被用户行锁串行化，精确形成五条失败和一条锁定。
3. 六个并发短信请求只产生一条验证码记录，并且网关只调用一次。
4. Refresh/Logout 并发后，只要 Logout 成功，该设备不存在任何未撤销会话。

前向迁移另由 `UserPaymentMigrationIT` 验证，确认 `auth_sms_cooldown` 可随完整 Flyway 链创建。

评审修复后的最终验证：

```powershell
mvn -pl sky-server -am test
```

结果：97 个测试通过，0 失败，0 错误。另显式运行 `UserPaymentMigrationIT`：1 个迁移测试通过，0 失败，0 错误。
