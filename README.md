# RikkeiBank API - Microservices Architecture (Spring Boot & Cloud)

Dự án Backend Microservice phân tán cho Ngân hàng số **RikkeiBank API** (Fintech) với các tiêu chuẩn chất lượng cao:
- **Kiến trúc**: Microservices (MSA), Database-per-service
- **Công nghệ**: Java 21, Spring Boot 3.3.4, Spring Cloud 2023.0.3, Spring Cloud Gateway, Eureka Server, Spring Cloud Config
- **Giao dịch phân tán**: Saga Orchestrator Pattern (Happy Path & Compensating Rollback)
- **Kháng lỗi**: Resilience4j Circuit Breaker (3 trạng thái: CLOSED, OPEN, HALF_OPEN)
- **Distributed Caching**: Spring Cache + Redis (Chiến lược Cache-Aside)
- **Event-Driven Asynchronous**: Apache Kafka (Topic: `transaction-events`) + Notification Service
- **Bảo mật & Phiên**: JWT, Long-lived Session qua Refresh Token, ADMIN Force-logout qua Redis Blacklist
- **Kiểm thử chất lượng**: JUnit 5 + Mockito + Jacoco Code Coverage

## Tài liệu chi tiết
- Chi tiết phân tích thiết kế, sơ đồ kiến trúc, kịch bản nghiệp vụ: Xem file [PROJECT_DOCUMENTATION.md](file:///d:/RIKKEI/Microservice_In_action/Session18_Mini_Project/PROJECT_DOCUMENTATION.md)
- Bộ sưu tập Postman Collection đầy đủ 25+ API qua Gateway: Xem file [RikkeiBank_API.postman_collection.json](file:///d:/RIKKEI/Microservice_In_action/Session18_Mini_Project/RikkeiBank_API.postman_collection.json)

## Lệnh kiểm thử & Báo cáo chất lượng
```bash
./gradlew test jacocoTestReport
```

Báo cáo kiểm thử HTML (Jacoco Coverage) được xuất tự động tại:
- `account-service/build/reports/jacoco/test/html/index.html`
- `customer-service/build/reports/jacoco/test/html/index.html`
- `identity-service/build/reports/jacoco/test/html/index.html`
- `transaction-service/build/reports/jacoco/test/html/index.html`
