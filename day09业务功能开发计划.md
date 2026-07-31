# day09 业务功能开发计划

## 1. 完善订单数据访问能力

- 增加订单分页条件查询、按 ID 查询、按状态统计等 SQL。
- 增加购物车批量插入能力，支持“再来一单”。
- 涉及文件：
  - `sky-server/src/main/java/com/sky/mapper/OrderMapper.java`
  - `sky-server/src/main/resources/mapper/OrderMapper.xml`
  - `sky-server/src/main/java/com/sky/mapper/ShoppingCartMapper.java`
  - `sky-server/src/main/resources/mapper/ShoppingCartMapper.xml`

## 2. 开发用户端历史订单功能

- 实现历史订单分页、订单详情、用户取消订单、再来一单。
- 增加当前用户数据隔离和订单状态校验。
- 模拟支付模式下退款只更新订单支付状态，不调用微信商户接口。
- 涉及文件：
  - `sky-server/src/main/java/com/sky/controller/user/OrderController.java`
  - `sky-server/src/main/java/com/sky/service/OrderService.java`
  - `sky-server/src/main/java/com/sky/service/impl/OrderServiceImpl.java`

## 3. 开发商家端订单管理功能

- 实现条件搜索、状态数量统计、详情、接单、拒单、取消、派送和完成订单。
- 对每个状态流转进行合法性校验。
- 涉及文件：
  - `sky-server/src/main/java/com/sky/controller/admin/OrderController.java`
  - `sky-server/src/main/java/com/sky/service/OrderService.java`
  - `sky-server/src/main/java/com/sky/service/impl/OrderServiceImpl.java`

## 4. 加入配送范围校验

- 配置门店地址、百度地图 AK 和配送范围开关。
- 调用百度地图地理编码与驾车路线接口，超过 5 公里时禁止下单。
- AK 未配置时跳过外部地图调用，保证本地开发环境可继续下单。
- 涉及文件：
  - `sky-server/src/main/resources/application.yml`
  - `sky-server/src/main/resources/application-dev.yml`
  - `sky-server/src/main/java/com/sky/service/impl/OrderServiceImpl.java`
  - `sky-common/src/main/java/com/sky/utils/HttpClientUtil.java`

## 5. 验证

- Maven 编译与测试。
- 核对 Swagger 接口路径是否与用户端小程序及管理端前端一致。
- 检查 SQL 映射、分页结果、订单状态流转和用户数据隔离。

