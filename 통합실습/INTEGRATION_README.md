# GitHub 원본 서버와 UI 통합 검사

2026-11-24 · 갈래말래 · 기준 커밋 `a772a84` (검사 시작 시 main)

## 무엇을 했는가

GitHub의 서버·UI·게임 로직을 새 폴더에 받아 하나의 프로젝트로 컴파일했다. **사용자의 요청에 따라 서버·UI·게임 로직 원본은 수정하지 않았다.** 기존 실습용 `G.S.G-integration`이나 Desktop의 `G.S.G`, `Game Logic`도 수정하지 않았다.

추가한 것은 루트 `pom.xml`, 실행 스크립트, `integration-tests`, 검사 결과와 Gson 라이브러리이다. `hwatu_image`는 원본 그대로 클래스패스의 `resource/hwatu_image`로 복사해 원본 CardCatalog의 경로에 맞췄다. 새 UI나 별도 서버는 만들지 않았다.

## IntelliJ에서 직접 실행

1. **이 폴더 루트의 `pom.xml`**을 프로젝트로 연다. `src/GameLogic/pom.xml`은 로직만 여는 파일이므로 이번에는 루트 파일을 선택한다.
2. 프로젝트 SDK는 **JDK 21 이상**으로 설정하고 Maven을 불러온다. 검사는 JDK 21.0.8에서 실행했다.
3. `src/server/ServerMain.java`의 main을 실행한다. 기본 포트는 **9999**이다.
4. `src/UI/ClientMain.java`의 main을 두 번 실행한다. IntelliJ 실행 설정에서 **여러 인스턴스 허용**을 켠다.
5. 실행 인자를 각각 `localhost 9999 A`, `localhost 9999 B`로 지정하면 닉네임 입력을 생략할 수 있다.
6. A에서 **방 만들기**, B에서 **새로고침 → 입장**, 양쪽에서 **준비**를 누른다. 원본의 선 선택 창에서 패를 고르면 게임이 시작된다.
7. 손패를 클릭해 진행한다. 바닥 매칭·국진·고스톱 선택 창은 원본 UI에 있는 기능을 사용한다.

실행 작업 디렉터리는 이 루트 폴더이다. 다른 서버가 9999를 사용 중이면 ServerMain의 인자를 `10099`로 바꾸고 양쪽 ClientMain도 `localhost 10099 A/B`로 맞춘다.

게임 선택 제한은 **30초**이다. 잘못된 카드 요청은 시간을 연장하지 않는다. 다음의 원본 UI 미연결 부분 때문에 모든 행동을 화면에서 실행할 수 있는 상태는 아니다.

## CMD와 PowerShell로 실행

Maven 없이 실행할 수 있도록 `lib/gson-2.13.1.jar`를 포함했다. 먼저 CMD에서 이 폴더로 이동한 뒤 아래 명령을 실행한다. `JDK설치폴더`는 `bin` 바로 위의 폴더이다.

```cmd
powershell -ExecutionPolicy Bypass -File .\build.ps1 -JdkHome "JDK설치폴더"
```

이후 CMD 창 세 개에서 각각 실행한다.

```cmd
powershell -ExecutionPolicy Bypass -File .\run.ps1 -Mode server -JdkHome "JDK설치폴더"
```

```cmd
powershell -ExecutionPolicy Bypass -File .\run.ps1 -Mode client -Nickname A -JdkHome "JDK설치폴더"
```

```cmd
powershell -ExecutionPolicy Bypass -File .\run.ps1 -Mode client -Nickname B -JdkHome "JDK설치폴더"
```

`JAVA_HOME`이 올바르게 설정되어 있으면 `-JdkHome` 옵션을 생략할 수 있다.

## 확인한 결과

| 검사 | 실제 결과 |
| --- | --- |
| 세 파트 컴파일 | Java 21로 통과 |
| 원본 로직 회귀 검사 | GameRulesCheck·FullGameCheck 통과, 동일 seed 2,000판 완주 |
| 원본 이미지 | 카드 50장과 뒷면 로딩·ID 대응 통과 |
| 실제 TCP로 게임 진행 | 80판 종료: 정상 79판, 총통 1판 |
| 실제 TCP 행동 | PLAY, SERVICE, SELECT, SHAKE, BOMB, DECK_ONLY, GO, STOP, GUKJIN_PI 전송·처리 확인 |
| 개인별 정보와 결과 | 상대 손패 목록 비공개, 양쪽 공개 상태·정산 일치, 잔액 합계 보존 |
| 다음 판·잘못된 차례 | 같은 연결에서 다음 판, NOT_YOUR_TURN 거절 확인 |
| 서버의 예약 작업 | 실제 100ms 예약 작업에 가상 시각을 30초 전진시켜 분배 전 무료 퇴장·분배 후 10만원 기권 확인 |
| 원본 MainFrame 두 개 | 실제 방 생성·입장·준비·선결정·카드 입력·선택 확인 |
| 화면에서 정상 승부 | 카드 제출 15회, 선택 5회, 정상 종료·정산 1판 |
| 실제 실행 진입점 추가 확인 | ServerMain.main과 원본 MainFrame 두 개를 실제 서버 시계로 실행. 카드 제출 17회·선택 5회·정상 정산 1판 |
| IntelliJ와 같은 Maven 빌드 | 루트 pom.xml로 compile·testCompile 성공. 검사 main은 별도 실행 |
| 원본 소스 보존 | verify-originals.ps1로 검사 가능 |

TCP 80판은 검사 클라이언트가 프로토콜을 통해 행동을 보낸 결과이다. 그 80판 모두를 UI로 조작한 것은 아니다. 원본 UI 검사는 별도로 실제 MainFrame의 버튼과 CardView에 입력을 주었고 정상 정산 한 판을 확인했다. 테스트 선택은 자동화했으며 사람이 직접 플레이한 결과로 기록하지 않는다.

재현 가능한 첫 UI 검사에서는 서버 테스트 시각을 고정했다. 추가로 원본 ServerMain.main의 실제 시계와 예약 타이머를 사용해 같은 화면의 정상 정산 한 판도 확인했다. 제한 시간 경계 검사는 별도로 가상 시각을 전진시켜 수행했다. 실제 30초를 기다린 벽시계 측정이나 원격 두 PC 검사는 하지 않았다.

## 원본에서 확인한 미연결 부분

1. **손패 서비스패:** GamePanel.onCardClick()은 카드 종류와 무관하게 `PLAY`를 보낸다. 서버는 서비스패에 `SERVICE`를 요구하므로 화면에서 서비스패를 누르면 거절한다. 실제 화면으로 재현했다.
2. **점수 표시:** 서버의 `ownScore`·`opponentScore`는 `total`을 포함한 객체인데, UI는 해당 필드를 숫자로 읽어 기본값 0을 표시한다. 실제 점수가 생겨도 화면이 0점을 표시하는 것을 확인했다. 승패·정산 자체는 서버 점수로 처리되어 정상 결과를 받았다.
3. **흔들기·폭탄·폭탄 기회:** 서버는 처리하지만 원본 GamePanel에 전송 조작이 TODO로 남아 있다. 이번에는 추가하지 않았다. 서버까지의 행동 처리는 TCP 검사에서 확인했다.
4. **선 선택 표시:** 원본 UI는 선 선택 패를 뒷면으로 표시한다. 확정 규칙의 공개 선택과 차이가 있지만 원본을 유지했다.
5. **그 밖의 원본 기능:** 남은 시간 카운트다운은 TODO이며 채팅은 로컬 표시 상태이다. 닉네임 변경은 JOIN을 다시 보내지만 현재 서버는 중복 JOIN을 거절한다. 이 항목들은 이번에 보완하지 않았다.

따라서 **현재 원본으로 일반 진행과 정상 정산까지 연결되지만, UI에서 모든 특수 규칙을 조작할 수 있는 완성 상태는 아니다.** 위 차이를 수정하려면 이후에 별도로 범위를 정해야 한다.

## 검사 재실행

먼저 build.ps1을 실행한다. 검사는 JUnit이 아닌 main 실행 방식이다.

```cmd
powershell -ExecutionPolicy Bypass -File .\run.ps1 -Mode check -JdkHome "JDK설치폴더"
```

위 명령은 기존 로직 검사와 실제 TCP 검사를 실행한다. TCP 검사는 임시 포트를 사용하므로 9999의 서버와 충돌하지 않는다.

원본 화면 자동 검사는 IntelliJ에서 `integration-tests/gsg/client/ui/OriginalUiCheck.java`의 main을 실행한다. 검사 중 원본 창 두 개가 열리고 입력이 자동 수행되며 끝나면 닫힌다. **검사 중에는 해당 창을 직접 조작하지 않는다.** 이 검사는 서비스패 거절과 점수 표시 차이를 원본의 알려진 한계로 기록한다.

원본 서버의 실제 실행 진입점까지 함께 검사하려면 `integration-tests/server/EntryPointCheck.java`의 main을 실행한다. 이 경우에도 검사 창은 자동 입력 후 종료된다.

## 결과 파일과 원본 확인

- `integration-results/TCP_RESULTS.txt`: 실제 TCP 검사 전체 출력
- `integration-results/UI_RESULTS.txt`: 원본 UI 검사 전체 출력
- `integration-results/LOGIC_RESULTS.txt`: 원본 로직 검사 출력
- `integration-results/ENTRYPOINT_RESULTS.txt`: 실제 ServerMain 진입점과 원본 UI 검사 출력
- `integration-results/MAVEN_BUILD.txt`: Maven 빌드 출력 (검사 main 자동 실행을 의미하지 않음)
- `integration-results/SUMMARY.txt`: PASS·KNOWN LIMIT 요약
- `integration-results/screens/`: 검사 중 원본 화면을 저장한 이미지
- `original-source-manifest.json`: 기준 커밋과 원본 파일 SHA-256

다음 명령으로 원본 파일이 바뀌지 않았는지 확인할 수 있다.

```cmd
powershell -ExecutionPolicy Bypass -File .\verify-originals.ps1
```

이 작업은 로컬 검사 브랜치 `integration/github-ui-server`에서 수행했다. GitHub에 커밋·푸시는 하지 않았다. 기존 보고서는 수정하지 않았다.

Desktop의 `G.S.G-통합검사`와 ZIP은 실행·검사용 복사본이며 `.git`과 컴파일 결과를 제외했다. 이후 GitHub에서 pull할 때는 기존 `G.S.G` 클론을 사용하고, 이 복사본은 검증한 커밋의 실행 자료로 구분한다.
