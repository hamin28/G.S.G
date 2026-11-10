# 카드 ID와 이미지 리소스 대응표

## 확인 범위

- 원본: [G.S.G hwatu_image 폴더](https://github.com/hamin28/G.S.G/tree/main/hwatu_image)
- 확인일: 2026-11-10
- GitHub API의 파일 목록 및 PNG 50개를 직접 확인하였다.
- 총 **51개**: 월별 기본 패 **48개**(12개월 × 4개), 보너스 패 **2개**, 뒷면 카드 **1개**
- 이 폴더 목록에는 카드 뒷면 이미지가 없다. 필요하면 별도 리소스로 추가한다.
- 이 문서와 `data/cards.json`의 ID 및 분류는 이번에 제안한 데이터 정의다. 기존 Java 코드가 사용하는 ID와 일치하는지는 별도 대조가 필요하다.

## ID 배정 규칙

1월부터 12월까지 순서대로 각 월의 `_1`~`_4`에 ID 1~48을 배정한다. 보너스 두 장은 49, 50이다. GitHub 화면의 알파벳 정렬 순서로 ID를 배정하지 않는다. 파일명 뒤 숫자는 그 월의 이미지 구분 번호이며, 점수나 패 종류를 뜻하지 않는다.

서버와 클라이언트가 이 대응표를 함께 사용한다. 파일명을 변경해도 이미 사용한 ID를 다른 패에 재배정하지 않는다. 일반 패는 같은 월의 패와 매칭하고, 보너스는 `month: null`로 두어 월 매칭에서 제외한다. 보너스 획득 및 추가 드로우 규칙은 게임 로직에서 별도로 정한다.

## 월별 대응 요약

| 월 | 카드 ID | 원본 파일명 |
|---|---|---|
| 1월 | 1~4 | `january_1.png` ~ `january_4.png` |
| 2월 | 5~8 | `february_1.png` ~ `february_4.png` |
| 3월 | 9~12 | `march_1.png` ~ `march_4.png` |
| 4월 | 13~16 | `april_1.png` ~ `april_4.png` |
| 5월 | 17~20 | `may_1.png` ~ `may_4.png` |
| 6월 | 21~24 | `june_1.png` ~ `june_4.png` |
| 7월 | 25~28 | `july_1.png` ~ `july_4.png` |
| 8월 | 29~32 | `august_1.png` ~ `august_4.png` |
| 9월 | 33~36 | `september_1.png` ~ `september_4.png` |
| 10월 | 37~40 | `october_1.png` ~ `october_4.png` |
| 11월 | 41~44 | `november_1.png` ~ `november_4.png` |
| 12월 | 45~48 | `december_1.png` ~ `december_4.png` |
| 보너스 | 49 | `bonus_1.png` (그림에 숫자 2 표시) |
| 보너스 | 50 | `bonus_2.png` (그림에 숫자 3 표시) |

## 데이터 필드

| 필드 | 자료형 | 의미 |
|---|---|---|
| `cardId` | 정수 | 통신과 게임 상태에서 사용하는 고유 ID |
| `month` | 정수 또는 null | 일반 패는 1~12, 보너스는 null |
| `variant` | 정수 | 원본 파일명의 `_1` 같은 구분 번호 |
| `type` | 문자열 | GWANG, ANIMAL, RIBBON, PI, BONUS |
| `imagePath` | 문자열 | 저장소 루트를 기준으로 한 상대 이미지 경로 |
| `printedValue` | 정수 | 보너스 이미지에 인쇄된 숫자. 보너스에만 존재 |

`type`은 이미지를 기준으로 정리한 기본 분류다. 광 5장, 열끗 9장, 띠 10장, 피 24장, 보너스 2장이다. `PI`는 피 종류를 뜻하며 피의 장수 가치가 반드시 1이라는 뜻은 아니다.

**점수 규칙은 이 파일에서 확정하지 않았다.** 쌍피의 피 가치, 9월 술잔 패의 열끗·쌍피 선택, 보너스의 피 가치와 처리 방식은 팀에서 정한 규칙에 맞춰 별도로 정의해야 한다. `printedValue`는 그림의 숫자이며 점수 계산을 자동으로 결정하는 필드가 아니다.

```json
{"cardId":1,"month":1,"variant":1,"type":"GWANG","imagePath":"hwatu_image/january_1.png"}
```

```json
{"cardId":49,"month":null,"variant":1,"type":"BONUS","printedValue":2,"imagePath":"hwatu_image/bonus_1.png"}
```

## 프로토콜 연결

```json
{"type":"PLAY_CARD","requestId":"req-1","payload":{"cardId":12}}
```

위 요청의 12번은 `march_4.png`, 즉 3월의 네 번째 이미지다. 파일명이나 이미지를 소켓으로 보낼 필요 없이 서버와 클라이언트가 `cardId`로 같은 데이터를 조회한다. 서버는 조회한 패가 요청자의 손패에 있는지 확인한다.

`imagePath`는 저장소 상대 경로다. Java에서 `getResource()`로 읽으려면 빌드 시 이미지 폴더를 클래스패스 리소스로 포함해야 한다. 예를 들어 실제 리소스 경로가 유지될 때 `/hwatu_image/january_1.png`로 읽을 수 있다. 파일 시스템 경로와 클래스패스 경로는 구분한다.

카드 뒷면은 상대 손패를 표시하는 UI 리소스이므로 플레이 가능한 카드 ID를 배정하지 않는다. 서버는 상대 손패 ID 대신 장수만 전달한다.

## 전체 대응표

| 카드 ID | 월 | 종류 | 이미지 경로 |
|---|---|---|---|
| 1 | 1월 | 광 | `hwatu_image/january_1.png` |
| 2 | 1월 | 띠 | `hwatu_image/january_2.png` |
| 3 | 1월 | 피 | `hwatu_image/january_3.png` |
| 4 | 1월 | 피 | `hwatu_image/january_4.png` |
| 5 | 2월 | 열끗 | `hwatu_image/february_1.png` |
| 6 | 2월 | 띠 | `hwatu_image/february_2.png` |
| 7 | 2월 | 피 | `hwatu_image/february_3.png` |
| 8 | 2월 | 피 | `hwatu_image/february_4.png` |
| 9 | 3월 | 광 | `hwatu_image/march_1.png` |
| 10 | 3월 | 띠 | `hwatu_image/march_2.png` |
| 11 | 3월 | 피 | `hwatu_image/march_3.png` |
| 12 | 3월 | 피 | `hwatu_image/march_4.png` |
| 13 | 4월 | 열끗 | `hwatu_image/april_1.png` |
| 14 | 4월 | 띠 | `hwatu_image/april_2.png` |
| 15 | 4월 | 피 | `hwatu_image/april_3.png` |
| 16 | 4월 | 피 | `hwatu_image/april_4.png` |
| 17 | 5월 | 열끗 | `hwatu_image/may_1.png` |
| 18 | 5월 | 띠 | `hwatu_image/may_2.png` |
| 19 | 5월 | 피 | `hwatu_image/may_3.png` |
| 20 | 5월 | 피 | `hwatu_image/may_4.png` |
| 21 | 6월 | 열끗 | `hwatu_image/june_1.png` |
| 22 | 6월 | 띠 | `hwatu_image/june_2.png` |
| 23 | 6월 | 피 | `hwatu_image/june_3.png` |
| 24 | 6월 | 피 | `hwatu_image/june_4.png` |
| 25 | 7월 | 열끗 | `hwatu_image/july_1.png` |
| 26 | 7월 | 띠 | `hwatu_image/july_2.png` |
| 27 | 7월 | 피 | `hwatu_image/july_3.png` |
| 28 | 7월 | 피 | `hwatu_image/july_4.png` |
| 29 | 8월 | 광 | `hwatu_image/august_1.png` |
| 30 | 8월 | 열끗 | `hwatu_image/august_2.png` |
| 31 | 8월 | 피 | `hwatu_image/august_3.png` |
| 32 | 8월 | 피 | `hwatu_image/august_4.png` |
| 33 | 9월 | 열끗 | `hwatu_image/september_1.png` |
| 34 | 9월 | 띠 | `hwatu_image/september_2.png` |
| 35 | 9월 | 피 | `hwatu_image/september_3.png` |
| 36 | 9월 | 피 | `hwatu_image/september_4.png` |
| 37 | 10월 | 열끗 | `hwatu_image/october_1.png` |
| 38 | 10월 | 띠 | `hwatu_image/october_2.png` |
| 39 | 10월 | 피 | `hwatu_image/october_3.png` |
| 40 | 10월 | 피 | `hwatu_image/october_4.png` |
| 41 | 11월 | 광 | `hwatu_image/november_1.png` |
| 42 | 11월 | 피 | `hwatu_image/november_2.png` |
| 43 | 11월 | 피 | `hwatu_image/november_3.png` |
| 44 | 11월 | 피 | `hwatu_image/november_4.png` |
| 45 | 12월 | 광 | `hwatu_image/december_1.png` |
| 46 | 12월 | 열끗 | `hwatu_image/december_2.png` |
| 47 | 12월 | 띠 | `hwatu_image/december_3.png` |
| 48 | 12월 | 피 | `hwatu_image/december_4.png` |
| 49 | 없음 | 보너스 | `hwatu_image/bonus_1.png` |
| 50 | 없음 | 보너스 | `hwatu_image/bonus_2.png` |
