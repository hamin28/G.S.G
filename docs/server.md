# 서버

네트워크 서버 코드는 `src/server`에 있으며 Java 21, Maven, Gson을 사용합니다. IntelliJ에서 모듈 의존성을 불러온 뒤 `server.ServerMain`을 실행하면 됩니다. 기본 포트는 `9999`이며, 첫 번째 실행 인자로 포트를 바꿀 수 있습니다. Swing 로비는 일부 버전에서 `5000`번 포트를 사용했으므로 클라이언트와 서버의 포트를 같게 설정해야 합니다.

## 통신 방식과 메시지 형식

클라이언트마다 TCP 연결 하나를 사용합니다. 메시지는 UTF-8 JSON 객체 한 개를 한 줄로 보내며 줄 끝은 LF(`\n`)입니다. CRLF도 허용합니다. 서버는 한 줄을 모두 받은 후 JSON으로 해석하며, 줄바꿈을 제외한 메시지가 64KiB를 초과하면 연결을 닫습니다.

클라이언트 요청 형식:

```json
{"type":"JOIN","requestId":"req-1","payload":{"nickname":"플레이어"}}
```

모든 요청에는 해당 연결에서 고유한 `requestId`와 객체 형식의 `payload`가 필요합니다. 보낼 데이터가 없는 경우에도 `payload`에는 `{}`를 넣습니다. 같은 연결에서 이미 사용한 `requestId`는 거절됩니다. 각 요청에는 같은 `requestId`를 담은 `ACK` 또는 `ERROR`가 응답으로 오며, 서버가 별도로 보내는 이벤트에는 `requestId`가 없습니다. 요청으로 발생한 이벤트보다 `ACK` 또는 `ERROR`가 먼저 전송됩니다.

## 구현된 요청

| 요청 | `payload` | 기능 |
|---|---|---|
| `JOIN` | `{"nickname":"플레이어"}` | 현재 연결에 닉네임을 등록합니다. |
| `LIST_ROOMS` | `{}` | 방 목록을 요청합니다. |
| `CREATE_ROOM` | `{"title":"연습방"}` | 방을 만들고 첫 번째 참가자로 입장합니다. |
| `JOIN_ROOM` | `{"roomId":"..."}` | 입장 가능한 방에 참가합니다. |
| `READY` | `{"ready":true}` 또는 `false` | 게임이 진행 중이 아닐 때 준비 상태를 변경하거나 취소합니다. |
| `SELECT_DEALER` | `{"selectionId":"...","drawNumber":1,"cardId":17}` | 첫 판의 선 선택에 사용할 카드를 제출합니다. |
| `GAME_ACTION` | 아래 게임 행동 참고 | 최신 상태 버전을 기준으로 게임 행동을 요청합니다. |
| `LEAVE_ROOM` | `{}` | 현재 참가 중인 방에서 나갑니다. |

플레이어 ID, 방 ID, 게임 ID, 선 선택 ID는 서버가 생성합니다. 한 연결은 한 번에 한 방에만 참가할 수 있고, 방 정원은 2명입니다. 닉네임은 앞뒤 공백을 제거한 뒤 1~12 유니코드 코드 포인트, 방 제목은 1~30 코드 포인트여야 합니다. 제어 문자는 허용하지 않습니다. 접속 중인 플레이어끼리는 닉네임이 중복될 수 없습니다.

## 응답과 방 이벤트

정상 요청에는 `ACK`, 잘못되었거나 현재 허용되지 않는 요청에는 `ERROR`가 응답합니다. `ERROR.payload`에는 오류 코드 `code`와 사용자에게 보여 줄 설명 `message`가 들어갑니다. 주요 오류 코드는 `INVALID_MESSAGE`, `DUPLICATE_REQUEST`, `NOT_JOINED`, `NICKNAME_TAKEN`, `NOT_IN_ROOM`, `ROOM_NOT_FOUND`, `ROOM_FULL`, `GAME_IN_PROGRESS`, `GAME_NOT_STARTED`, `INVALID_PHASE`, `NOT_YOUR_TURN`, `STALE_STATE`, `INVALID_SELECTION`입니다.

서버가 보내는 이벤트는 다음과 같습니다.

- `ROOM_LIST`: 방 목록, 인원수, 방 상태
- `ROOM_STATE`: 방 상태와 플레이어 ID, 닉네임, 자리, 준비 여부, 보유 잔액
- `DEALER_SELECTION`: 현재 선 선택 ID, 추첨 번호, 선택 후보, 남은 시간
- `GAME_START`, `GAME_STATE`: 수신 플레이어별 게임 상태
- `GAME_OVER`: 종료 사유, 승자, 정산 결과, 변경된 잔액, 다음 판 정보
- `ROOM_LEFT`: 방에서 제거된 플레이어에게 방 ID, 잔액, 사유 전달

방에 남아 있는 플레이어의 요청이 거절된 경우, 서버는 클라이언트가 상태를 다시 맞출 수 있도록 최신 `ROOM_STATE`와 해당 시점의 선 선택 정보 또는 게임 상태를 추가로 보낼 수 있습니다.

## 게임 행동과 상태

`GAME_ACTION` 요청에는 최신 `GAME_START` 또는 `GAME_STATE`에서 받은 `gameId`, `roundNumber`(또는 `roundId`), `version`을 그대로 넣고, `action`과 필요한 경우 `cardId`를 추가합니다.

```json
{"type":"GAME_ACTION","requestId":"req-6","payload":{"gameId":"room-1-round-1","roundNumber":1,"version":0,"action":"PLAY","cardId":17}}
```

지원하는 행동은 `PLAY`, `SERVICE`, `SHAKE`, `BOMB`, `DECK_ONLY`, `SELECT`, `GUKJIN_PI`, `GUKJIN_ANIMAL`, `GO`, `STOP`입니다. 이전 요청 형식인 `PLAY_CARD`, `SELECT_CAPTURE`, `CHOOSE_MATCH`도 처리되며, `DECIDE` 요청은 `decision` 값이 `GO` 또는 `STOP`이어야 합니다.

`GAME_START`와 `GAME_STATE`는 같은 구조이며, 수신자에 맞춘 상태를 담습니다. 주요 필드는 `gameId`, `roomId`, `roundId`, `roundNumber`, `version`, `mySeat`, `currentPlayerId`, `phase`, `ownHand`, `opponentHandCount`, `stockCount`, `floor`, 각 플레이어의 획득패와 점수, 선택 후보, 고 횟수, 이벤트, `remainingMillis`입니다. 상대방의 손패 ID와 덱 순서는 보내지 않습니다. 게임 단계는 `PLAY`, `HAND_CHOICE`, `STOCK_CHOICE`, `GUKJIN`, `GO_STOP`, `FINISHED`이며 종료 상태에서는 `currentPlayerId`가 `null`입니다.

행동 순서와 유효성은 서버가 판단합니다. 서버는 현재 차례, 단계, 카드 소유 여부, 선택 후보, 게임·판 식별자와 버전을 검사합니다. 정상 행동은 `ACK` 뒤에 각 플레이어에게 갱신된 게임 상태 또는 게임 종료 이벤트를 보냅니다. 오래되었거나 유효하지 않은 행동은 적용하지 않고 거절하며, 클라이언트에 최신 상태를 다시 보낼 수 있습니다. 방별 차례 진행과 정산 규칙은 `RoomRules` 및 게임 로직에서 처리합니다.

## 시간 초과, 연결 종료, 잔액

`ServerMain`은 100ms 간격으로 제한 시간 확인 작업을 시작합니다. 게임 로직이 선 선택 및 게임 행동의 제한 시간을 판단하며, 시간 초과 결과는 다음 확인 작업에서 전달됩니다. 게임 중 자발적 퇴장이나 연결 종료는 게임 로직에 따라 기권으로 처리됩니다. 남은 플레이어에게는 결과와 최신 방 상태가 전달되지만, 연결이 끊긴 플레이어는 그 이후 전송되는 이벤트를 받을 수 없습니다.

잔액은 연결별 임시 테스트 잔액으로, 기본값은 `1,000,000`입니다. `-Dgsg.initialBalance=...` 옵션으로 시작 금액을 바꿀 수 있습니다. 잔액은 재접속하거나 서버를 다시 시작하면 복구되지 않습니다. 채팅, 재접속, 중단된 게임 이어하기는 구현되어 있지 않습니다.

## 오류 처리 범위

잘못된 JSON, 누락되거나 자료형이 맞지 않는 필드, 중복 요청 ID, 지원하지 않는 요청, 잘못된 게임 단계, 오래된 버전, 허용되지 않는 게임 행동은 `ERROR` 응답으로 처리합니다. 소켓 읽기·쓰기 오류가 나면 연결을 닫거나 정리합니다. 수신 스레드의 `finally` 경로에서 방 참가자를 정리하고 닉네임을 해제합니다. 제한 시간 확인 작업에서 런타임 오류가 발생하면 로그를 남겨 서버 프로세스가 종료되지 않도록 합니다.

예상하지 못한 모든 프로그래밍 오류가 복구 가능한 `ERROR`로 바뀌는 것은 아닙니다. 클라이언트 처리 중 잡히지 않은 런타임 오류가 나면 해당 연결의 처리 스레드가 종료될 수 있지만, `finally`에서 방 정리를 시도합니다. 메시지 크기 초과는 오류 응답 대신 연결 종료로 처리합니다. 클라이언트는 연결 종료 상태를 표시하고 사용자가 다시 접속하도록 안내해야 합니다. 자동 재접속 및 진행 중 게임 복구는 지원하지 않습니다.

## 코드 위치와 검증 참고

- `ServerMain`: TCP 연결을 받고 제한 시간 확인 작업을 시작합니다.
- `GameUser`: 줄 단위 JSON을 읽고 요청 형식을 검사하며, ACK·ERROR·이벤트를 보내고 연결 종료를 정리합니다.
- `RoomManager`: 요청을 순서대로 처리하고 ID·차례·행동을 검사하며 방과 게임 이벤트를 전달합니다.
- `GameRoom`: 방 참가자와 자리 배정을 관리합니다.

세부 통신 규칙과 게임 진행은 `src/server/NETWORK_SERVER.md`에 정리되어 있습니다. 메시지 형식이나 지원 행동이 바뀌면 이 문서와 세부 문서도 함께 수정해야 합니다. 서버 컴파일 확인과 Swing 클라이언트까지 포함한 종단 간 동작 확인은 별도로 수행해야 합니다.
