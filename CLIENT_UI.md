# 클라이언트 UI (로비 · 게임 방) — 정우진

`docs/README.md`(통신 프로토콜 v0.1)와 `data/cards.json`(카드 ID 대응표)에 맞춘 Swing 클라이언트 화면입니다.

## 파일 구성

```
src/gsg/client/
  ClientMain.java            시작점 (Run As → Java Application)
  net/ServerConnection.java  TCP 연결: JSON 한 줄 송수신, requestId 부여, 수신 스레드 → EDT 전달
  card/CardInfo.java         cards.json 항목 (cardId, month, variant, type, imagePath, printedValue)
  card/CardCatalog.java      카드 데이터·이미지 관리 (원본을 작게 줄여 캐시, back.png = 뒷면)
  ui/MainFrame.java          로비 ↔ 게임 방 전환, 서버 메시지를 type별로 각 화면에 전달
  ui/LobbyPanel.java         로비 화면
  ui/GameRoomPanel.java      게임 방 화면 (대기 / 게임)
  ui/ChatPanel.java          방 채팅 + 빠른 채팅
  ui/CardRow.java            카드 여러 장을 한 줄로 그리는 컴포넌트 (겹치기, 클릭, 강조)
  ui/Theme.java              공통 색·글꼴
src/gsg/testserver/
  MockServer.java            UI 시험용 간이 서버 (실제 서버 아님)
```

## Eclipse에서 실행

1. 저장소 루트(`data/`, `hwatu_image/`가 있는 폴더)를 Java 프로젝트로 열고, `src`를 소스 폴더로 지정합니다.
2. Gson jar를 Build Path에 추가합니다.
3. 서버 실행: `MockServer` → Run As → Java Application (포트 5000)
4. 클라이언트 실행: `ClientMain`을 두 번 실행해 창 2개를 띄웁니다.
5. 각 창에서 닉네임 입력 → 접속 → 한쪽은 방 만들기, 다른 쪽은 입장 → 둘 다 준비.

카드 데이터와 이미지는 클래스패스(`/data/cards.json`)를 먼저 찾고, 없으면 작업 폴더 기준 경로(`data/cards.json`)로 찾습니다.
Eclipse의 기본 작업 폴더가 프로젝트 루트이므로 별도 설정 없이 동작합니다.

## 화면별 기능과 사용하는 메시지

| 화면 | 기능 | 보내는 요청 | 받는 이벤트 |
|---|---|---|---|
| 로비 | 서버 주소·포트·닉네임 입력 후 접속 (닉네임 1~12자 검사) | `JOIN` | `ACK`(playerId), `ERROR`(NICKNAME_TAKEN 등) |
| 로비 | 방 목록 (제목, 인원, 대기/게임 중), 새로고침 | `LIST_ROOMS` | `ROOM_LIST` |
| 로비 | 방 만들기 (제목 1~30자), 입장 (더블클릭 가능, 게임 중·정원 초과 방은 입장 불가) | `CREATE_ROOM`, `JOIN_ROOM` | `ACK`(roomId) |
| 방-대기 | 자리 2개, 준비 상태, 준비/준비 취소 | `READY` | `ROOM_STATE` |
| 방-게임 | 상대: 뒷면 손패, 점수, 고 횟수, 종류별 획득패 | – | `GAME_START`, `GAME_STATE` |
| 방-게임 | 바닥패, 덱 남은 장수, 차례 안내 | – | `GAME_STATE` |
| 방-게임 | 내 손패 클릭으로 패 내기 (내 차례 + `WAIT_PLAY`일 때만) | `PLAY_CARD` | `ACK` / `ERROR` |
| 방-게임 | 먹을 패 선택: 후보 바닥패 노란 테두리, 나머지 어둡게 | `SELECT_CAPTURE` | `CHOICE_REQUIRED`(CAPTURE) |
| 방-게임 | 고/스톱 버튼 ("n고" 표시) | `DECIDE` | `CHOICE_REQUIRED`(GO_STOP) |
| 방-게임 | 결과 창 후 대기 화면으로 복귀 | – | `GAME_OVER`, `ROOM_STATE` |
| 방 공통 | 채팅, 빠른 채팅 6개, 입장·퇴장·시작·종료 안내 | `CHAT` | `CHAT_MESSAGE` |
| 방 공통 | 나가기 (게임 중이면 확인 창) | `LEAVE_ROOM` | `ACK` |

## 프로토콜 9장(Swing 주의점) 반영

- 소켓 연결과 수신은 별도 스레드, 화면 변경은 `SwingUtilities.invokeLater()`
- 버튼·카드 클릭 가능 여부는 서버가 보낸 `phase`, `currentPlayerId`, `pendingChoice`로만 판단
- 요청을 보낸 뒤 서버 응답(`GAME_STATE`/`ERROR`)이 올 때까지 다시 누를 수 없게 막음 (자동 재전송 없음)
- 같은 `choiceId`의 선택창은 한 번만 처리, `revision`이 더 낮은 오래된 상태는 무시
- 채팅은 `CHAT_MESSAGE`를 받았을 때만 표시 (중복 방지), HTML로 해석하지 않고 일반 텍스트로 삽입
- 보너스 패는 획득패의 피 묶음에 함께 표시

## 아직 없는 것 (서버·게임 로직 확정 후 추가)

- 게임 머니(100만 원 시작, 점당 1만 원 정산): 현재 프로토콜에 머니 필드가 없음 → 필드가 정해지면 플레이어 정보 줄에 표시
- 뻑·쪽·따닥·싹쓸이·폭탄·흔들기 등 특수 규칙 연출(이벤트 효과)
- 9월 술잔(국진) 열끗/쌍피 선택: 서버가 선택 종류(`kind`)를 정하면 GO_STOP과 같은 방식으로 버튼 추가

## MockServer 규칙 (시험용)

보너스 제외 48장, 손패 10장·바닥 8장 분배. 같은 월 2장이면 CAPTURE 선택, 낸 뒤 덱에서 1장 뒤집기.
간이 점수(광 3장↑, 열끗·띠 5장↑, 피 10장↑)가 3점 이상이면 고/스톱. 실제 점수 규칙과 다릅니다.
