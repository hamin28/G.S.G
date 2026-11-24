# 2인 게임방 네트워크 서버

Java 21, Maven, Gson을 사용합니다. 방과 참가자는 Vector로 관리하고 게임 규칙은 각 GameRoom의 RoomRules 하나에 위임합니다. 예전 TurnEngine과 RoundInitializer를 서버에서 직접 호출하지 않습니다.

## 실행

1. IntelliJ에서 Maven을 새로고침합니다.
2. server.ServerMain.main()을 실행합니다. 기본 포트는 9999이며 첫 번째 실행 인자로 변경할 수 있습니다.
3. 클라이언트는 UTF-8 JSON 객체 하나와 LF 줄바꿈을 전송합니다. 최대 크기는 줄바꿈 제외 64 KiB입니다.

현재 계정 저장소는 연결하지 않았습니다. 초기 테스트 잔액은 접속당 1,000,000원입니다. IntelliJ VM options의 `-Dgsg.initialBalance=2000000`으로 변경할 수 있습니다. 클라이언트가 보낸 잔액은 사용하지 않습니다. 같은 접속에서 퇴장 후 다른 방에 들어가면 정산된 잔액이 유지됩니다. 재접속/서버 재시작 시 잔액 복구는 지원하지 않습니다.

## 공통 형식

```json
{"type":"JOIN","requestId":"req-1","payload":{"nickname":"선하"}}
```

요청마다 연결 내 고유 requestId를 사용합니다. 성공은 같은 requestId의 ACK, 거절은 ERROR와 payload.code/message로 응답합니다. 상태와 선택 안내는 별도 이벤트이며 requestId가 없습니다. 응답 후 해당 요청이 만든 이벤트를 전송합니다. 오류 후에는 참가 중인 방/게임의 최신 상태를 추가로 보낼 수 있습니다.

## 접속·방 요청

| type | payload |
| --- | --- |
| JOIN | `{"nickname":"선하"}` |
| LIST_ROOMS | `{}` |
| CREATE_ROOM | `{"title":"연습방"}` |
| JOIN_ROOM | `{"roomId":"응답의 방 ID"}` |
| READY | `{"ready":true}` 또는 false |
| LEAVE_ROOM | `{}` |

JOIN ACK에는 playerId와 balance가 있습니다. CREATE_ROOM/JOIN_ROOM ACK에는 roomId가 있습니다. 방 정원은 2명입니다. 닉네임은 공백 제거 후 1~12 코드 포인트, 방 제목은 1~30 코드 포인트이며 제어 문자를 허용하지 않습니다.

ROOM_LIST는 방 목록, ROOM_STATE는 방 상태와 players를 전달합니다. players는 playerId, nickname, seat, ready, balance를 포함합니다.

**새 로직의 방 status:** WAITING(상대 대기), READY(준비 대기), DEALER_SELECTION(선 선택), PLAYING, CLOSED(파산으로 폐쇄). 예전 WAITING/PLAYING 두 종류와 달라 클라이언트도 갱신해야 합니다. 새 상대가 입장하면 선 결정과 나가리 누적을 새로 시작합니다.

## 선 선택

두 명이 준비하면 새 상대와의 첫 판은 DEALER_SELECTION 이벤트를 각자 받습니다.

- roomId, selectionId: 현재 선 선택 절차 식별자
- drawNumber: 재추첨 번호
- choices: 선택 가능한 Card 객체 배열
- remainingMillis: 서버 기준 남은 시간

```json
{"type":"SELECT_DEALER","requestId":"req-5","payload":{"selectionId":"받은 값","drawNumber":1,"cardId":17}}
```

ACK의 choiceResult는 WAITING, RESELECT, REDRAW, DECIDED입니다. 이후 새 DEALER_SELECTION의 choices를 기준으로 표시합니다. 선택을 마친 사람은 choices가 비어 있을 수 있습니다. 충돌 시 먼저 선택한 사람의 패는 유지되며, 뒤에 선택한 사람이 재선택합니다. 준비 취소 후 다시 시작하면 selectionId가 달라집니다.

선이 정해지면 RoomRules가 분배·총통 판정·MatchEngine 생성을 수행합니다. 이후 같은 상대와 다음 판은 로직이 정한 선을 유지하므로 다시 선을 고르지 않을 수 있습니다.

## 개인별 게임 상태

GAME_START와 GAME_STATE는 MatchEngine.View에서 만듭니다. 내부 엔진이나 상대 손패·더미 순서를 직렬화하지 않습니다.

주요 필드: gameId, roomId, roundId, roundNumber(roundId의 호환 별칭), version, mySeat, firstPlayerId, secondPlayerId, currentPlayerId, phase, ownHand, opponentHandCount, stockCount, floor, ownCaptures, opponentCaptures, ownScore, opponentScore, choices, ownTokens, ownGo, events, remainingMillis.

phase는 PLAY, HAND_CHOICE, STOCK_CHOICE, GUKJIN, GO_STOP, FINISHED입니다. 끝난 상태의 currentPlayerId는 null입니다. ownHand/choices의 항목은 정수 ID가 아닌 Card 객체이며 항목의 id를 요청에 사용합니다. 선·게임 제한 시간은 서버의 단조 증가 시계로 판정합니다. deadlineMillis 자체를 클라이언트의 현재 시각과 비교하지 말고 remainingMillis로 화면 카운트다운을 표시합니다.

정상 시작: ACK → ROOM_STATE → GAME_START. 시작 직후 총통/무효면 GAME_START 없이 종료 상태/결과가 전달될 수 있습니다. 클라이언트는 GAME_START 수신 즉시 게임 화면을 초기화하고 ROOM_STATE로 로비/방 표시를 갱신합니다.

## 게임 행동

최신 GAME_START/GAME_STATE의 gameId, roundNumber(또는 roundId), version을 그대로 요청에 붙입니다. 사용자 자리와 현재 시각은 서버가 결정합니다.

```json
{"type":"GAME_ACTION","requestId":"req-6","payload":{"gameId":"받은 값","roundNumber":1,"version":0,"action":"PLAY","cardId":17}}
```

| action | 의미 | cardId |
| --- | --- | --- |
| PLAY | 일반 패 내기 | 손패 ID |
| SERVICE | 서비스패 사용 | 서비스패 ID |
| SHAKE | 흔들기와 제출 | 해당 월 손패 ID |
| BOMB | 폭탄 | 해당 월 손패 ID |
| DECK_ONLY | 폭탄 뒤집기 기회 사용 | 생략 가능 |
| SELECT | 바닥 매칭 선택 | choices의 ID |
| GUKJIN_PI / GUKJIN_ANIMAL | 국진 분류 | 생략 가능 |
| GO / STOP | 고·스톱 | 생략 가능 |

기존 PLAY_CARD는 PLAY, SELECT_CAPTURE/CHOOSE_MATCH는 SELECT로 연결됩니다. DECIDE의 payload.decision은 GO/STOP으로 처리합니다. 행동명을 type에 직접 써도 되지만 신규 클라이언트에는 GAME_ACTION을 권장합니다.

정상 행동은 ACK → 개인별 GAME_STATE로 전달됩니다. 오래된 판/버전은 STALE_STATE, 다른 차례는 NOT_YOUR_TURN, 잘못된 후보는 INVALID_SELECTION, 로직의 거절은 INVALID_ACTION입니다. 실패한 행동은 로직에서 상태를 롤백합니다. 독립적인 시간 초과는 요청의 유효성과 관계없이 판을 종료할 수 있습니다.

## 종료·정산·시간 초과

게임 로직의 Result를 한 번만 반영하고 GAME_OVER를 방 전체에 전송합니다.

- gameId, roomId, roundNumber
- reason: NORMAL, TONGTONG, THREE_PPEOK, DRAW, VOID, FORFEIT
- winnerId: 무승부/무효면 null
- settlement: 로직이 계산한 점수, 배율, 계산 금액, 실제 지급액. 무승부/무효면 null
- results: playerId, balance, bankrupt
- nextDealerId, nextDrawStreak

일반 종료 후 다시 두 사람의 READY가 필요합니다. 기권자는 방에서 제거하고 승자는 새 상대를 기다립니다. 파산으로 CLOSED가 되면 양쪽을 방에서 제거합니다. 강제/일반 방 이탈은 ROOM_LEFT로 roomId, balance, reason을 받습니다.

ServerMain이 시작하는 예약 작업에서 100ms 간격으로 expire()를 호출합니다. 제한 시각은 30초이며 실제 전송은 예약 작업 실행 시점에 따릅니다. 선 선택 중 시간 초과는 무료 퇴장, 분배 이후 시간 초과·퇴장·연결 종료는 로직의 기권 정산을 사용합니다. 소켓 단절로 떠난 사람은 결과를 못 받을 수 있지만 남은 사람에게 결과와 최신 방 상태를 전달합니다.

## 구조와 검증

- ServerMain: TCP 수락, 제한 시간 검사 시작/종료
- GameUser: 사용자 소켓, JSON 요청 해석, 응답/이벤트 송신
- GameRoom: Vector 참가자, 고정 자리 매핑, RoomRules
- RoomManager: 요청 직렬 처리, 규칙 호출, 결과와 개인별 상태 전송

소규모 과제용으로 RoomManager의 공통 잠금 안에서 요청과 전송 순서를 보장합니다. 채팅은 아직 구현하지 않았습니다.

`src/test/java/server/NetworkRulesCheck.java`의 main은 실제 시간 대기 없이 가상 서버 시각으로 150판, 정상/총통/나가리 종료, 다음 판, 손패 분리, 기권/새 상대 잔액, 선택 취소와 시간 초과를 검사합니다. JUnit 테스트가 아니므로 Maven test만으로 자동 실행되지 않습니다. 테스트 소스에서 main을 직접 실행하세요.

별도로 실제 로컬 TCP 접속에서 방 생성/입장, 선 선택, 개인별 시작, 행동/상태, 접속 종료 결과를 확인했습니다. Swing 화면 연동 검증은 별도입니다.
