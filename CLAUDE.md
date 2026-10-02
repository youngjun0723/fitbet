# FitBet (핏벳)

친구 전용 모바일 운동 인증 & 벌금 배틀 웹 서비스. 1일 1회 사진 인증, 미인증 시 1,000원 가상 벌금, 주간 정산 리포트.

## 요구사항 문서
전체 기획·도메인 룰·ERD·비즈니스 로직·화면·API는 아래 PRD가 기준이다. 기능을 구현하거나 설계를 바꾸기 전에 반드시 먼저 읽는다.

@docs/PRD.md

PRD와 다르게 구현해야 할 이유가 생기면 코드부터 고치지 말고 먼저 알려주고, 합의되면 `docs/PRD.md`도 함께 수정한다.

## 기술 스택
- Java 17, Spring Boot 3.x, Spring Data JPA, Spring Validation
- 스케줄러: Spring `@Scheduled` (zone = `Asia/Seoul`)
- DB: H2 (개발/테스트), MariaDB/MySQL (운영)
- 화면: Thymeleaf + Tailwind CSS (CDN) + Vanilla JS, 모바일 390px 기준
- 빌드: Gradle

## 자주 쓰는 명령어
- 빌드: `./gradlew build`
- 실행: `./gradlew bootRun`
- 테스트: `./gradlew test`
- (Windows 터미널이면 `gradlew.bat ...`)

## 패키지 구조
루트 패키지 `com.fitbet` 아래 도메인별로 나눈다: `user`, `room`, `challenge`, `streak`, `penalty`, `scheduler`, `common`. 자세한 내용은 PRD 3.1 참고.

## 꼭 지킬 규칙
- 날짜/시간은 항상 `Asia/Seoul` 기준. `LocalDate.now()`를 직접 쓰지 말고 `Clock`을 주입받아 테스트에서 날짜를 고정할 수 있게 한다.
- 하루 1인증, 하루 1벌금은 애플리케이션 체크 + DB UNIQUE 제약 두 겹으로 막는다.
- 결제/송금 API는 연동하지 않는다. 벌금은 가상 장부(PenaltyLog)에만 기록한다.
- Streak 갱신, 마감 스케줄러, 정산 로직에는 단위 테스트를 같이 작성한다.
- PRD 2.6의 미결정 정책은 확정 전까지 "MVP 제안" 값으로 구현한다.

## 작업 방식
- 나는 백엔드 기본기를 다지는 중이다. 코드를 작성할 때 왜 그렇게 설계했는지(트랜잭션 범위, 인덱스, 연관관계 매핑 등) 짧게 설명해 준다.
- 한 번에 큰 덩어리를 만들지 말고 마일스톤(PRD 9장) 단위로 작게 나눠 진행한다.
