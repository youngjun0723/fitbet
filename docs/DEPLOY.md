# FitBet 배포 가이드

친구 몇 명이 쓰는 소규모 서비스 기준. **집 PC(Windows) + Docker Desktop + Cloudflare Tunnel**로 운영한다.
(공인 IP가 있는 VPS로 옮길 때는 [B. VPS + Caddy](#b-vps--caddy-대안)를 참고)

## 0. 구성 한눈에 보기

```
친구 휴대폰 ──HTTPS──▶ Cloudflare ◀──(PC에서 먼저 연결한 터널)── cloudflared ──▶ app:8080 ──▶ db(MariaDB)
                                                               └────────── 집 PC의 Docker ──────────┘
```

| 조건 | 이 구성에서의 해결 |
|---|---|
| 자정 스케줄러가 돌아야 함 | PC가 켜져 있으면 실행. 꺼져 있었으면 다시 켜질 때 **최근 7일 따라잡기**로 빠진 마감을 채움 |
| 인증 사진 유지 | Docker 볼륨(`uploads`)에 저장 → 재배포해도 유지 |
| HTTPS (복사하기 버튼, Secure 쿠키) | Cloudflare가 인증서 처리 |
| 공유기 설정 | 불필요. cloudflared가 **바깥으로 나가는 연결**만 쓰기 때문에 포트포워딩·방화벽 개방 없음 |

### 비용
- 도메인: 연 1~2만 원 수준 (Cloudflare에서 구매하거나, 가진 도메인의 네임서버를 Cloudflare로 변경). 가격은 구매 전에 확인할 것.
- Cloudflare Tunnel / Access: 무료 플랜으로 충분.
- 전기료: PC를 24시간 켜 두는 비용. 사양에 따라 다르지만 월 수천 원 이상 들 수 있다.

> `trycloudflare.com` 임시 주소(도메인 없이 쓰는 빠른 터널)는 재시작할 때마다 주소가 바뀌어서 이 용도로는 맞지 않는다.

---

## A. 집 PC + Cloudflare Tunnel (현재 방식)

### A-1. PC 설정 (한 번)
1. **절전 끄기**: 설정 > 시스템 > 전원 > "절전 모드로 전환"을 **안 함**. (화면 끄기는 괜찮다)
2. **Windows 업데이트 재부팅 시간**: 설정 > Windows 업데이트 > 고급 옵션 > 사용 시간을 넓게 잡아 한밤중 자동 재부팅을 줄인다.
3. **Docker Desktop 설치**: https://www.docker.com/products/docker-desktop/ (WSL 2 백엔드 사용)
   - 설치 후 Settings > General > **Start Docker Desktop when you sign in to your computer** 체크
   - (선택) 메모리가 부족하면 `%UserProfile%\.wslconfig`에 아래를 넣어 WSL 메모리 상한을 둔다
     ```ini
     [wsl2]
     memory=3GB
     ```

### A-2. 배포용 폴더 따로 받기 (한 번)
개발 폴더(`C:\fitbet`)와 **분리**한다. 커밋하지 않은 작업 중인 코드가 그대로 배포되는 사고를 막기 위해서다.
```powershell
git clone https://github.com/youngjun0723/fitbet.git C:\fitbet-deploy
cd C:\fitbet-deploy
copy .env.example .env
```

### A-3. Cloudflare 터널 만들기 (한 번)
메뉴 이름은 Cloudflare 화면 개편에 따라 조금 다를 수 있다.
1. Cloudflare 가입 → 도메인을 Cloudflare에 추가(또는 Cloudflare에서 구매).
2. **Zero Trust** 대시보드 → **Networks > Tunnels** → **Create a tunnel** → 유형 **Cloudflared** → 이름 `fitbet`.
3. 설치 명령 화면에 나오는 **토큰**(`eyJ...`로 시작하는 긴 문자열)만 복사한다.
   설치 명령 자체는 실행하지 않는다 — Docker의 `tunnel` 컨테이너가 대신 실행한다.
4. **Public Hostname** 추가:
   - Subdomain: `fitbet` / Domain: 내 도메인 → 접속 주소는 `https://fitbet.내도메인`
   - Service Type: `HTTP` / URL: `app:8080`  ← compose 안의 서비스 이름과 포트

### A-4. `.env` 작성 (한 번)
`C:\fitbet-deploy\.env`를 메모장으로 열어 수정:
```dotenv
COMPOSE_PROFILES=tunnel
TUNNEL_TOKEN=eyJ...복사한토큰...
DB_USERNAME=fitbet
DB_PASSWORD=(긴 랜덤 문자열)
DB_ROOT_PASSWORD=(다른 긴 랜덤 문자열)
```
랜덤 문자열은 Git Bash에서 `openssl rand -base64 24`로 만들 수 있다. `SITE_ADDRESS`는 VPS용이라 무시해도 된다.

### A-5. 실행
```powershell
cd C:\fitbet-deploy
docker compose up -d --build
```

### A-6. 확인
```powershell
docker compose ps            # db(healthy), app, tunnel 이 Up. caddy는 안 뜨는 게 정상
docker compose logs -f app   # "Started FitbetApplication" + 에러 없음 (Ctrl+C로 빠져나옴)
docker compose logs tunnel   # "Registered tunnel connection" 이 보이면 연결 성공
```
- Cloudflare 대시보드의 터널 상태가 **HEALTHY**인지 확인.
- 휴대폰 **와이파이를 끄고 LTE로** `https://fitbet.내도메인` 접속 → 로그인 → 방 만들기 → 사진 인증.

### A-7. 재부팅되면 어떻게 되나
Windows 로그인 → Docker Desktop 자동 시작 → 컨테이너들 자동 재시작(`restart: unless-stopped`) → 앱이 뜨면서 **최근 7일 따라잡기**.
- 즉 **Windows에 로그인해야** 서비스가 다시 살아난다. 정전·업데이트 재부팅 후에는 PC에 로그인만 해 주면 된다.

### A-8. 백업 (매일 자동)
작업 스케줄러(`taskschd.msc`) → **기본 작업 만들기**:
- 트리거: 매일 04:00
- 동작: 프로그램 시작
  - 프로그램: `C:\Program Files\Git\bin\bash.exe`
  - 인수: `-lc "/c/fitbet-deploy/deploy/backup.sh >> /c/fitbet-deploy/backups/backup.log 2>&1"`

`C:\fitbet-deploy\backups\`에 `db-*.sql.gz`(DB)와 `uploads-*.tgz`(사진)가 쌓이고 14일 지난 것은 지워진다.
**같은 PC 안의 백업은 PC 고장에 무력하다** → 가끔 `backups` 폴더를 OneDrive/Google Drive나 외장 디스크로 복사해 두자.

### A-9. 업데이트 (새 코드 배포)
```powershell
cd C:\fitbet-deploy
git pull
docker compose up -d --build app
```

### A-10. (강력 추천) Cloudflare Access로 친구만 들어오게
FitBet은 비밀번호 없는 닉네임 로그인이라, 주소를 아는 누구나 아무 닉네임으로 들어올 수 있다.
Cloudflare Access를 앞에 두면 **허용한 이메일만** 사이트에 접근할 수 있다. (무료 플랜 50명까지)
1. Zero Trust → **Access > Applications** → **Add an application** → **Self-hosted**
2. Application domain: `fitbet.내도메인`
3. Policy: Action **Allow**, Include **Emails** → 친구들 이메일 입력
4. 친구는 처음 접속할 때 이메일로 받은 인증 코드를 한 번 입력 (Session Duration을 길게, 예: 1 month)

---

## B. VPS + Caddy (대안)
공인 IP가 있는 서버(Lightsail, Vultr 등)로 옮길 때. 코드는 그대로, `.env`만 다르다.
1. Ubuntu 서버 생성, 방화벽 22/80/443 허용, 도메인 A 레코드를 서버 IP로.
2. `curl -fsSL https://get.docker.com | sudo sh && sudo usermod -aG docker $USER` (재접속)
3. 메모리 1GB면 스왑 2GB 추가:
   ```bash
   sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
   echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
   ```
4. `git clone https://github.com/youngjun0723/fitbet.git && cd fitbet && cp .env.example .env`
5. `.env`: `COMPOSE_PROFILES=caddy`, `SITE_ADDRESS=fitbet.내도메인`, DB 비밀번호 2개
6. `docker compose up -d --build` → `docker compose ps`에 db, app, caddy가 Up
7. 백업: `crontab -e` → `0 4 * * * /home/ubuntu/fitbet/deploy/backup.sh >> /home/ubuntu/fitbet/backups/backup.log 2>&1`

---

## 공통: 복원
Git Bash(Windows) 또는 서버 셸에서, 배포 폴더로 이동 후:
```bash
gunzip -c backups/db-YYYYMMDD-HHMM.sql.gz | docker compose exec -T db sh -c 'mariadb -u root -p"$MARIADB_ROOT_PASSWORD" fitbet'
MSYS_NO_PATHCONV=1 docker compose run --rm --no-deps --user root -v "$(pwd -W 2>/dev/null || pwd)/backups:/backup" \
  --entrypoint tar app xzf /backup/uploads-YYYYMMDD-HHMM.tgz -C /data
```

## 공통: 운영 설정 요약 (`application-prod.yml`)

| 설정 | 값 | 이유 |
|---|---|---|
| DB 접속 | 환경변수 `DB_URL/DB_USERNAME/DB_PASSWORD` | 비밀번호를 코드·git에 남기지 않음 |
| `ddl-auto` | `update` | 첫 실행 때 테이블 생성, 이후 없는 컬럼만 추가. 스키마 변경이 잦아지면 Flyway 도입 |
| H2 콘솔 | 꺼짐 | 운영 DB 노출 방지 |
| 세션 쿠키 | `Secure`, `HttpOnly`, `SameSite=Lax`, 30일 | HTTPS 전용, JS 접근 차단 |
| 프록시 헤더 | `forward-headers-strategy: framework` | Cloudflare/Caddy 뒤에서 https·도메인을 올바르게 인식 |
| DB·앱 포트 | 외부에 열지 않음 | 터널(또는 Caddy)만 앱에 접근, DB는 앱만 접근 |

## 알아둘 한계
- **닉네임 로그인**: 비밀번호가 없다 → A-10 Cloudflare Access로 보완 추천.
- **사진 URL**: 주소(UUID)를 알면 방 멤버가 아니어도 볼 수 있다. (Access를 켜면 외부인은 사이트 자체에 못 들어옴)
- **집 PC가 꺼져 있는 동안**은 접속 불가. 마감은 다시 켜질 때 7일치까지 자동으로 채워진다.
