# G.S.G 통신 프로토콜

> 상태: 구현 제안 초안 v0.1. 저장소 코드와 대조하기 전의 설계 문서이며, 현재 구현된 기능을 보장하지 않습니다.
> 대상 저장소: https://github.com/hamin28/G.S.G

Java TCP 소켓을 사용하는 고스톱 게임의 클라이언트와 서버 사이 통신 규칙을 정의합니다. Swing 클라이언트는 사용자의 행동을 요청하고, 서버는 요청을 검증하여 게임 상태를 변경한 뒤 결과를 전달합니다. 채팅과 게임 메시지는 같은 연결을 사용합니다.

## 1. 기본 통신 규칙

| 항목 | 규칙 | 
|---|---|
| 전송 방식 | TCP, 클라이언트당 연결 하나 |
| 인코딩 | UTF-8 |
| 메시지 형식 | JSON 객체 하나를 한 줄로 전송 |
| 메시지 경계 | LF(`\n`). 수신 시 CRLF도 허용 |
| 포트 | 서버 설정에서 지정하며 클라이언트가 같은 값을 사용 |
| 최대 메시지 크기 | 줄바꿈을 제외한 UTF-8 기준 64 KiB |
| 프로토콜 버전 | `1` |

TCP는 한 번 보낸 데이터가 한 번의 수신으로 도착하는 것을 보장하지 않습니다. 수신자는 줄바꿈까지 데이터를 모은 후 JSON을 해석해야 합니다. 실제 전송 시 JSON을 여러 줄로 들여쓰기하지 않습니다. 채팅 문자열 내부 줄바꿈은 JSON의 `\n`으로 이스케이프합니다.

길이 제한은 줄을 읽는 도중에도 적용합니다. 제한을 초과하면 연결을 종료합니다.

## 2. 공통 메시지 구조

```json
{"version":1,"type":"PLAY_CARD","requestId":"req-15","payload":{"cardId":12}}
```

| 필드 | 자료형 | 설명 |
|---|---|---|
| `version` | 정수 | 프로토콜 버전. 현재 `1` |
| `type` | 문자열 | 대문자와 밑줄로 된 메시지 종류 |
| `requestId` | 문자열 | 클라이언트 요청 식별자. 요청마다 연결 내 고유한 값 사용 |
| `payload` | 객체 | 메시지별 데이터. 데이터가 없어도 `{}` 사용 |

클라이언트 요청과 서버의 직접 응답에는 같은 `requestId`를 사용합니다. 서버가 자발적으로 보내는 이벤트에는 `requestId`를 생략합니다. 요청은 정확히 하나의 `ACK` 또는 `ERROR`로 응답하고, 게임 상태나 채팅은 별도 이벤트로 전달합니다.

성공 응답 예시:

```json
{"version":1,"type":"ACK","requestId":"req-15","payload":{"requestType":"PLAY_CARD"}}
```

오류 응답 예시:

```json
{"version":1,"type":"ERROR","requestId":"req-15","payload":{"code":"NOT_YOUR_TURN","message":"자신의 차례가 아닙니다."}}
```

JSON 자체가 잘못되어 요청 식별자를 읽을 수 없으면 `ERROR`의 `requestId`를 생략합니다. 오류 시 게임 상태를 변경하지 않습니다. 문서에 없는 추가 필드는 무시하되 필수 필드 누락, 자료형 오류, 허용되지 않은 값은 거절합니다. 식별자는 불투명한 값으로 취급하며 화면상의 위치나 접속 순서로 추정하지 않습니다.

## 3. 접속 및 방 관리

플레이어 ID와 방 ID는 서버에서 생성합니다. 서버는 소켓 연결과 플레이어 ID를 연결해 관리하며, 클라이언트가 임의로 보낸 발신자 ID를 신뢰하지 않습니다. 한 플레이어는 동시에 한 방에만 참가할 수 있습니다.

| 요청 종류 | `payload` 예시 | 처리 |
|---|---|---|
| `JOIN` | `{"nickname":"철수"}` | 연결의 닉네임 등록. 연결 후 최초 요청 |
| `LIST_ROOMS` | `{}` | 참가 가능한 방 목록 요청 |
| `CREATE_ROOM` | `{"title":"함께 고스톱"}` | 방 생성 후 생성자가 입장 |
| `JOIN_ROOM` | `{"roomId":"room-1"}` | 기존 방 입장 |
| `READY` | `{"ready":true}` | 대기 중 준비 상태 변경 |
| `LEAVE_ROOM` | `{}` | 현재 방 퇴장 |

닉네임은 앞뒤 공백 제거 후 1~12 Unicode 코드 포인트, 방 제목은 1~30 코드 포인트로 제한합니다. 줄바꿈과 제어 문자는 허용하지 않습니다. 닉네임 중복 정책은 서버에서 통일하며, 이 초안에서는 접속 중 중복을 거절합니다.

`JOIN` 성공 시 `ACK.payload`에 `playerId`를 추가합니다. `CREATE_ROOM`, `JOIN_ROOM` 성공 시에는 `roomId`를 추가합니다.

```json
{"version":1,"type":"ACK","requestId":"req-1","payload":{"requestType":"JOIN","playerId":"player-1"}}
```

| 서버 이벤트 | 데이터 |
|---|---|
| `ROOM_LIST` | `rooms`: 방 정보 배열. 각 항목은 `roomId`, `title`, `playerCount`, `maxPlayers`, `status` 포함 |
| `ROOM_STATE` | 현재 방의 `roomId`, `title`, `maxPlayers`, `status`, `players` |

`status`는 `WAITING` 또는 `PLAYING`입니다. `players` 각 항목에는 `playerId`, `nickname`, `ready`가 들어갑니다. 방 목록은 요청자에게, 방 상태는 해당 방 전체에 전달합니다.

게임 인원수와 세부 규칙은 게임 로직에서 확정하고 `maxPlayers`에 반영합니다. 방 정원이 찬 상태에서 전원이 준비하면 서버가 자동으로 게임을 시작합니다. 진행 중인 방의 추가 입장은 거절합니다.

## 4. 채팅

채팅은 현재 참가한 방 안에서만 전달합니다. 클라이언트는 내용만 보내며 서버가 발신자 정보를 붙입니다.

클라이언트 → 서버:

```json
{"version":1,"type":"CHAT","requestId":"req-8","payload":{"message":"안녕하세요!"}}
```

서버 → 같은 방 전체(보낸 사람 포함):

```json
{"version":1,"type":"CHAT_MESSAGE","payload":{"playerId":"player-1","nickname":"철수","message":"안녕하세요!"}}
```

- 앞뒤 공백을 제거한 내용이 비어 있으면 거절합니다.
- 내용은 최대 200 Unicode 코드 포인트로 제한합니다.
- 클라이언트는 서버의 `CHAT_MESSAGE` 수신 시 채팅창에 추가합니다. 전송 직후에도 추가하면 중복 표시될 수 있습니다.
- 일반 텍스트로 표시하며 HTML 등으로 해석하지 않습니다.

## 5. 게임 진행 요청

패 ID는 전체 덱에서 고유한 정수이며 서버와 클라이언트가 같은 카드 데이터 정의를 사용합니다. 월과 종류를 ID 값에서 임의로 계산하지 않습니다. 카드 데이터 파일에 ID, 월, 종류, 이미지 경로의 대응을 별도로 정의합니다.

| 요청 종류 | `payload` 예시 | 검증 및 처리 |
|---|---|---|
| `PLAY_CARD` | `{"cardId":12}` | 현재 턴, 패 내기 단계, 손패 소유 여부 확인 후 처리 |
| `SELECT_CAPTURE` | `{"choiceId":"choice-1","cardId":17}` | 서버가 제시한 선택의 후보 중 먹을 바닥패 선택 |
| `DECIDE` | `{"choiceId":"choice-2","decision":"GO"}` | 고·스톱 선택. `GO` 또는 `STOP` |

서버는 패 섞기, 분배, 덱에서 뒤집기, 패 매칭, 획득패, 점수, 턴, 고·스톱 가능 여부와 승패를 관리합니다. 클라이언트가 보낸 점수나 승리 선언은 사용하지 않습니다.

선택이 필요하면 서버가 해당 플레이어에게 `CHOICE_REQUIRED`를 전송합니다. 선택 중에는 다른 패 내기 요청을 거절합니다. `choiceId`는 게임 내 고유하며 한 번 처리한 선택은 재사용할 수 없습니다.

```json
{"version":1,"type":"CHOICE_REQUIRED","payload":{"gameId":"game-1","choiceId":"choice-1","kind":"CAPTURE","cardIds":[17,18]}}
```

```json
{"version":1,"type":"CHOICE_REQUIRED","payload":{"gameId":"game-1","choiceId":"choice-2","kind":"GO_STOP","options":["GO","STOP"]}}
```

`CAPTURE`에는 `cardIds`, `GO_STOP`에는 `options`를 포함합니다. 클라이언트는 제시된 후보만 선택할 수 있습니다. 선택이 해결되면 최신 상태에서 `pendingChoice`를 `null`로 변경합니다.

## 6. 게임 상태 전달

이 초안은 구현을 단순하게 하기 위해 상태 변경마다 전체 상태를 전달합니다. 서버는 각 수신자에게 맞춘 상태를 생성합니다.

| 이벤트 종류 | 전송 시점 |
|---|---|
| `GAME_START` | 분배가 끝나고 게임 시작 시 |
| `GAME_STATE` | 유효한 게임 행동이나 선택 처리 후 |
| `GAME_OVER` | 스톱, 규칙에 따른 종료, 상대 퇴장 등으로 종료 시 |

`GAME_START`, `GAME_STATE`의 `payload` 구조는 같습니다. 아래 ID와 점수는 형식 설명용 예시입니다.

```json
{"version":1,"type":"GAME_STATE","payload":{"gameId":"game-1","revision":3,"phase":"WAIT_PLAY","currentPlayerId":"player-1","myHand":[12,20,31],"tableCards":[4,17,18],"deckCount":21,"players":[{"playerId":"player-1","handCount":3,"capturedCards":[2,6],"score":0,"goCount":0},{"playerId":"player-2","handCount":3,"capturedCards":[8],"score":0,"goCount":0}],"pendingChoice":null}}
```

| 필드 | 의미 |
|---|---|
| `gameId` | 한 판의 고유 ID |
| `revision` | 상태 변경마다 증가하는 게임 상태 번호 |
| `phase` | `WAIT_PLAY`, `WAIT_CAPTURE`, `WAIT_GO_STOP`, `FINISHED` |
| `currentPlayerId` | 현재 행동할 플레이어. 종료 시 `null` |
| `myHand` | 수신자 자신의 손패 ID 배열 |
| `tableCards` | 공개된 바닥패 ID 배열 |
| `deckCount` | 뒤집지 않은 덱의 남은 장수 |
| `players` | 플레이어별 손패 수, 공개 획득패, 점수, 고 횟수 |
| `pendingChoice` | 선택 대상자에게만 선택 정보 객체, 그 외에는 `null` |

`pendingChoice`의 객체는 `CHOICE_REQUIRED.payload`와 같은 구조입니다. 서버는 상태를 먼저 보내고 선택 이벤트를 보냅니다. 클라이언트는 동일한 `choiceId`에 대해 선택창을 중복 생성하지 않습니다.

**상대 손패의 실제 ID, 덱의 순서와 남은 패 ID는 전송하지 않습니다.** 다른 플레이어에게는 손패 장수만 전달합니다. 따라서 `myHand`가 다른 메시지를 모든 소켓에 그대로 방송하면 안 됩니다.

게임 종료 예시:

```json
{"version":1,"type":"GAME_OVER","payload":{"gameId":"game-1","reason":"STOP","winnerId":"player-1","results":[{"playerId":"player-1","score":7,"goCount":1},{"playerId":"player-2","score":2,"goCount":0}]}}
```

`reason`은 `STOP`, `DRAW`, `PLAYER_LEFT` 중 하나입니다. 무승부나 접속 종료로 경기가 취소되면 `winnerId`는 `null`로 합니다. 접속 종료를 몰수패로 처리하려면 별도의 팀 규칙 확정 후 이 명세도 수정합니다.

종료 직전 `phase: FINISHED` 상태를 전달하고, 이어서 `GAME_OVER`를 전달합니다. 이후 방을 `WAITING`으로 바꾸고 전원 준비 상태를 해제하여 `ROOM_STATE`를 보냅니다. 정산 배수, 특수 규칙과 패 분배 장수는 별도 게임 규칙 문서에서 정의합니다.

## 7. 오류 코드

| 코드 | 의미 |
|---|---|
| `INVALID_MESSAGE` | JSON, 필수 필드, 자료형 또는 값 오류 |
| `UNSUPPORTED_VERSION` | 지원하지 않는 버전 |
| `UNKNOWN_TYPE` | 정의되지 않은 메시지 종류 |
| `DUPLICATE_REQUEST` | 이미 처리한 요청 식별자 |
| `NOT_JOINED` | 닉네임 등록 전 요청 |
| `ALREADY_JOINED` | 닉네임 등록 중복 요청 |
| `NICKNAME_TAKEN` | 사용 중인 닉네임 |
| `NOT_IN_ROOM` | 방 참가 필요 |
| `ALREADY_IN_ROOM` | 이미 방 참가 중 |
| `ROOM_NOT_FOUND` | 없는 방 |
| `ROOM_FULL` | 방 정원 초과 |
| `GAME_IN_PROGRESS` | 게임 진행 중 허용되지 않는 요청 |
| `GAME_NOT_STARTED` | 진행 중인 게임 없음 |
| `NOT_YOUR_TURN` | 다른 플레이어의 차례 |
| `INVALID_PHASE` | 현재 단계에서 허용되지 않는 요청 |
| `CARD_NOT_OWNED` | 손패에 없는 패 |
| `INVALID_SELECTION` | 만료된 선택 또는 후보에 없는 선택 |

클라이언트는 오류 코드로 처리를 분기하고 `message`는 사용자 안내에 사용합니다. 오류 메시지에 서버 내부 예외나 스택 트레이스를 포함하지 않습니다.

## 8. 연결 종료와 동시 처리

- `readLine()`이 `null`을 반환하거나 입출력 예외가 발생하면 연결 종료로 처리합니다.
- 종료된 플레이어를 접속 목록과 방에서 제거하고, 남은 인원에게 `ROOM_STATE`를 전달합니다. 빈 방은 삭제합니다.
- 게임 중 종료 또는 `LEAVE_ROOM`이면 `PLAYER_LEFT`로 게임을 종료합니다.
- 자동 재접속과 중단된 판 복구는 이 버전의 범위에 포함하지 않습니다. 재접속은 새 플레이어로 처리합니다.
- 서버는 방별 행동을 순서대로 처리하여 동시에 들어온 요청이 상태를 중복 변경하지 않도록 합니다.
- 같은 소켓으로 보내는 메시지도 직렬화하여 JSON 두 개가 서로 섞이지 않게 합니다.
- 서버는 연결 동안 처리한 `requestId`를 기록하고 중복 요청을 거절합니다. 클라이언트는 응답 지연만으로 같은 행동을 자동 재전송하지 않습니다.

## 9. Swing 클라이언트 구현 주의점

소켓 수신은 별도 스레드에서 처리합니다. 수신 메시지로 컴포넌트를 변경할 때는 `SwingUtilities.invokeLater()`를 사용합니다. EDT에서 소켓 연결이나 블로킹 송수신을 수행하지 않습니다.

게임 버튼 활성화는 서버가 알려준 `phase`와 `currentPlayerId`를 기준으로 합니다. 서버 응답을 받기 전에 점수나 손패를 확정 변경하지 않습니다. 클라이언트에서 버튼을 비활성화하더라도 서버 검증은 항상 수행합니다.

## 10. 통신 흐름 예시

```text
클라이언트 → 서버: JOIN
서버 → 요청자: ACK (playerId 포함)
클라이언트 → 서버: CREATE_ROOM 또는 JOIN_ROOM
서버 → 요청자: ACK (roomId 포함)
서버 → 방 전체: ROOM_STATE
각 클라이언트 → 서버: READY
서버 → 각 요청자: ACK
서버 → 방 전체: ROOM_STATE
전원 준비 시 서버 → 각 플레이어: GAME_START (개인별 손패 포함)
현재 플레이어 → 서버: PLAY_CARD
서버 → 요청자: ACK
서버 → 각 플레이어: GAME_STATE
선택 필요 시 서버 → 선택 대상자: CHOICE_REQUIRED
선택 대상자 → 서버: SELECT_CAPTURE 또는 DECIDE
서버 → 요청자: ACK
서버 → 각 플레이어: GAME_STATE
종료 시 서버 → 방 전체: GAME_OVER, ROOM_STATE
```

채팅은 방 참가 후 대기 중과 게임 중 모두 사용할 수 있습니다. 게임 메시지와 채팅 메시지는 `type`으로 구분합니다.

## 11. 구현 전 확정할 항목

- 게임 인원, 분배 장수, 점수 계산, 고·스톱 조건, 특수 규칙
- 카드 ID와 이미지 리소스의 대응 데이터
- 실제 코드의 메시지 클래스와 이 문서의 이름 및 필드 일치 여부
- JSON 라이브러리 선정 및 서버 포트 설정 방법

필드 삭제, 필수 필드 추가 또는 의미 변경처럼 기존 구현과 호환되지 않는 변경은 프로토콜 버전을 올립니다. 구현 변경 시 이 문서와 서버·클라이언트의 공통 메시지 정의를 함께 수정합니다.
