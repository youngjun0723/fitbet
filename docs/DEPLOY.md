# FitBet 배포 가이드

친구 몇 명이 쓰는 소규모 서비스 기준. **서버 한 대 + Docker Compose(앱 + MariaDB + Caddy)**.

## 1. 왜 이 구성인가

| 조건 | 이유 |
|---|---|
| 서버가 **항상 켜져 있어야** 함 | 00:00:05 마감 스케줄러가 돌아야 벌금이 기록된다. 요청이 없으면 잠드는 무료 PaaS(Render 무료 등)는 부적합 |
| **디스크가 유지**돼야 함 | 인증 사진을 로컬 디스크에 저장한다(`StorageService`). 재배포 때 디스크가 초기화되는 PaaS는 사진이 사라짐 |
| **HTTPS** 필요 | 정산 탭의 [복사하기](`navigator.clipboard`)는 보안 연결에서만 동작. 세션 쿠키도 `Secure` |
| 사용자 수 10명 내외 | 앱 + DB를 한 서버에 같이 올려도 충분. 관리형 DB(RDS 등)는 과한 비용 |

### 추천 서버 (택1)

| 옵션 | 비용 | 장점 | 단점 |
|---|---|---|---|
| **Oracle Cloud Always Free** (Ampere ARM VM) | 0원 | 무료 한도가 넉넉함(메모리 수 GB), 기간 제한 없음 | 가입·인스턴스 생성이 까다로울 때가 있음(카드 인증, 리전 자원 부족) |
| 저가 VPS (AWS Lightsail, Vultr, DigitalOcean 등 1~2GB) | 월 5~7달러 | 생성이 쉽고 안정적 | 유료 |

- 두 옵션 모두 이 문서의 절차가 그대로 적용된다(이미지가 x86/ARM 모두 지원).
- 메모리 1GB 서버라면 아래 "스왑 추가"를 꼭 할 것.
- 도메인: 무료 **DuckDNS** 서브도메인(`xxx.duckdns.org`)이면 충분. Caddy가 인증서를 자동 발급한다.

## 2. 처음 설치

### 2-1. 서버 준비
1. Ubuntu 22.04/24.04 VM 생성.
2. 방화벽(보안 그룹)에서 **22, 80, 443** 인바운드 허용. (Oracle은 VCN 보안 목록 + VM 내부 iptables 둘 다 열어야 함)
3. DuckDNS에서 서브도메인 생성 → 서버 공인 IP 입력.

### 2-2. Docker 설치
```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER   # 재접속 후 sudo 없이 docker 사용
```

### 2-3. (메모리 1GB 서버만) 스왑 추가
```bash
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

### 2-4. 앱 실행
```bash
git clone https://github.com/youngjun0723/fitbet.git
cd fitbet
cp .env.example .env
nano .env      # SITE_ADDRESS, DB 비밀번호 2개 변경 (openssl rand -base64 24 로 생성 추천)
docker compose up -d --build
```

### 2-5. 확인
```bash
docker compose ps                 # db(healthy), app, caddy 모두 Up
docker compose logs -f app        # "Started FitbetApplication" 확인
```
브라우저에서 `https://SITE_ADDRESS` 접속 → 닉네임 로그인 → 방 만들기.

## 3. 업데이트 (새 코드 배포)
```bash
cd fitbet
git pull
docker compose up -d --build app   # 앱만 다시 빌드/재시작. DB·사진은 볼륨에 그대로
```
- 재시작 직후 스케줄러가 **최근 7일을 따라잡기 마감**하므로, 자정 즈음 배포해도 마감이 빠지지 않는다.
- 세션이 메모리에 있어서 재시작하면 모두 다시 로그인해야 한다(닉네임만 입력하면 됨).

## 4. 백업 / 복원

### 백업 (매일 자동)
```bash
chmod +x deploy/backup.sh
crontab -e
# 추가: 매일 04:00
0 4 * * * /home/ubuntu/fitbet/deploy/backup.sh >> /home/ubuntu/fitbet/backups/backup.log 2>&1
```
`backups/`에 `db-*.sql.gz`(DB)와 `uploads-*.tgz`(사진)가 쌓이고 14일 지난 것은 자동 삭제.
서버가 통째로 날아가는 경우를 대비해 가끔 `scp`로 내 PC에도 받아 두자.

### 복원
```bash
gunzip -c backups/db-YYYYMMDD-HHMM.sql.gz | docker compose exec -T db sh -c 'mariadb -u root -p"$MARIADB_ROOT_PASSWORD" fitbet'
docker compose run --rm --no-deps --user root -v "$PWD/backups:/backup" --entrypoint tar app \
  xzf /backup/uploads-YYYYMMDD-HHMM.tgz -C /data
```

## 5. 운영 설정 요약 (`application-prod.yml`)

| 설정 | 값 | 이유 |
|---|---|---|
| DB 접속 | 환경변수 `DB_URL/DB_USERNAME/DB_PASSWORD` | 비밀번호를 코드·git에 남기지 않음 |
| `ddl-auto` | `update` | 첫 실행 때 테이블 생성, 이후 없는 컬럼만 추가. 스키마 변경이 잦아지면 Flyway 도입 |
| H2 콘솔 | 꺼짐 | 운영 DB 노출 방지 |
| 세션 쿠키 | `Secure`, `HttpOnly`, `SameSite=Lax`, 30일 | HTTPS 전용, JS 접근 차단 |
| 프록시 헤더 | `forward-headers-strategy: framework` | Caddy 뒤에서 https/도메인을 올바르게 인식 (리다이렉트 주소) |
| DB 포트 | 외부에 열지 않음 | compose 내부 네트워크에서 앱만 접근 |

## 6. 알아둘 한계
- **닉네임 로그인(PRD 6.4)**: 비밀번호가 없어서 닉네임을 아는 사람은 그 사람으로 로그인할 수 있다. 친구끼리라 MVP에서는 허용했지만, 주소를 공개적으로 퍼뜨리지 말 것. 필요해지면 방 초대 코드와 함께 간단한 PIN을 추가하는 방향을 검토.
- **사진 URL**: 주소(UUID)를 알면 방 멤버가 아니어도 볼 수 있다.
- **서버 1대 전제**: 여러 대로 늘리면 스케줄러가 서버마다 돈다(벌금은 멱등이라 중복되지 않음). 그때는 ShedLock 도입.
