# BÁO CÁO PHÂN TÍCH THIẾT KẾ VÀ TRIỂN KHAI DỰ ÁN NGÂN HÀNG SỐ "RikkeiBank API"

**Dự án**: Ngân hàng số "RikkeiBank API"  
**Lĩnh vực**: Tài chính – Ngân hàng (Fintech)  
**Khách hàng**: Chi nhánh ngân hàng bán lẻ RikkeiBank  
**Nền tảng kỹ thuật**: Java 21, Spring Boot 3.3.4, Spring Cloud 2023.0.3, Spring Cloud Gateway, Eureka Server, Spring Cloud Config, Spring Data JPA, OpenFeign, Resilience4j Circuit Breaker, Apache Kafka, Redis, H2 Database (Database-per-service), JUnit 5, Jacoco.

---

## MỤC LỤC
1. [Phân tích nghiệp vụ & Lý do chuyển đổi Monolithic sang Microservices (MSA)](#1-phân-tích-nghiệp-vụ--lý-do-chuyển-đổi-monolithic-sang-microservices-msa)
2. [Kiến trúc tổng thể hệ thống (System Architecture)](#2-kiến-trúc-tổng-thể-hệ-thống-system-architecture)
3. [Mô hình Dữ liệu phân tán (Database-per-service)](#3-mô-hình-dữ-liệu-phân-tán-database-per-service)
4. [Bảo mật, Phân quyền & Quản lý Phiên (Security & Seamless Session)](#4-bảo-mật-phân-quyền--quản-lý-phiên-security--seamless-session)
5. [Giao dịch phân tán với Saga Pattern (Orchestrator & Compensation)](#5-giao-dịch-phân-tán-với-saga-pattern-orchestrator--compensation)
6. [Kháng lỗi hệ thống với Resilience4j Circuit Breaker](#6-kháng-lỗi-hệ-thống-với-resilience4j-circuit-breaker)
7. [Distributed Caching với Redis (Chiến lược Cache-Aside)](#7-distributed-caching-với-redis-chiến-lược-cache-aside)
8. [Giao tiếp Bất đồng bộ & Event-Driven qua Apache Kafka](#8-giao-tiếp-bất-đồng-bộ--event-driven-qua-apache-kafka)
9. [Cấu trúc mã nguồn & Danh sách Module](#9-cấu-trúc-mã-nguồn--danh-sách-module)
10. [Hướng dẫn Khởi chạy & Kịch bản Kiểm thử minh chứng](#10-hướng-dẫn-khởi-chạy--kịch-bản-kiểm-thử-minh-chứng)

---

## 1. Phân tích nghiệp vụ & Lý do chuyển đổi Monolithic sang Microservices (MSA)

### 1.1. Hiện trạng hệ thống Monolithic cũ
- **Điểm nghẽn tài nguyên (Bottleneck)**: Toàn bộ module (khách hàng, tài khoản, chuyển tiền, thông báo) đóng gói chung một WAR/JAR và dùng chung một CSDL lớn. Vào các giờ cao điểm (cuối tháng chi lương, ngày hội mua sắm), lưu lượng chuyển khoản tăng vọt làm nghẽn toàn bộ kết nối DB (connection pool exhaustion), khiến cả chức năng xem thông tin khách hàng hay đăng nhập đều bị đình trệ.
- **Rủi ro lỗi dây chuyền (Single Point of Failure)**: Lỗi tại một module không quan trọng (ví dụ lỗi gửi thông báo email/SMS) có thể chiếm dụng bộ nhớ (memory leak), gây crash toàn bộ tiến trình ứng dụng của ngân hàng.
- **Khó khăn trong mở rộng & bàn giao**: Mỗi lần nâng cấp một tính năng nhỏ của nghiệp vụ Giao dịch viên đều đòi hỏi phải dừng và kiểm thử lại toàn bộ hệ thống ngân hàng lớn, chu kỳ phát hành bị kéo dài.

### 1.2. Lợi ích vượt trội khi chuyển đổi sang Microservices (MSA)
1. **Tách rời nghiệp vụ (Decoupling & Autonomy)**: Mỗi dịch vụ độc lập (`identity-service`, `customer-service`, `account-service`, `transaction-service`, `notification-service`) có vòng đời phát triển, triển khai và bảo trì riêng biệt.
2. **Khả năng mở rộng độc lập (Independent Elastic Scalability)**: `transaction-service` và `account-service` chịu tải gấp 100 lần so với `customer-service` (quản lý hồ sơ). Ta có thể nhân bản (scale up) 10 instance của `transaction-service` mà không cần tốn tài nguyên nhân bản các service khác.
3. **Cô lập lỗi (Fault Isolation)**: Nếu `notification-service` hoặc CSDL thông báo tạm thời ngưng trệ, nghiệp vụ lõi chuyển tiền vẫn hoàn tất thành công.
4. **Cơ sở dữ liệu độc lập (Database-per-service)**: Loại bỏ triệt để xung đột khóa bảng (table locks), đảm bảo an toàn phân vùng dữ liệu tài chính.

---

## 2. Kiến trúc tổng thể hệ thống (System Architecture)

```
                         [ Mobile App / Web Frontend / Postman ]
                                            |
                                            v (Port 8080)
                       +------------------------------------------+
                       |        Spring Cloud API Gateway          |
                       |  - Global JWT Authentication Filter      |
                       |  - Token Revocation / Blacklist Check    |
                       |  - LoadBalancer & Route Dispatcher       |
                       +------------------------------------------+
                           /         |              |         \
                          /          |              |          \
                         v           v              v           v
               +------------+ +-------------+ +-----------+ +---------------+
               |  Identity  | |  Customer   | |  Account  | |  Transaction  |
               |  Service   | |   Service   | |  Service  | |    Service    |
               |  (Port     | |  (Port      | |  (Port    | |   (Port       |
               |   8081)    | |   8082)     | |   8083)   | |    8084)      |
               +------------+ +-------------+ +-----------+ +---------------+
                     |               |              ^               |
                     |               |              | (Feign +      |
                     |               |              |  Resilience4j)|
                     |               |              +---------------+
                     |               | (Cache-Aside)                | (Produces Events)
                     |               v                              v
                     |       +---------------+             +----------------+
                     +------>|  Redis Cache  |             |  Apache Kafka  |
              (Blacklist TTL)|  (Port 6379)  |             |  Topic:        |
                             +---------------+             |  transaction-  |
                                                           |  events        |
                                                           +----------------+
                                                                    | (Consumes)
                                                                    v
                                                           +----------------+
                                                           |  Notification  |
                                                           |    Service     |
                                                           |  (Port 8085)   |
                                                           +----------------+
                     ===============================================
                     Hạ tầng hỗ trợ (Infrastructure Services):
                     - Spring Cloud Netflix Eureka Server (Port 8761)
                     - Spring Cloud Config Server (Port 8888)
                     ===============================================
```

---

## 3. Mô hình Dữ liệu phân tán (Database-per-service)

Mỗi service hoàn toàn tự trị, sở hữu CSDL độc lập, không truy cập trực tiếp bảng của service khác:

| Microservice | Tên CSDL (In-memory/File/MySQL) | Các thực thể cốt lõi (Entities) | Mô tả trách nhiệm |
|---|---|---|---|
| `identity-service` | `identitydb` | `users`, `refresh_tokens` | Tài khoản xác thực, mã hóa BCrypt, quyền hạn, Refresh Token |
| `customer-service` | `customerdb` | `customers`, `staff` | Hồ sơ khách hàng (CIF, CCCD, Email), Danh mục Giao dịch viên/Nhân viên |
| `account-service` | `accountdb` | `account_types`, `accounts` | Danh mục loại tài khoản (SAVINGS, CHECKING, VIP), Số dư tài khoản thanh toán |
| `transaction-service` | `transactiondb` | `transactions`, `saga_step_logs` | Lịch sử giao dịch nợ/có, Nhật ký các bước Saga & bằng chứng rollback |
| `notification-service` | `notificationdb` | `notifications` | Lịch sử biến động số dư gửi tới khách hàng qua luồng Event-driven |

---

## 4. Bảo mật, Phân quyền & Quản lý Phiên (Security & Seamless Session)

### 4.1. Ma trận Phân quyền theo vai trò (RBAC)
- **ADMIN**:
  - Quản lý toàn bộ danh mục Hồ sơ: Thêm, sửa, xóa Khách hàng, Giao dịch viên (`Staff`), Loại tài khoản (`AccountType`).
  - Quản lý trạng thái tài khoản: Khóa/mở khóa tài khoản thanh toán.
  - **Thu hồi quyền truy cập (Token Revocation / Force Logout)**: Ép buộc bất kỳ tài khoản nào đăng xuất ngay lập tức bằng cách ghi JWT vào Redis Blacklist với thời gian sống (TTL) tương ứng với thời gian hết hạn còn lại của token.
- **TELLER (Giao dịch viên)**:
  - Xem và quản lý danh sách giao dịch phát sinh trong ngày (`/api/transactions/daily`).
  - Mở tài khoản thanh toán mới cho khách hàng đến quầy.
  - Tuyệt đối không can thiệp hay sửa đổi giao dịch của nhân viên khác.
- **CUSTOMER (Khách hàng)**:
  - Xem số dư và chi tiết các tài khoản ngân hàng của chính mình (chặn xem tài khoản người khác).
  - Khởi tạo giao dịch chuyển khoản đi (`/api/transactions/transfer`).
  - Xem lịch sử giao dịch cá nhân và thông báo biến động số dư.
- **Khách vãng lai**: Mọi truy cập vào tài nguyên tài chính đều bị Gateway chặn và trả về HTTP `401 Unauthorized` hoặc `403 Forbidden`.

### 4.2. Trải nghiệm Đăng nhập Liền mạch (Seamless Experience)
- Cơ chế **Access Token (Ngắn hạn, 1 giờ)** kết hợp **Refresh Token (Dài hạn, 7 ngày)**:
  - Mobile App / Web App lưu trữ an toàn Refresh Token.
  - Khi Access Token hết hạn, ứng dụng tự động gọi endpoint `/api/auth/refresh` ngầm dưới nền để nhận cặp Access Token mới mà **không bắt người dùng phải nhập lại mật khẩu**.

---

## 5. Giao dịch phân tán với Saga Pattern (Orchestrator & Compensation)

Do mỗi service sở hữu CSDL riêng, nghiệp vụ Chuyển khoản (trừ tiền tài khoản nguồn ở `account-service` và cộng tiền tài khoản đích ở `account-service` cùng việc ghi nhận giao dịch tại `transaction-service`) không thể sử dụng giao dịch ACID truyền thống (`@Transactional` hai pha 2PC gây lock tài nguyên lớn).
Hệ thống sử dụng **Saga Orchestrator Pattern** được điều phối bởi `TransactionSagaService`:

### 5.1. Luồng thành công (Happy Path)
1. `transaction-service` tạo bản ghi giao dịch trạng thái `PENDING`.
2. **Saga Step 1**: Gọi `account-service` thực hiện **DEBIT** (Trừ tiền tài khoản nguồn).
   - Kiểm tra số dư khả dụng: `balance - minBalance >= amount`.
   - Trừ tiền, cập nhật số dư mới, ghi log bước DEBIT thành công vào `saga_step_logs`.
3. **Saga Step 2**: Gọi `account-service` thực hiện **CREDIT** (Cộng tiền tài khoản đích).
   - Kiểm tra tài khoản đích đang hoạt động (`ACTIVE`).
   - Cộng tiền, cập nhật số dư mới, ghi log bước CREDIT thành công vào `saga_step_logs`.
4. **Saga Step 3**: Cập nhật trạng thái giao dịch thành `COMPLETED`.
5. **Event-driven**: Phát hành sự kiện `TRANSFER_COMPLETED` lên Kafka topic `transaction-events`.

### 5.2. Luồng thất bại & Bồi hoàn tự động (Compensating Rollback)
Nếu tại **Step 2**, bước CREDIT tài khoản đích thất bại (ví dụ: tài khoản đích bị khóa, mạng lỗi, hoặc cờ kiểm thử `simulateFailureAtTarget=true`):
1. Bộ điều phối Saga Orchestrator bắt được ngoại lệ (`Exception`).
2. Nhận biết tài khoản nguồn đã bị trừ tiền (`sourceDebited = true`).
3. **Kích hoạt Compensating Transaction**: Gọi endpoint `/api/accounts/{accountNumber}/refund` tại `account-service` để hoàn trả lại 100% số tiền đã trừ vào tài khoản nguồn.
4. Ghi log bước bồi hoàn `COMPENSATE_REFUND` trạng thái `COMPENSATED` vào `saga_step_logs`.
5. Cập nhật giao dịch thành `COMPENSATED`, lưu chi tiết lý do lỗi.
6. Phát hành sự kiện `TRANSFER_FAILED_COMPENSATED` lên Kafka để thông báo cho khách hàng rằng giao dịch không thành công và tiền đã được hoàn trả nguyên vẹn.

---

## 6. Kháng lỗi hệ thống với Resilience4j Circuit Breaker

Để giải quyết vấn đề **Lỗi dây chuyền (Cascading Failure)** khi gọi đồng bộ giữa các microservice, `transaction-service` tích hợp **Resilience4j Circuit Breaker** bọc bên ngoài Feign Client gọi sang `account-service`:

### Cơ chế 3 trạng thái:
1. **CLOSED (Bình thường)**:
   - Các request chuyển khoản được định tuyến bình thường sang `account-service`.
   - Hệ thống theo dõi tỷ lệ lỗi qua Sliding Window (kích thước 5 cuộc gọi, tối thiểu 3 cuộc gọi).
2. **OPEN (Mở mạch ngắt)**:
   - Khi tỷ lệ lỗi vượt ngưỡng 50% (ví dụ `account-service` bị sập hoặc quá tải), Circuit Breaker chuyển sang trạng thái `OPEN`.
   - Toàn bộ các request mới tới `account-service` đều bị chặn ngay lập tức mà không gửi đi, chuyển thẳng tới hàm `fallbackMethod ("transferFallback")`.
   - Điều này giải phóng tài nguyên CPU/Thread, không làm treo Gateway và hệ thống tài chính.
3. **HALF_OPEN (Nửa mở - Thử nghiệm phục hồi)**:
   - Sau thời gian chờ `waitDurationInOpenState: 10s`, Circuit Breaker tự động chuyển sang `HALF_OPEN`.
   - Cho phép 2 cuộc gọi thử nghiệm đi qua. Nếu thành công, mạch đóng lại (`CLOSED`); nếu tiếp tục lỗi, mạch quay lại `OPEN`.

---

## 7. Distributed Caching với Redis (Chiến lược Cache-Aside)

Tại `customer-service`, các truy vấn xem thông tin khách hàng được tối ưu hóa bằng Spring Cache kết hợp Redis:

- **Đọc thông tin (`@Cacheable(value = "customers", key = "#id")`)**:
  - Khi có request tra cứu khách hàng theo ID:
  - Hệ thống kiểm tra trong Redis cache với key `customers::<id>`.
  - Nếu có (Cache Hit): Trả về ngay lập tức với tốc độ < 2ms, không truy vấn CSDL.
  - Nếu chưa có (Cache Miss): Truy vấn CSDL H2/MySQL, sau đó tự động lưu vào Redis cache với TTL 10 phút.
- **Cập nhật thông tin (`@CachePut(value = "customers", key = "#id")`)**:
  - Khi cập nhật hồ sơ khách hàng, dữ liệu vừa được lưu vào CSDL vừa đồng thời ghi đè làm mới dữ liệu trong Redis cache.
- **Xóa khách hàng (`@CacheEvict(value = "customers", key = "#id")`)**:
  - Khi xóa khách hàng, bản ghi tương ứng trong Redis cache lập tức bị thu hồi để tránh dữ liệu rác (stale data).

---

## 8. Giao tiếp Bất đồng bộ & Event-Driven qua Apache Kafka

- **Giảm liên kết lỏng (Loose Coupling)**: `transaction-service` không cần biết `notification-service` nằm ở đâu hay đang bật hay tắt. Sau khi giao dịch hoàn tất, một tin nhắn `TransactionEvent` được gửi lên Kafka topic `transaction-events`.
- **Consumer chịu tải cao**: `notification-service` lắng nghe topic `transaction-events`:
  - Sự kiện `TRANSFER_COMPLETED`: Tự động sinh ra 2 thông báo biến động số dư:
    1. Thông báo trừ tiền (DEBIT_ALERT) cho tài khoản gửi.
    2. Thông báo nhận tiền (CREDIT_ALERT) cho tài khoản nhận.
  - Sự kiện `TRANSFER_FAILED_COMPENSATED`: Tự động sinh ra thông báo bồi hoàn (ROLLBACK_ALERT) cho khách hàng biết tiền đã về tài khoản.

---

## 9. Cấu trúc mã nguồn & Danh sách Module

```
Session18_Mini_Project/
├── build.gradle                                # Root build script + BOM + Jacoco config
├── settings.gradle                             # Định nghĩa 9 module microservices
├── docker-compose.yml                          # Container hạ tầng: Kafka, Zookeeper, Redis
├── RikkeiBank_API.postman_collection.json      # Trọn bộ 25+ API Postman kiểm thử qua Gateway
│
├── common-dto/                                 # Thư viện dùng chung DTO, Enums, Event
│   └── src/main/java/com/rikkeibank/common/
│       ├── dto/ (ApiResponse, ErrorResponse, CustomerDto, AccountDto, TransactionDto, AuthDto...)
│       ├── enums/ (Role, AccountStatus, TransactionStatus, TransactionType)
│       └── event/ (TransactionEvent)
│
├── discovery-server/ (Port 8761)               # Netflix Eureka Service Registry & Dashboard
├── config-server/ (Port 8888)                  # Spring Cloud Config Server (Tập trung cấu hình)
│   └── src/main/resources/shared-config/
│
├── api-gateway/ (Port 8080)                    # Spring Cloud Gateway
│   ├── filter/JwtAuthenticationFilter.java     # Kiểm tra Token & Tra cứu Redis Blacklist
│   └── security/JwtUtils.java
│
├── identity-service/ (Port 8081)               # Quản lý Đăng nhập, JWT, Refresh Token & Revocation
│   ├── entity/ (User, RefreshToken)
│   ├── service/ (AuthService, TokenBlacklistService)
│   └── controller/AuthController.java
│
├── customer-service/ (Port 8082)               # Quản lý Customer & Staff Catalog, Redis Cache-Aside
│   ├── entity/ (Customer, Staff)
│   ├── service/ (CustomerService, StaffService)
│   └── controller/ (CustomerController, StaffController)
│
├── account-service/ (Port 8083)                # Quản lý Account Type & Bank Accounts, Debit/Credit/Refund
│   ├── entity/ (AccountType, Account)
│   ├── service/AccountService.java
│   └── controller/ (AccountController, AccountTypeController)
│
├── transaction-service/ (Port 8084)            # Saga Orchestrator, Circuit Breaker, Kafka Producer
│   ├── client/AccountServiceClient.java        # OpenFeign gọi sang account-service
│   ├── service/TransactionSagaService.java     # Điều phối Saga & Bồi hoàn Rollback
│   ├── event/TransactionEventProducer.java     # Phát hành tin nhắn Kafka
│   └── controller/TransactionController.java
│
└── notification-service/ (Port 8085)           # Kafka Consumer, Biến động số dư nợ/có
    ├── entity/Notification.java
    ├── service/NotificationService.java        # @KafkaListener consumer
    └── controller/NotificationController.java
```

---

## 10. Hướng dẫn Khởi chạy & Kịch bản Kiểm thử minh chứng

### 10.1. Kiểm tra môi trường & Biên dịch
Toàn bộ dự án đã được cấu hình tương thích hoàn toàn với **Java 21** và **Gradle 9.3**:
```bash
# Kiểm tra biên dịch và chạy trọn bộ Unit Test + Jacoco
./gradlew test jacocoTestReport
```
Kết quả kiểm tra code coverage Jacoco có sẵn tại các thư mục:
- `account-service/build/reports/jacoco/test/html/index.html`
- `customer-service/build/reports/jacoco/test/html/index.html`
- `identity-service/build/reports/jacoco/test/html/index.html`
- `transaction-service/build/reports/jacoco/test/html/index.html`

### 10.2. Khởi chạy hạ tầng qua Docker Compose (Tuỳ chọn)
```bash
docker compose up -d
```
Khởi chạy Zookeeper (2181), Kafka (9092), Redis (6379).

### 10.3. Thứ tự khởi chạy các Microservice
Khởi chạy từng module theo thứ tự phụ thuộc:
1. `discovery-server`: `./gradlew :discovery-server:bootRun` (Port 8761 - Eureka Dashboard: http://localhost:8761)
2. `config-server`: `./gradlew :config-server:bootRun` (Port 8888)
3. `identity-service`: `./gradlew :identity-service:bootRun` (Port 8081)
4. `customer-service`: `./gradlew :customer-service:bootRun` (Port 8082)
5. `account-service`: `./gradlew :account-service:bootRun` (Port 8083)
6. `transaction-service`: `./gradlew :transaction-service:bootRun` (Port 8084)
7. `notification-service`: `./gradlew :notification-service:bootRun` (Port 8085)
8. `api-gateway`: `./gradlew :api-gateway:bootRun` (Port 8080)

### 10.4. Các Kịch bản Kiểm thử trên Postman qua API Gateway (`http://localhost:8080`)

Import file `RikkeiBank_API.postman_collection.json` vào Postman:

#### Kịch bản 1: Đăng nhập & Duy trì phiên (Seamless Login & Token Revocation)
1. Gửi request `1.1 Login as ADMIN`: Nhận Access Token của Quản trị viên (lưu vào `admin_token`).
2. Gửi request `1.3 Login as CUSTOMER`: Nhận Access Token (1h) và Refresh Token (7 ngày).
3. Gửi request `1.4 Refresh Token`: Gửi Refresh Token để cấp phát Access Token mới không cần mật khẩu.
4. Gửi request `1.6 Force Logout`: ADMIN ép buộc thu hồi quyền truy cập của một token hoặc user. Token lập tức bị blacklist trên Redis và không thể gọi bất kỳ API nào sau đó (trả về 401 Unauthorized).

#### Kịch bản 2: Tra cứu danh mục & Chứng minh Redis Cache-Aside
1. Gửi request `2.2 Get Customer By ID`:
   - Lần gọi 1: `customer-service` log thông báo `--- [CACHE MISS] Fetching customer ID 1 from Database ---`.
   - Lần gọi 2: Phản hồi trả về ngay lập tức, console không ghi nhận thêm truy vấn CSDL (Cache Hit từ Redis).
2. Gửi request `2.4 Update Customer`: Kích hoạt `@CachePut`, CSDL và Redis cache được làm mới đồng thời.

#### Kịch bản 3: Chuyển khoản thành công qua Saga (Happy Path)
1. Gửi request `4.1 SAGA Transfer SUCCESS`:
   - Tài khoản nguồn: `1001000001` (Số dư ban đầu: 10,000,000 VND).
   - Tài khoản đích: `1001000002` (Số dư ban đầu: 5,000,000 VND).
   - Số tiền: 500,000 VND.
2. Kiểm tra kết quả:
   - Giao dịch trả về HTTP 200 `COMPLETED`.
   - Tài khoản `1001000001` còn: 9,500,000 VND.
   - Tài khoản `1001000002` tăng lên: 5,500,000 VND.
   - `notification-service` sinh 2 bản ghi thông báo biến động: Trừ tiền người gửi và Cộng tiền người nhận.

#### Kịch bản 4: Chuyển khoản thất bại có Bồi hoàn tự động (Saga Compensating Rollback)
1. Gửi request `4.2 SAGA Transfer ROLLBACK`:
   - Đặt cờ `simulateFailureAtTarget: true` để mô phỏng lỗi xảy ra tại bước cộng tiền đích sau khi đã trừ tiền tài khoản nguồn.
2. Kiểm tra kết quả:
   - Hệ thống tự động kích hoạt Compensating Transaction: Hoàn trả lại 300,000 VND vào tài khoản nguồn.
   - Giao dịch trả về trạng thái `COMPENSATED`.
   - Số dư tài khoản nguồn `1001000001` không bị du lệch, được giữ nguyên vẹn 9,500,000 VND.
3. Gửi request `4.3 View SAGA Audit Step Logs`: Xem minh chứng 3 bước phân tán:
   - Bước 1: `DEBIT_SOURCE` - `SUCCESS`.
   - Bước 2: `CREDIT_TARGET` - `FAILED`.
   - Bước 3: `COMPENSATE_REFUND` - `COMPENSATED` (Khôi phục số dư).

#### Kịch bản 5: Kháng lỗi hệ thống với Resilience4j Circuit Breaker
- Khi `account-service` bị ngắt hoặc không khả dụng, gọi request chuyển khoản sẽ lập tức kích hoạt hàm fallback của Circuit Breaker:
  - Trả về mã lỗi an toàn: `"Circuit Breaker Active: Account service is currently unavailable or unstable. Request rejected safely to avoid cascading failure."`
  - Ngăn ngừa tình trạng treo luồng và sập lan truyền toàn bộ hệ thống API Gateway.
