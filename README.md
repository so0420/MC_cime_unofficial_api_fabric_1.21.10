# Jamku CIME Connector

CIME(ci.me) 후원 알림을 마인크래프트 채팅으로 전달하는 Fabric 모드입니다.

## 명령어

### 일반 플레이어

| 명령어 | 설명 |
|---|---|
| `/cime connect <슬러그>` | CIME 채널에 연결합니다. |
| `/cime disconnect` | 연결을 해제합니다. |
| `/cime status` | 현재 연결 상태를 확인합니다. |

### OP 전용 (권한 레벨 2 이상)

| 명령어 | 설명 |
|---|---|
| `/cime op connect <플레이어> <슬러그>` | 다른 플레이어를 CIME 채널에 연결합니다. |
| `/cime test <플레이어> <후원액>` | 테스트 후원 메시지를 전송합니다. |

### 사용 예시

```
/cime connect smong
/cime status
/cime disconnect
/cime op connect Steve smong
/cime test Steve 10000
```

## 동작 방식

- 채널 연결 시 CIME WebSocket에 접속하여 후원 이벤트를 수신합니다.
- 후원이 감지되면 `[CIME] 1,000원 후원 받았습니다.` 형태로 채팅에 표시됩니다.
- 플레이어가 서버에서 나가면 연결이 자동으로 끊기고, 다시 접속하면 자동 재연결됩니다.
- 방송이 재시작되면 자동으로 감지하여 재연결합니다.
