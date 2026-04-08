<p align="center">
  <img src="desktop/src/main/resources/icons/icon.png" width="128" height="128" alt="Travel Plan Logo" />
</p>

<h1 align="center">Docker Remote Orchestrator</h1>

<p align="center">
  여러 원격 머신의 Docker 컨테이너를 하나의 대시보드에서 관리하는 크로스 플랫폼 데스크톱 애플리케이션입니다.<br>
</p>

---

[English](README.md)

## 주요 기능

- **호스트 모드**: 서버를 시작하고 호스트 코드를 원격 노드에 공유
- **클라이언트 모드**: 호스트 코드를 입력하여 호스트에 연결
- **실시간 대시보드**: 연결된 모든 노드의 컨테이너를 실시간으로 모니터링
- **컨테이너 제어**: 원격으로 컨테이너 시작, 중지, 재시작, 삭제
- **자동 업데이트**: 시작 시 새 버전 확인
- **TLS 지원**: 호스트-클라이언트 간 암호화 통신
- **Webhook 연동**: 컨테이너 이벤트에 대한 HTTP 웹훅 알림

## 아키텍처

```
┌──────────┐     WebSocket     ┌──────────┐
│  Desktop │◄─────────────────►│  Server  │
│   (UI)   │                   │  (Ktor)  │
└──────────┘                   └────┬─────┘
                                    │
                          ┌─────────┼─────────┐
                          ▼         ▼         ▼
                      ┌───────┐ ┌───────┐ ┌───────┐
                      │Client │ │Client │ │Client │
                      │Node 1 │ │Node 2 │ │Node 3 │
                      └───┬───┘ └───┬───┘ └───┬───┘
                          │         │         │
                        Docker    Docker    Docker
```

## 다운로드

[Releases](https://github.com/shimhyuck/docker-remote-orchestrator/releases) 페이지에서 최신 버전을 다운로드하세요.

| 플랫폼 | 형식 |
|--------|------|
| macOS (Apple Silicon) | `.dmg` |
| macOS (Intel) | `.dmg` |
| Windows (x64) | `.msi` |

### macOS - Gatekeeper 안내

Apple 개발자 인증서로 서명되지 않았기 때문에 macOS에서 실행이 차단될 수 있습니다. 해결 방법:

**방법 1**: 앱을 우클릭 → **열기** 선택 → 대화상자에서 **열기** 클릭

**방법 2**: 설치 후 터미널에서 실행:
```bash
xattr -cr /Applications/DRO.app
```

**방법 3**: **시스템 설정 → 개인정보 보호 및 보안** → **확인 없이 열기** 클릭

## 소스에서 빌드

### 사전 요구사항

- JDK 17+
- Docker (클라이언트 노드 기능에 필요)

### 빌드

```bash
# 클론
git clone https://github.com/shimhyuck/docker-remote-orchestrator.git
cd docker-remote-orchestrator

# 데스크톱 앱 실행
./gradlew :desktop:run

# 현재 OS용 패키징
./gradlew :desktop:packageDmg   # macOS
./gradlew :desktop:packageMsi   # Windows
./gradlew :desktop:packageDeb   # Linux
```

## 사용법

### 호스트 (서버)로 사용

1. DRO 실행
2. **Start Host** 클릭
3. 생성된 **호스트 코드**를 원격 노드에 공유
4. 대시보드에서 연결된 모든 노드 모니터링

### 클라이언트 (원격 노드)로 사용

1. DRO 실행
2. **Connect as Client** 클릭
3. **호스트 코드**와 서버 주소 입력
4. 해당 노드의 Docker 컨테이너가 호스트 대시보드에 표시

### 독립 클라이언트 (헤드리스)

```bash
./gradlew :client:run --args="<server-host> <port>"

# TLS 사용
./gradlew :client:run --args="--tls <server-host> <port>"
```

### 독립 서버

```bash
./gradlew :server:run

# 커스텀 포트 및 TLS
./gradlew :server:run --args="--port 9090 --tls"
```

## 기술 스택

- **UI**: Jetpack Compose Desktop (Material 3)
- **서버/클라이언트**: Ktor (WebSocket)
- **Docker**: docker-java
- **언어**: Kotlin
- **직렬화**: kotlinx.serialization

## 프로젝트 구조

```
dro/
├── common/    # 공유 모델, 프로토콜, 보안 유틸리티
├── server/    # Ktor WebSocket 서버, 세션 관리
├── client/    # Docker 연동, 호스트 연결
└── desktop/   # Compose Desktop UI
```

## 라이선스

[MIT](LICENSE)
