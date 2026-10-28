# pet-service-platform

宠物服务预约平台后端（Spring Boot + MyBatis-Plus + MySQL + Redis + JWT），同时为两个真实前端提供接口：

- 商家管理端：[pet-web-admin-kit](https://github.com/XHH-software/pet-web-admin-kit)（Vue 3 + Element Plus + Vite，默认 8081）
- 小程序端：[pet-wx-users-kit](https://github.com/XHH-software/pet-wx-users-kit)（uni-app，H5 调试常用 8082）

后端按前端现有调用契约实现，不是空壳或 mock 数据，所有列表、详情、下单、支付、核销、排班、CRUD、上传接口均可直接联调。

---

## 1. 技术栈

| 组件 | 版本 / 说明 |
| --- | --- |
| JDK | 11 |
| Spring Boot | 2.7.18 |
| MyBatis-Plus | 3.5.5（分页插件） |
| MySQL | 8.x，库名 `pet_service_platform` |
| Redis | 3.x / 5.x，用于 token 黑名单、订单超时 ZSet |
| JWT | jjwt 0.11.5（HS256） |
| 密码 | Spring Security BCrypt |
| 文件存储 | 本地磁盘（默认）/ 阿里云 OSS（可切换） |
| 构建 | Maven |

---

## 2. 目录结构

```
pet-service-platform/
├─ sql/schema.sql                 # 16 张表 DDL + 演示数据由启动器播种
├─ src/main/java/com/pet/platform/
│  ├─ common/                     # Result / PageResult / UserContext / @AutoFill + AOP
│  ├─ config/                     # WebConfig（拦截器、CORS、静态资源）、MyBatis-Plus、Jackson
│  ├─ security/                   # JwtUtil / AdminAuthInterceptor / UserAuthInterceptor / TokenService
│  ├─ storage/                    # 本地 / OSS 上传
│  ├─ task/AppointmentTimeoutTask # Redis ZSet 超时关单 + 扫表兜底
│  ├─ init/DemoDataInitializer    # 首次启动播种演示数据
│  └─ module/
│     ├─ auth, staff, customer, pet, address
│     ├─ service（服务项目 / 套餐 / 分类）
│     ├─ schedule, appointment, review
│     ├─ shop, dashboard, upload
├─ src/main/resources/application.yml
└─ pom.xml
```

---

## 3. 环境与启动

### 3.1 前置依赖

1. MySQL 8 运行在 `localhost:3306`，账号 `root / root`
2. Redis 运行在 `localhost:6379`
3. JDK 11、Maven 3.6+

### 3.2 初始化数据库

```bash
mysql -uroot -proot --default-character-set=utf8mb4 < sql/schema.sql
```

> 该脚本会 `DROP TABLE` 后重建，仅用于开发/演示。重建后请执行 `redis-cli flushdb`，再重启后端。

### 3.3 启动后端

```bash
mvn -DskipTests package
java -jar target/pet-service-platform-1.0.0.jar
```

服务地址：`http://localhost:8080`。首次启动检测到 `staff` 表为空时，会自动播种：

- 1 家门店、4 个分类、8 个服务项目、3 个套餐
- 5 名员工、5 名客户、8 只宠物
- 本周排班、约 30 条历史/今日预约、10 条评价

### 3.4 启动前端联调

```bash
# 管理端
cd D:\pet-web-admin-kit
npm install
npm run dev            # http://localhost:8081

# 小程序 H5
cd D:\pet-wx-users-kit
npm install
npm run dev:h5 -- --port 8082   # http://localhost:8082（不加参数时默认 5173）
```

- 管理端 `VITE_API_BASE=/api`，Vite 代理到 `http://localhost:8080` 并去掉 `/api`。
- 小程序 `src/utils/request.js` 直连 `http://localhost:8080`，后端已开启 CORS。

---

## 4. 演示账号

### 管理端（`POST /admin/staff/login`）

| 账号 | 密码 | 姓名 | 职位 |
| --- | --- | --- | --- |
| `admin` | `123456` | 张伟 | 店长 |
| `13800000002` | `123456` | 王芳 | 美容师 |
| `13800000003` | `123456` | 李娜 | 美容师 |
| `13800000004` | `123456` | 孙悦 | 美容师 |
| `13800000005` | `123456` | 赵敏 | 助理 |

> 账号也支持用手机号登录。

### 小程序端

开发期微信登录为 mock：`POST /user/login` 传 `{"code":"dev_mock_code"}` 即固定绑定客户 **张女士（id=1，13812341234）**。H5 端在 `uni.login` 失败时会自动回退到该 code。

---

## 5. 鉴权与响应约定

### 5.1 请求头

| 端 | Header | 说明 |
| --- | --- | --- |
| 管理端 | `token: <jwt>` | 兼容 `Authorization: Bearer <jwt>` |
| 小程序 | `authentication: <jwt>` | 兼容 `token` |

### 5.2 统一响应

```json
{ "code": 1, "msg": null, "data": {} }
```

| code | 含义 | HTTP 状态 |
| --- | --- | --- |
| 1 | 成功 | 200 |
| 0 | 业务失败（参数、状态、冲突等） | 200 |
| 401 | 未登录 / token 过期 / 已登出 | 200 |

业务异常统一由 `GlobalExceptionHandler` 转为 `code=0`，前端只需判断 `code`。

### 5.3 双端隔离

- `/admin/**` 使用 `AdminAuthInterceptor`，只接受 `type=admin` 的 token，并校验 Redis 黑名单。
- `/user/**` 使用 `UserAuthInterceptor`，只接受 `type=user` 的 token。
- 两个拦截器都在 `afterCompletion` 中调用 `UserContext.clear()`，避免线程池复用导致用户串号。
- 放行路径：`/admin/staff/login`、`/user/login`、`/user/shop/status`、`/user/category/**`、`/user/service-item/**`、`/user/package/**`、`/user/schedule/available`。

---

## 6. 接口清单

### 6.1 管理端 `/admin/**`

| 模块 | 方法与路径 | 说明 |
| --- | --- | --- |
| 认证 | `POST /admin/staff/login` | 员工登录，返回 `{id,name,token}` |
| | `POST /admin/staff/logout` | 登出，token 写入 Redis 黑名单 |
| | `PUT /admin/staff/password` | 修改密码（旧密码校验，成功后需重新登录） |
| 工作台 | `GET /admin/dashboard/summary` | 今日预约/待服务/营业额/出勤 |
| | `GET /admin/dashboard/trend` | 近 7 日预约数与营业额 |
| | `GET /admin/report/staff-rank` | 技师排行 Top 5 |
| 预约 | `GET /admin/appointment/page` | 分页；支持 `startDate/endDate/appointmentDate/status/staffId/keyword`，`appointmentDate` 支持 `today`/`今天` |
| | `GET /admin/appointment/calendar?date=` | 日历视图 |
| | `GET /admin/appointment/{id}` | 详情（手机号不脱敏） |
| | `POST /admin/appointment` | 代客下单（`status=2`、`source=2`，不进入超时关单） |
| | `POST /admin/appointment/{id}/checkin` | 核销 |
| | `POST /admin/appointment/{id}/complete` | 完成服务 |
| | `POST /admin/appointment/{id}/cancel` | 取消 |
| 排班 | `GET /admin/schedule/week?startDate=` | 周视图 |
| | `PUT /admin/schedule/create` | 单日新增（body 用 `date`） |
| | `PUT /admin/schedule/{id}` | 修改单日排班 |
| | `DELETE /admin/schedule/{id}` | 删除 |
| | `POST /admin/schedule/batch` | 批量排班 |
| | `POST /admin/schedule/copy` | 复制上周（body `startDate`） |
| 服务项目 | `GET /admin/service-item/page` / `GET /admin/service-item/{id}` | 分页 / 详情 |
| | `POST` `PUT` `DELETE /admin/service-item` | 新增 / 修改 / 删除（删除用 `?id=`） |
| | `POST /admin/service-item/status/{status}?id=` | 上下架 |
| 套餐 | `GET /admin/package/page` / `GET /admin/package/{id}` | 分页 / 详情 |
| | `POST` `PUT` `DELETE /admin/package` | 新增 / 修改 / 删除 |
| | `POST /admin/package/status/{status}?id=` | 上下架 |
| 分类 | `GET /admin/category/page` / `GET /admin/category/list` | 分页 / 下拉 |
| | `POST` `PUT` `DELETE /admin/category` | 新增 / 修改 / 删除 |
| | `POST /admin/category/status/{status}?id=` | 启停 |
| 客户/宠物 | `GET /admin/customer/page` | 客户分页 |
| | `GET /admin/pet/page?customerId=` / `GET /admin/pet/{id}` | 宠物分页 / 详情（含预约记录） |
| 员工 | `GET /admin/staff/page` / `GET /admin/staff/list` / `GET /admin/staff/{id}` | 分页 / 下拉 / 详情 |
| | `POST` `PUT /admin/staff` | 新增 / 修改 |
| | `POST /admin/staff/status/{status}?id=` | 启用 / 停用 |
| 评价 | `GET /admin/review/page` | 分页（评分字段为 `rating`） |
| | `POST /admin/review/{id}/reply` | 商家回复 |
| 门店 | `GET /admin/shop` / `PUT /admin/shop` | 门店信息与营业时间 |
| 上传 | `POST /admin/common/upload` | `multipart/form-data` 字段名 `file`，仅 jpg/png，≤2MB，返回 URL |

### 6.2 小程序端 `/user/**`

| 模块 | 方法与路径 | 说明 |
| --- | --- | --- |
| 认证 | `POST /user/login` | 微信 code 登录（开发期 mock） |
| | `GET /user/info` / `PUT /user/info` | 当前客户信息 |
| 门店 | `GET /user/shop/status` | 门店状态、营业时间、地址电话 |
| 分类 | `GET /user/category/list?type=` | 分类数组 |
| 服务项目 | `GET /user/service-item/list` | **直接返回数组**，不分页 |
| | `GET /user/service-item/{id}` | 详情 |
| 套餐 | `GET /user/package/list` | **直接返回数组** |
| | `GET /user/package/{id}` | 详情，含 `price`（售价）与 `duration`（总时长） |
| 排班 | `GET /user/schedule/available?date=&serviceItemId=` | 可约员工与时段 |
| 预约 | `POST /user/appointment` | 下单（`status=1`、`source=1`，写入 Redis 超时 ZSet） |
| | `GET /user/appointment/page?status=` | 我的预约分页 |
| | `GET /user/appointment/{id}` | 详情（含 `serviceName`、`timeSlotName`、`hasReview`） |
| | `POST /user/appointment/{id}/pay` | 支付（演示环境直接置为已支付） |
| | `POST /user/appointment/{id}/cancel` | 取消 |
| 宠物 | `GET /user/pet/list` / `POST` `PUT` `DELETE /user/pet` | 宠物 CRUD |
| 地址 | `GET /user/address/list` / `POST` `PUT` `DELETE /user/address` | 地址 CRUD |
| 评价 | `GET /user/review/list` | 我的评价分页 |
| | `POST /user/review` | 发表评价（评分字段为 `score`） |

---

## 7. 核心实现说明

### 7.1 双端 JWT + 独立拦截器 + ThreadLocal

- `JwtUtil` 签发 HS256 token，payload 含 `sub=userId`、`type=admin|user`、`exp`。
- 管理端读 `token` 头，小程序读 `authentication` 头，分别校验 `type`，互不串用。
- 登录用户信息存入 `UserContext`（`ThreadLocal`），请求结束在 `afterCompletion` 清理。
- 登出把 token 写入 Redis 黑名单（`jwt:blacklist:*`，TTL 与 token 剩余有效期一致）；Redis 不可用时降级为仅依赖 JWT 过期时间，不阻断业务。

### 7.2 自定义注解 + AOP 公共字段填充

- `@AutoFill(INSERT|UPDATE)` 标注在 Service 写方法上。
- `AutoFillAspect` 环绕通知扫描方法参数中的 `BaseEntity`，统一填充 `createTime/updateTime/createBy/updateBy`，`createBy/updateBy` 取自 `UserContext`。
- `BaseEntity` 作为所有业务实体父类，避免每个 Service 手写重复赋值。

### 7.3 预约时段冲突 + `schedule.booked_count` 乐观锁

- 下单时按 `staffId + date + 时间段` 查询 `appointment_slot`，存在重叠即拒绝，提示“该时段已被预约，请选择其他时间”。
- 排班表 `schedule` 有 `version` 字段，`booked_count` 通过以下 SQL 更新：

  ```sql
  update schedule
     set booked_count = booked_count + 1, version = version + 1, update_time = now()
   where id = #{id} and version = #{version}
  ```

  更新影响行数为 0 时重试，重试仍失败则回滚下单，防止并发下超卖时段。

### 7.4 Redis ZSet 订单超时关闭 + 扫表兜底

- 小程序下单后写入 `appointment:timeout`，score 为到期时间戳（默认 30 分钟，`app.order.timeout-minutes`）。
- `AppointmentTimeoutTask` 每 30 秒按 score 拉取到期订单并关闭；支付/取消后从 ZSet 移除。
- 每 5 分钟扫表兜底：查询 `status=1 且 create_time < now-30min` 的订单，避免 Redis 写入失败导致订单永久待支付。
- Redis 异常时只记录日志，不影响下单主流程。

### 7.5 订单号与状态

- 订单号格式：`AP + yyyyMMdd + 4 位当日序号`，例如 `AP202610280001`。
- 状态：`1 待支付`、`2 待服务`、`3 服务中`、`4 已取消`、`5 已完成`、`6 已评价`（与两端前端 `APPOINTMENT_STATUS` 完全一致）。
- 管理端代下单初始 `status=2`、`source=2`；小程序下单初始 `status=1`、`source=1`。

### 7.6 展示字段适配

前端存在同一业务字段不同命名的情况，后端在实体上用 `@TableField(exist = false)` 做别名兼容，不改数据库：

- 管理端门店用 `status`，小程序用 `businessStatus`
- 管理端评价用 `rating`，小程序用 `score`
- 小程序评价页需要 `serviceName`、`timeSlotName`
- 小程序套餐详情需要 `price`、`duration`

---

## 8. 配置说明

`src/main/resources/application.yml`：

```yaml
server.port: 8080
spring.datasource.url: jdbc:mysql://localhost:3306/pet_service_platform...
spring.datasource.username/password: root/root
spring.redis.host/port: localhost/6379
app.jwt.secret: ...            # 生产环境务必替换
app.jwt.expire-days: 7
app.upload.type: local         # local | oss
app.upload.dir: ./uploads
app.upload.base-url: http://localhost:8080
app.wx.mock: true              # 开发期微信登录 mock
app.order.timeout-minutes: 30
```

切换到 OSS：把 `app.upload.type` 改为 `oss`，并填写 `app.upload.oss.*` 的 endpoint/bucket/AK/SK。

---

## 9. 已知边界（如实说明）

1. **没有购物车、没有商品库存扣减**。本项目是“服务预约”业务，不是电商；核心并发控制是“时段冲突校验 + `schedule.booked_count` 乐观锁”，不是商品库存防超卖。
2. **微信登录是开发期 mock**。`app.wx.mock=true` 时任意 code 都映射到 `mock_openid_dev`（张女士）；接入真实小程序需配置 `app.wx.app-id/app-secret` 并关闭 mock。
3. **支付是演示实现**。`/user/appointment/{id}/pay` 直接把订单置为已支付，未接微信支付。
4. **单门店**。`shop` 表只有一条记录，未做多租户/多门店隔离。
5. **小程序代下单页忽略 `packageId`**。前端 `appointment/create` 只处理 `serviceItemId`，套餐预约目前仅后端接口支持，属前端缺口。
6. **疫苗过期拦截**。演示数据中 `雪球` 疫苗已过期，下单会被拒绝，这是预期业务校验。
7. **上传文件保存在本地 `./uploads`**，通过 `/uploads/**` 静态访问；生产建议切 OSS 或对象存储。
8. 管理端列表手机号脱敏（`138****1234`），详情接口返回完整号码。

---

## 10. 快速自测

```bash
# 管理端登录
curl -s -X POST http://localhost:8080/admin/staff/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}'

# 工作台今日预约
curl -s "http://localhost:8080/admin/appointment/page?appointmentDate=today&page=1&pageSize=10" \
  -H "token: <上一步返回的 token>"

# 小程序 mock 登录
curl -s -X POST http://localhost:8080/user/login \
  -H "Content-Type: application/json" \
  -d '{"code":"dev_mock_code"}'

# 未登录应返回 code=401
curl -s http://localhost:8080/user/pet/list
```

---

## 11. 与简历描述的对应关系

| 简历能力 | 本项目实际实现 |
| --- | --- |
| 双端 JWT + 独立拦截器 + ThreadLocal 清理 | ✅ 已实现 |
| 自定义注解 + AOP 公共字段填充 | ✅ `@AutoFill` + `AutoFillAspect` |
| Redis ZSet 订单超时关闭 + 扫表兜底 | ✅ 已实现（30 分钟，30s ZSet + 5min 扫表） |
| 乐观锁防超卖 | ⚠️ 实际是 `schedule.booked_count` 的 version 乐观锁 + 时段冲突校验，不是商品库存 |
| 购物车 / 订单明细 / 清购物车 | ❌ 预约业务没有购物车；订单明细为 `appointment_item`（服务项目快照） |

建议简历措辞改为：

> 基于 Spring Boot + MyBatis-Plus + Redis 实现宠物服务预约平台后端，设计双端 JWT 鉴权与独立拦截器，使用 ThreadLocal 传递登录态并在请求结束清理；通过自定义注解 + AOP 统一填充公共字段；预约下单时校验员工时段冲突，并用 `schedule.booked_count` 的 version 乐观锁控制并发；基于 Redis ZSet 实现待支付订单 30 分钟超时关闭，配合定时扫表兜底。
