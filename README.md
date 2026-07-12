# 포인트시스템

적립 / 적립취소 / 사용 / 사용취소를 지원하는 무료 포인트 시스템 API입니다.
특정 시점에 적립된 포인트를 1원 단위까지 추적하며, 어떤 주문에서 소진·복원되었는지
식별할 수 있습니다.

### 핵심 특징

- **원장 기반** — 적립 1건 단위로 잔액을 관리해 어떤 주문에서 얼마가
  소진·복원됐는지 1원 단위까지 역추적.
- **멱등성** — 모든 변경 API가 `Idempotency-Key`로 중복 요청을 1회만
  반영. 네트워크 재시도에도 이중 적립/사용 없음.
- **동시성 안전** — 사용자별 비관적 락으로 한도 우회·이중지출 차단, 멱등 레코드
  유니크 제약으로 동시 중복 요청 차단.
- **외부화된 정책** — 적립 한도·만료 정책을 재빌드 없이 설정 파일로 제어.
- **결정적 소비 순서** — 수기지급 우선 → 만료 임박 순 소진.

## 기술 스택

- Java 21
- Spring Boot 3.5.x (Web, Data JPA, Validation)
- H2 (in-memory)
- Gradle (Kotlin DSL), JUnit 5

## 빌드 & 실행

```bash
# 빌드 + 전체 테스트
./gradlew build

# 테스트만
./gradlew test

# 애플리케이션 실행 (http://localhost:8080)
./gradlew bootRun
```

## 설정값 (하드코딩이 아닌 방법으로 한도 제어)

한도/만료 정책은 소스 상수가 아니라 `application.properties`에서 주입

## 도메인 모델

원장 방식으로 설계했습니다.

- **Point** — 적립 1건(명세의 "특정 시점에 적립된 포인트"). 생성 트랜잭션 id(`transactionId`), 최초
  금액, 가변 잔액, 출처(`sourceType`), 만료일, 상태를 가진다. 잔액이 변하는 유일한
  테이블.
- **PointTransaction** — 모든 행위(EARN/CANCEL_EARN/USE/CANCEL_USE/RESTORE)의
  생성 원장.
- **PointUsageDetail** — 하나의 USE가 어떤 Point에서 얼마를 소진했는지 연결. 사용취소
  시 복원 대상·순서·금액의 근거.
- **UserPointAccount** — 사용자별 비관적 락 앵커(동시성 제어).
- **IdempotencyRecord** — 멱등키별 첫 성공 응답을 JSON으로 저장하는 독립 테이블. 
  같은 키 재요청 시 저장된 응답을 그대로 반환해 이중 적용을 막는다.

각 행위는 `PointTransaction.id`로 순번을 가지며, 적립 포인트는 자신을 생성한 트랜잭션 id를
`transactionId`로 참조합니다.

auto_increment id를 순번 원천으로 사용

### ERD

`src/main/resources/erd/erd.png`

## 테스트

```bash
./gradlew test
```