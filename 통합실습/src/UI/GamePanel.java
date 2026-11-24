package gsg.client.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

// [8-5] 게임방 화면의 "조종 담당".
//       서버가 보낸 이벤트(ROOM_STATE, DEALER_SELECTION, GAME_START/STATE, GAME_OVER, ROOM_LEFT)를 받아서
//       FeltPanel에 무엇을 보여 줄지 정하고, 사용자의 동작(준비, 카드 내기, 고/스톱 ...)을 서버 요청으로 바꾼다.
public class GamePanel extends JPanel {

    // [8-10] 서버에 채팅(CHAT / CHAT_MESSAGE)이 구현되면 true로 바꿈.
    //        false이면 7단계처럼 내 화면에만 "나 : 내용"을 보여 줌.
    private static final boolean CHAT_SERVER_READY = false;

    private final GameClient client;
    private final Session session;
    private final Runnable onLeave;

    private final FeltPanel felt = new FeltPanel();
    private final JTextArea chat = new JTextArea();

    // ---- 방/게임 상태 ----
    private String roomId;              // 지금 있는 방 (없으면 null)
    private JsonObject lastState;       // 가장 최근 GAME_START / GAME_STATE (행동 요청에 gameId, version을 붙이려고 보관)
    private boolean inGame;             // 게임 중인지
    private boolean myReady;            // 내 준비 상태 (ROOM_STATE로 서버와 맞춤)
    private String opponentName = "---";
    private JDialog prompt;             // 지금 떠 있는 선택 창 (선 선택 / 바닥 선택 / 고·스톱 / 국진)

    public GamePanel(GameClient client, Session session, Runnable onLeave) {
        this.client = client;
        this.session = session;
        this.onLeave = onLeave;

        // [5-6] 게임 화면 전체를 BorderLayout으로 설정 (CENTER 담요 / EAST 사이드)
        setLayout(new BorderLayout());

        // [5-7] CENTER: 담요 화면(FeltPanel)
        add(felt, BorderLayout.CENTER);
        felt.setOnCardClick(this::onCardClick);     // [8-8] 손패 클릭 -> PLAY 요청
        felt.setOnReady(this::toggleReady);         // [8-5] 준비 버튼 -> READY 요청

        // [5-8] EAST: 사이드 패널 (폭 280 고정, 어두운 배경, 내부도 BorderLayout)
        JPanel Side = new JPanel(new BorderLayout());
        Side.setPreferredSize(new Dimension(280, 0));
        Side.setBackground(new Color(0x1E, 0x1E, 0x1E));
        add(Side, BorderLayout.EAST);

        // [7-4] 채팅 메시지 목록 (직접 수정 불가, 긴 글은 줄바꿈)
        chat.setEditable(false);
        chat.setLineWrap(true);
        // [7-4] 목록을 스크롤 가능하게 감싸 사이드의 CENTER에 배치
        JScrollPane scroll = new JScrollPane(chat);
        Side.add(scroll, BorderLayout.CENTER);

        // [7-4] 입력줄: 입력칸(CENTER) + "전송" 버튼(EAST)
        JPanel row = new JPanel(new BorderLayout());
        JTextField input = new JTextField();
        JButton send = new JButton("전송");
        row.add(input, BorderLayout.CENTER);
        row.add(send, BorderLayout.EAST);

        // [5-8][7-4] 사이드 아래쪽: 입력줄(NORTH) + "나가기" 버튼(SOUTH)
        JButton BtnExit = new JButton("나가기");
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(row, BorderLayout.NORTH);
        bottom.add(BtnExit, BorderLayout.SOUTH);
        Side.add(bottom, BorderLayout.SOUTH);
        // [8-9] "나가기": LEAVE_ROOM 요청 후 로비로 (7단계까지는 onLeave만 실행했음)
        BtnExit.addActionListener(e -> leaveRoom());

        // [7-4] "전송" 버튼과 엔터 키 모두 sendChat 실행
        send.addActionListener(e -> sendChat(input, chat));
        input.addActionListener(e -> sendChat(input, chat));
    }

    // ====================================================================== 방 들어가기 / 나가기

    // [8-5] 방에 들어갔을 때 화면을 초기 상태로 되돌림 (로비가 부름)
    public void enterRoom(String newRoomId) {
        closePrompt();
        roomId = newRoomId;
        session.roomId = newRoomId;
        lastState = null;
        inGame = false;
        myReady = false;
        opponentName = "---";
        chat.setText("");
        felt.clearTable();
        felt.setStatusText("상대를 기다리는 중...");
        felt.setReadyButton(true, false, "준비");
    }

    // [8-9] 나가기 버튼: LEAVE_ROOM 요청. 성공(ACK)하면 로비로.
    private void leaveRoom() {
        client.request("LEAVE_ROOM", new JsonObject(), resp -> {
            if (GameClient.isError(resp)) {
                showError(resp);          // 방 안이 아니었다는 오류 등. 화면은 로비로 돌려 보냄
            }
            doLeave();
        });
    }

    // 방 정보를 비우고 로비로 돌아감. 두 번 불려도 한 번만 동작
    private void doLeave() {
        if (roomId == null) return;
        closePrompt();
        roomId = null;
        session.roomId = null;
        lastState = null;
        inGame = false;
        onLeave.run();
    }

    // ====================================================================== [8-5] ROOM_STATE / 준비

    // ROOM_STATE: 방 상태와 players(playerId, nickname, seat, ready, balance)
    public void onRoomState(JsonObject b) {
        String id = Jx.str(b, "roomId");
        if (roomId == null || (id != null && !id.equals(roomId))) return;   // 내가 있는 방의 이야기만

        String status = Jx.str(b, "status");
        JsonArray players = Jx.arr(b, "players");
        boolean opponentReady = false;
        boolean hasOpponent = false;
        opponentName = "---";
        for (JsonElement e : players) {
            if (!e.isJsonObject()) continue;
            JsonObject p = e.getAsJsonObject();
            if (session.playerId != null && session.playerId.equals(Jx.str(p, "playerId"))) {
                myReady = Jx.bool(p, "ready", false);
                long bal = Jx.lng(p, "balance", Long.MIN_VALUE);
                if (bal != Long.MIN_VALUE) session.balance = bal;
            } else {
                hasOpponent = true;
                opponentReady = Jx.bool(p, "ready", false);
                String nick = Jx.str(p, "nickname");
                opponentName = (nick == null) ? "상대" : nick;
            }
        }

        if (inGame && "PLAYING".equals(status)) return;     // 게임 중 화면은 GAME_STATE가 그림

        if (!inGame) {
            felt.setOpponentText("상대: " + opponentName);
            if ("CLOSED".equals(status)) {
                felt.setStatusText("방이 닫혔습니다.");
                felt.setReadyButton(false, false, "준비");
            } else if ("DEALER_SELECTION".equals(status)) {
                felt.setStatusText("선을 정하는 중...");
                felt.setReadyButton(false, false, "준비");
            } else if (!hasOpponent) {
                felt.setStatusText("상대를 기다리는 중...");
                felt.setReadyButton(true, false, "준비");
            } else {
                felt.setStatusText("준비 버튼을 눌러 주세요. (상대: " + (opponentReady ? "준비 완료" : "준비 전") + ")");
                felt.setReadyButton(true, true, myReady ? "준비 취소" : "준비");
            }
        }
    }

    // [8-5] 준비 버튼: READY {ready:true/false}
    private void toggleReady() {
        final boolean want = !myReady;
        JsonObject payload = new JsonObject();
        payload.addProperty("ready", want);
        client.request("READY", payload, resp -> {
            if (GameClient.isError(resp)) {
                showError(resp);
                return;
            }
            myReady = want;     // 이어서 오는 ROOM_STATE가 최종 값으로 다시 맞춰 줌
            felt.setReadyButton(true, true, myReady ? "준비 취소" : "준비");
        });
    }

    // ====================================================================== [8-6] 선 선택

    // DEALER_SELECTION: roomId, selectionId, drawNumber, choices(Card 배열), remainingMillis
    // TODO(확인): 선택지 카드를 뒷면으로 보여 주는 것이 맞는지 (앞면이 공개되는 규칙이면 CardCatalog.pathOf(id)로 바꿈)
    // TODO(선택): remainingMillis로 남은 시간 카운트다운 표시
    public void onDealerSelection(JsonObject b) {
        if (roomId == null) return;
        closePrompt();
        inGame = false;
        felt.setReadyButton(false, false, "준비");

        JsonArray choices = Jx.arr(b, "choices");
        if (choices.size() == 0) {
            felt.setStatusText("상대의 선택을 기다리는 중...");
            return;
        }
        felt.setStatusText("선을 정합니다. 카드를 한 장 고르세요.");

        final JsonElement selectionId = b.get("selectionId");
        final JsonElement drawNumber = b.get("drawNumber");

        JPanel cards = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 8));
        for (JsonElement c : choices) {
            final int cardId = Jx.cardId(c);
            CardView cv = new CardView(CardCatalog.BACK);
            cv.setOnClick(() -> selectDealer(selectionId, drawNumber, cardId));
            cards.add(cv);
        }
        showCardDialog("선 정하기", "카드를 한 장 고르세요.", cards);
    }

    // SELECT_DEALER {selectionId, drawNumber, cardId} -> ACK의 choiceResult: WAITING / RESELECT / REDRAW / DECIDED
    private void selectDealer(JsonElement selectionId, JsonElement drawNumber, int cardId) {
        closePrompt();
        JsonObject payload = new JsonObject();
        if (selectionId != null) payload.add("selectionId", selectionId);
        if (drawNumber != null) payload.add("drawNumber", drawNumber);
        payload.addProperty("cardId", cardId);
        client.request("SELECT_DEALER", payload, resp -> {
            if (GameClient.isError(resp)) {
                showError(resp);
                return;
            }
            String result = Jx.str(Jx.body(resp), "choiceResult");
            if ("WAITING".equals(result)) felt.setStatusText("상대의 선택을 기다리는 중...");
            else if ("RESELECT".equals(result)) felt.setStatusText("상대와 같은 카드입니다. 다시 고르세요.");
            else if ("REDRAW".equals(result)) felt.setStatusText("다시 뽑습니다...");
            else if ("DECIDED".equals(result)) felt.setStatusText("선이 정해졌습니다. 곧 시작합니다...");
        });
    }

    // ====================================================================== [8-7] 게임 상태 그리기

    // GAME_START / GAME_STATE: 서버가 개인별로 만들어 준 현재 상태 (상대 손패 내용은 오지 않음)
    // 필드: gameId, roundNumber(roundId), version, currentPlayerId, phase, ownHand, opponentHandCount,
    //       stockCount, floor, ownCaptures, opponentCaptures, ownScore, opponentScore, choices, ownGo ...
    public void onGameState(JsonObject b) {
        if (roomId == null) return;
        closePrompt();
        inGame = true;
        lastState = b;

        String phase = Jx.str(b, "phase");
        String current = Jx.str(b, "currentPlayerId");
        boolean myTurn = session.playerId != null && session.playerId.equals(current);

        List<Integer> handIds = idList(Jx.arr(b, "ownHand"));
        List<Integer> floorIds = idList(Jx.arr(b, "floor"));
        int stock = Jx.num(b, "stockCount", 0);

        // 내 손패는 "내 차례의 PLAY 단계"에서만 클릭해서 낼 수 있음
        felt.setHand(handIds, myTurn && "PLAY".equals(phase));
        felt.setFloor(floorIds);
        felt.setOpponentHandCount(Jx.num(b, "opponentHandCount", 0));
        felt.setReadyButton(false, false, "준비");

        felt.setOpponentText("상대: " + opponentName
                + "   점수 " + Jx.num(b, "opponentScore", 0)
                + "   획득 " + Jx.count(b.get("opponentCaptures")) + "장");
        felt.setMyInfoText("나: " + session.nickname
                + "   점수 " + Jx.num(b, "ownScore", 0)
                + "   고 " + Jx.num(b, "ownGo", 0)
                + "   획득 " + Jx.count(b.get("ownCaptures")) + "장"
                + "   (더미 " + stock + "장)");

        if ("FINISHED".equals(phase)) {
            felt.setStatusText("게임이 끝났습니다.");
            return;
        }
        if (!myTurn) {
            felt.setStatusText("상대 차례입니다.");
            return;
        }

        // ---- 내 차례: 단계(phase)에 따라 할 일이 다름 ----
        if ("PLAY".equals(phase)) {
            felt.setStatusText("내 차례입니다. 낼 카드를 클릭하세요.");
        } else if ("HAND_CHOICE".equals(phase) || "STOCK_CHOICE".equals(phase)) {
            // [8-8] 바닥에 먹을 수 있는 패가 둘 이상일 때: choices 중 하나를 고름 (SELECT)
            felt.setStatusText("먹을 바닥패를 고르세요.");
            askCapture(Jx.arr(b, "choices"));
        } else if ("GUKJIN".equals(phase)) {
            // [8-8] 국진(9월 열끗) 분류: 피로 쓸지 열끗으로 쓸지
            felt.setStatusText("국진을 어떻게 사용할지 고르세요.");
            showPrompt("국진", "국진(9월 열끗)을 어떻게 사용할까요?",
                    new String[] {"열끗으로 사용", "피로 사용"},
                    i -> sendAction(i == 0 ? "GUKJIN_ANIMAL" : "GUKJIN_PI", null));
        } else if ("GO_STOP".equals(phase)) {
            // [7-3] 고/스톱 선택창을 [8-8]에서 서버 요청(GO / STOP)과 연결
            felt.setStatusText("고 또는 스톱을 선택하세요.");
            askGoStop(Jx.num(b, "ownScore", 0));
        } else {
            felt.setStatusText("진행 중: " + phase);   // TODO(확인): 이 외의 단계가 있는지
        }
        // TODO: 흔들기(SHAKE), 폭탄(BOMB), 서비스패(SERVICE), 폭탄 뒤집기(DECK_ONLY)는
        //       서버 규칙이 어떤 상황에서 요구하는지 확인한 뒤 같은 방식으로 sendAction("SHAKE", cardId) 등을 연결
    }

    // Card 객체 배열 -> 카드 ID 목록
    private List<Integer> idList(JsonArray arr) {
        List<Integer> ids = new ArrayList<>();
        for (JsonElement e : arr) {
            int id = Jx.cardId(e);
            if (id >= 0) ids.add(id);
        }
        return ids;
    }

    // ====================================================================== [8-8] 행동 보내기

    // 손패의 카드를 클릭 -> GAME_ACTION / PLAY
    private void onCardClick(int cardId) {
        felt.lockHand();             // 응답이 올 때까지 다시 클릭되지 않게
        sendAction("PLAY", cardId);
    }

    // [8-8] GAME_ACTION: 최신 상태의 gameId, roundNumber(또는 roundId), version을 그대로 붙여 보낸다.
    //       행동 종류(action): PLAY, SELECT, GO, STOP, GUKJIN_PI, GUKJIN_ANIMAL ... (cardId는 필요한 것만)
    private void sendAction(String action, Integer cardId) {
        if (lastState == null) return;
        JsonObject payload = new JsonObject();
        copy(lastState, payload, "gameId");
        if (lastState.has("roundNumber")) copy(lastState, payload, "roundNumber");
        else copy(lastState, payload, "roundId");
        copy(lastState, payload, "version");
        payload.addProperty("action", action);
        if (cardId != null) payload.addProperty("cardId", cardId);

        client.request("GAME_ACTION", payload, resp -> {
            if (GameClient.isError(resp)) {
                // STALE_STATE(오래된 판/버전), NOT_YOUR_TURN, INVALID_SELECTION, INVALID_ACTION ...
                showError(resp);
                // 오류 뒤에는 서버가 최신 GAME_STATE를 다시 보낼 수 있음. 오지 않으면 화면이 잠긴 채이므로 다시 그림
                if (lastState != null) onGameState(lastState);
            }
            // 성공(ACK)이면 이어서 오는 GAME_STATE가 화면을 갱신함
        });
    }

    private static void copy(JsonObject from, JsonObject to, String key) {
        JsonElement e = from.get(key);
        if (e != null) to.add(key, e);
    }

    // [8-8] 바닥 매칭 선택: 후보 카드들을 보여 주고 하나를 고르면 SELECT
    private void askCapture(JsonArray choices) {
        JPanel cards = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 8));
        for (JsonElement c : choices) {
            final int cardId = Jx.cardId(c);
            CardView cv = new CardView(CardCatalog.pathOf(cardId));
            cv.setOnClick(() -> {
                closePrompt();
                sendAction("SELECT", cardId);
            });
            cards.add(cv);
        }
        showCardDialog("바닥패 선택", "먹을 바닥패를 고르세요.", cards);
    }

    // [7-3] 고/스톱 선택창 -> [8-8] 고 = GO, 스톱 = STOP
    private void askGoStop(int score) {
        showPrompt("고? 스톱!", "현재 " + score + "점 입니다. 고 하시겠습니까?",
                new String[] {"고", "스톱"},
                i -> sendAction(i == 0 ? "GO" : "STOP", null));
    }

    // ====================================================================== [8-9] 결과 / 방에서 나가짐

    // GAME_OVER: gameId, roomId, roundNumber, reason, winnerId, settlement, results[playerId, balance, bankrupt], nextDealerId ...
    // TODO(확인): settlement 안의 필드 이름. 지금은 안에 있는 값을 "이름: 값"으로 그대로 보여 줌
    public void onGameOver(JsonObject b) {
        if (roomId == null) return;
        closePrompt();
        inGame = false;
        lastState = null;
        felt.lockHand();

        String winnerId = Jx.str(b, "winnerId");
        boolean iWon = winnerId != null && winnerId.equals(session.playerId);
        String reason = Jx.str(b, "reason");

        // 정산 후 내 보유금 갱신
        for (JsonElement e : Jx.arr(b, "results")) {
            if (!e.isJsonObject()) continue;
            JsonObject r = e.getAsJsonObject();
            if (session.playerId != null && session.playerId.equals(Jx.str(r, "playerId"))) {
                long bal = Jx.lng(r, "balance", Long.MIN_VALUE);
                if (bal != Long.MIN_VALUE) session.balance = bal;
            }
        }

        StringBuilder sb = new StringBuilder();
        if (winnerId == null) sb.append("무승부(또는 무효)입니다.\n");
        else sb.append(iWon ? "승리했습니다!\n" : "패배했습니다.\n");
        sb.append("종료 사유: ").append(reasonText(reason)).append("\n");
        JsonObject settlement = Jx.obj(b, "settlement");
        if (settlement != null) {
            sb.append("\n[정산]\n");
            for (Map.Entry<String, JsonElement> en : settlement.entrySet()) {
                if (en.getValue().isJsonPrimitive()) {
                    sb.append(en.getKey()).append(": ").append(en.getValue().getAsString()).append("\n");
                }
            }
        }
        sb.append("\n보유금: ").append(Session.won(session.balance));

        felt.setStatusText("게임이 끝났습니다. 다시 하려면 준비를 눌러 주세요.");
        felt.setReadyButton(true, true, "준비");
        myReady = false;
        JOptionPane.showMessageDialog(this, sb.toString(), "게임 종료", JOptionPane.INFORMATION_MESSAGE);
        // 결과창을 닫으면 이전 판의 카드를 치우고 다음 판(준비)을 기다림
        if (!inGame && roomId != null) {
            felt.clearTable();
            felt.setOpponentText("상대: " + opponentName);
        }
    }

    private static String reasonText(String reason) {
        if (reason == null) return "-";
        switch (reason) {
            case "NORMAL": return "일반 종료";
            case "TONGTONG": return "총통";
            case "THREE_PPEOK": return "뻑 세 번";
            case "DRAW": return "무승부(나가리)";
            case "VOID": return "무효";
            case "FORFEIT": return "기권";
            default: return reason;
        }
    }

    // ROOM_LEFT: roomId, balance, reason (강제/일반 방 이탈)
    public void onRoomLeft(JsonObject b) {
        long bal = Jx.lng(b, "balance", Long.MIN_VALUE);
        if (bal != Long.MIN_VALUE) session.balance = bal;
        if (roomId == null) return;          // 내가 나가기를 눌러 이미 로비에 와 있음
        String reason = Jx.str(b, "reason");
        JOptionPane.showMessageDialog(this,
                "방에서 나왔습니다. (" + (reason == null ? "-" : reason) + ")\n보유금: " + Session.won(session.balance));
        doLeave();
    }

    // ====================================================================== [8-10] 채팅

    // CHAT_MESSAGE: nickname, text (서버에 구현되면 사용. 규격 초안은 별도 문서)
    public void onChat(JsonObject b) {
        String nick = Jx.str(b, "nickname");
        String text = Jx.str(b, "text");
        if (text == null) return;
        chat.append((nick == null ? "?" : nick) + " : " + text + "\n");
        chat.setCaretPosition(chat.getDocument().getLength());
    }

    // [7-4] 채팅 전송: 입력 내용을 보냄
    private void sendChat(JTextField input, JTextArea area) {
        // [7-4] 앞뒤 공백을 제거하고, 비어 있으면 전송하지 않음
        String text = input.getText().trim();
        if (text.isEmpty()) return;
        if (CHAT_SERVER_READY) {
            // [8-10] 서버 채팅이 구현된 경우: CHAT 요청만 보내고, 목록 표시는 돌아오는 CHAT_MESSAGE로 함
            JsonObject payload = new JsonObject();
            payload.addProperty("text", text);
            client.request("CHAT", payload, resp -> {
                if (GameClient.isError(resp)) area.append("[전송 실패] " + GameClient.errorText(resp) + "\n");
            });
        } else {
            // [7-4] 서버 채팅이 없는 동안: 내 화면에만 "나 : 내용" 추가
            area.append("나 : " + text + "\n");
        }
        // [7-4] 입력칸 비우기
        input.setText("");
        // [7-4] 새 메시지가 보이도록 스크롤을 맨 아래로
        area.setCaretPosition(area.getDocument().getLength());
    }

    // ====================================================================== 선택 창 도우미

    // 오류를 알림창으로 보여 줌: payload.code / payload.message
    private void showError(JsonObject resp) {
        JOptionPane.showMessageDialog(this, GameClient.errorText(resp), "요청 실패", JOptionPane.WARNING_MESSAGE);
    }

    // 지금 떠 있는 선택 창을 닫음 (새 상태가 오면 이전 선택 창은 의미가 없어지므로)
    private void closePrompt() {
        if (prompt != null) {
            prompt.dispose();
            prompt = null;
        }
    }

    // [7-3] JOptionPane 버튼 선택창을 "기다리지 않는" 방식(비모달)으로 띄움.
    //       모달 창은 닫힐 때까지 이벤트 처리가 멈추는 것처럼 보이므로, 서버 상태가 갱신될 수 있는 게임 중에는 비모달이 안전하다.
    //       X와 Esc로는 닫히지 않고 버튼으로만 선택 (7-3과 동일).
    private void showPrompt(String title, String message, String[] options, IntConsumer onChoose) {
        closePrompt();
        final JOptionPane pane = new JOptionPane(message, JOptionPane.QUESTION_MESSAGE,
                JOptionPane.DEFAULT_OPTION, null, options, options[0]);
        final JDialog dialog = pane.createDialog(this, title);
        dialog.setModal(false);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        final boolean[] done = {false};
        pane.addPropertyChangeListener(JOptionPane.VALUE_PROPERTY, ev -> {
            Object v = ev.getNewValue();
            if (done[0] || v == null || v == JOptionPane.UNINITIALIZED_VALUE) return;
            for (int i = 0; i < options.length; i++) {
                if (options[i].equals(v)) {
                    done[0] = true;
                    dialog.dispose();
                    if (prompt == dialog) prompt = null;
                    onChoose.accept(i);
                    return;
                }
            }
        });
        prompt = dialog;
        dialog.setVisible(true);
    }

    // 카드 그림을 눌러 고르는 창(선 선택, 바닥패 선택). 닫기 버튼으로는 닫히지 않음
    private void showCardDialog(String title, String message, JPanel cards) {
        closePrompt();
        java.awt.Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner, title, java.awt.Dialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        JLabel label = new JLabel(message, JLabel.CENTER);
        label.setBorder(BorderFactory.createEmptyBorder(12, 12, 0, 12));
        dialog.getContentPane().add(label, BorderLayout.NORTH);
        dialog.getContentPane().add(cards, BorderLayout.CENTER);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        prompt = dialog;
        dialog.setVisible(true);
    }
}
