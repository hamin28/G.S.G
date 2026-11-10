package gsg.client.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gsg.client.card.CardCatalog;
import gsg.client.card.CardInfo;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 게임 방 화면 (프로토콜 3~6장).
 *
 * 화면은 두 상태를 오간다.
 *  - 대기: ROOM_STATE.status == WAITING. 자리 2개와 준비(READY) 버튼.
 *  - 게임: GAME_START / GAME_STATE 수신 후. 위쪽 상대, 가운데 바닥패·덱, 아래쪽 내 획득패와 손패.
 * 오른쪽에는 항상 채팅(ChatPanel)이 있다.
 *
 * 버튼·카드 클릭 가능 여부는 서버가 보낸 phase, currentPlayerId, pendingChoice로만 정한다.
 * 요청을 보낸 뒤에는 서버 응답(GAME_STATE 또는 ERROR)이 올 때까지 다시 누를 수 없게 막는다.
 */
public class GameRoomPanel extends JPanel {

    /** 방 안에서 일어나는 사용자 동작 */
    public interface Actions {
        void ready(boolean ready);
        void leave();
        void chat(String message);
        void playCard(int cardId);
        void selectCapture(String choiceId, int cardId);
        void decide(String choiceId, String decision);
    }

    private static final String[] GROUP_TYPES = {"GWANG", "ANIMAL", "RIBBON", "PI"};

    private final Actions actions;
    private String myPlayerId;

    // 방 상태
    private final Map<String, String> nicknames = new LinkedHashMap<>();
    private final Map<String, Boolean> readyMap = new LinkedHashMap<>();
    private String roomStatus = "WAITING";
    private int maxPlayers = 2;

    // 게임 상태
    private JsonObject game;              // 마지막 GAME_START / GAME_STATE payload
    private JsonObject pendingChoice;     // 나에게 온 선택 (없으면 null)
    private String handledChoiceId;       // 이미 응답한 choiceId (중복 선택 방지)
    private boolean waitingResponse;      // 요청을 보내고 서버 응답을 기다리는 중

    // ---- 헤더
    private final JLabel roomTitleLabel = Theme.label("방", Font.BOLD, 18, Color.WHITE);
    private final JLabel roomStatusLabel = Theme.label("대기 중", Font.BOLD, 12, Theme.GOLD);
    private final JButton leaveButton = Theme.style(new JButton("나가기"), Theme.INK, Color.WHITE);

    // ---- 가운데 (대기 / 게임 전환)
    private final CardLayout centerCards = new CardLayout();
    private final JPanel center = new JPanel(centerCards);

    // 대기 화면
    private final JPanel seatsPanel = new JPanel(new GridLayout(1, 2, 24, 0));
    private final JButton readyButton = Theme.primary(new JButton("준비"));
    private final JLabel waitingHint = Theme.label(" ", Font.PLAIN, 14, new Color(0xD9, 0xE8, 0xDE));

    // 게임 화면
    private final PlayerArea opponentArea = new PlayerArea(false);
    private final PlayerArea myArea = new PlayerArea(true);
    private final CardRow tableRow = new CardRow(66, 74);
    private final CardRow deckRow = new CardRow(56, 3);
    private final JLabel deckLabel = Theme.label("덱 0장", Font.BOLD, 13, Color.WHITE);
    private final CardRow handRow = new CardRow(76, 82);
    private final JLabel turnLabel = Theme.label(" ", Font.BOLD, 16, Color.WHITE);
    private final JButton goButton = Theme.style(new JButton("고"), Theme.RED, Color.WHITE);
    private final JButton stopButton = Theme.style(new JButton("스톱"), Color.WHITE, Theme.INK);

    private final ChatPanel chat;

    public GameRoomPanel(Actions actions) {
        this.actions = actions;
        this.chat = new ChatPanel(actions::chat);
        setLayout(new BorderLayout());
        setBackground(Theme.FELT);

        add(buildHeader(), BorderLayout.NORTH);
        center.setOpaque(false);
        center.add(buildWaitingView(), "waiting");
        center.add(buildGameView(), "game");
        add(center, BorderLayout.CENTER);
        add(chat, BorderLayout.EAST);

        leaveButton.addActionListener(e -> onLeave());
        readyButton.addActionListener(e -> {
            boolean now = Boolean.TRUE.equals(readyMap.get(myPlayerId));
            readyButton.setEnabled(false);
            actions.ready(!now);
        });
        handRow.setOnClick(this::onHandClick);
        tableRow.setOnClick(this::onTableClick);
        goButton.addActionListener(e -> onDecide("GO"));
        stopButton.addActionListener(e -> onDecide("STOP"));
    }

    // ============================================================ 화면 구성

    private JPanel buildHeader() {
        JPanel h = new JPanel(new BorderLayout());
        h.setBackground(Theme.INK);
        Theme.pad(h, 10, 18, 10, 18);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        left.setOpaque(false);
        left.add(roomTitleLabel);
        left.add(roomStatusLabel);
        h.add(left, BorderLayout.WEST);
        h.add(leaveButton, BorderLayout.EAST);
        return h;
    }

    private JPanel buildWaitingView() {
        JPanel p = new FeltPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.insets = new Insets(10, 0, 10, 0);

        c.gridy = 0;
        p.add(Theme.label("상대를 기다리고 있습니다", Font.BOLD, 24, Color.WHITE), c);
        c.gridy = 1;
        p.add(waitingHint, c);

        seatsPanel.setOpaque(false);
        c.gridy = 2;
        c.insets = new Insets(24, 0, 24, 0);
        p.add(seatsPanel, c);

        readyButton.setFont(Theme.font(Font.BOLD, 18));
        readyButton.setPreferredSize(new Dimension(220, 52));
        c.gridy = 3;
        c.insets = new Insets(0, 0, 0, 0);
        p.add(readyButton, c);
        return p;
    }

    private JPanel buildGameView() {
        JPanel p = new FeltPanel(new GridBagLayout());
        Theme.pad(p, 10, 16, 10, 16);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 0, 4, 0);

        // 위: 상대
        c.gridy = 0;
        p.add(opponentArea, c);

        // 가운데: 바닥패 + 덱
        JPanel table = new JPanel(new BorderLayout(18, 0));
        table.setOpaque(false);
        table.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.FELT_LINE, 2, true),
                BorderFactory.createEmptyBorder(10, 14, 6, 14)));
        JPanel deckBox = new JPanel();
        deckBox.setOpaque(false);
        deckBox.setLayout(new BoxLayout(deckBox, BoxLayout.Y_AXIS));
        deckRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        deckLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        deckBox.add(Box.createVerticalGlue());
        deckBox.add(deckRow);
        deckBox.add(Box.createVerticalStrut(4));
        deckBox.add(deckLabel);
        deckBox.add(Box.createVerticalGlue());
        table.add(deckBox, BorderLayout.WEST);

        // 바닥패는 영역 세로 가운데에 놓는다
        JPanel tableBox = new JPanel(new GridBagLayout());
        tableBox.setOpaque(false);
        GridBagConstraints t = new GridBagConstraints();
        t.gridx = 0;
        t.anchor = GridBagConstraints.WEST;
        t.weightx = 1;
        t.fill = GridBagConstraints.HORIZONTAL;
        t.gridy = 0;
        tableBox.add(Theme.label("바닥패", Font.BOLD, 12, new Color(0xBF, 0xD9, 0xC7)), t);
        t.gridy = 1;
        tableBox.add(tableRow, t);
        table.add(tableBox, BorderLayout.CENTER);
        c.gridy = 1;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(8, 0, 8, 0);
        p.add(table, c);
        c.weighty = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 0, 4, 0);

        // 차례 안내 + 고/스톱
        JPanel turnBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        turnBar.setOpaque(false);
        goButton.setFont(Theme.font(Font.BOLD, 16));
        stopButton.setFont(Theme.font(Font.BOLD, 16));
        goButton.setPreferredSize(new Dimension(110, 40));
        stopButton.setPreferredSize(new Dimension(110, 40));
        turnBar.add(turnLabel);
        turnBar.add(goButton);
        turnBar.add(stopButton);
        c.gridy = 2;
        p.add(turnBar, c);

        // 아래: 나
        c.gridy = 3;
        p.add(myArea, c);

        // 내 손패
        JPanel hand = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        hand.setOpaque(false);
        hand.add(handRow);
        c.gridy = 4;
        p.add(hand, c);
        return p;
    }

    // ============================================================ MainFrame이 호출

    /** 방에 들어올 때 초기화 */
    public void enterRoom(String myPlayerId, String title) {
        this.myPlayerId = myPlayerId;
        roomTitleLabel.setText(title == null ? "게임 방" : title);
        nicknames.clear();
        readyMap.clear();
        game = null;
        pendingChoice = null;
        handledChoiceId = null;
        waitingResponse = false;
        chat.clear();
        chat.addSystem("방에 입장했습니다.");
        centerCards.show(center, "waiting");
    }

    /** ROOM_STATE 이벤트 */
    public void setRoomState(JsonObject p) {
        roomTitleLabel.setText(p.get("title").getAsString());
        maxPlayers = p.get("maxPlayers").getAsInt();
        String prevStatus = roomStatus;
        roomStatus = p.get("status").getAsString();

        Map<String, String> before = new LinkedHashMap<>(nicknames);
        nicknames.clear();
        readyMap.clear();
        for (JsonElement el : p.getAsJsonArray("players")) {
            JsonObject pl = el.getAsJsonObject();
            String id = pl.get("playerId").getAsString();
            nicknames.put(id, pl.get("nickname").getAsString());
            readyMap.put(id, pl.get("ready").getAsBoolean());
        }
        if (!before.isEmpty()) {
            for (String id : nicknames.keySet())
                if (!before.containsKey(id)) chat.addSystem(nicknames.get(id) + " 님이 입장했습니다.");
            for (String id : before.keySet())
                if (!nicknames.containsKey(id)) chat.addSystem(before.get(id) + " 님이 나갔습니다.");
        }

        boolean playing = "PLAYING".equals(roomStatus);
        roomStatusLabel.setText(playing ? "게임 중" : "대기 중 · " + nicknames.size() + "/" + maxPlayers);
        if (!playing) {
            if ("PLAYING".equals(prevStatus)) game = null;
            pendingChoice = null;
            centerCards.show(center, "waiting");
        }
        refreshWaiting();
        if (game != null) refreshGame();
    }

    /** GAME_START / GAME_STATE 이벤트 */
    public void setGameState(JsonObject p, boolean start) {
        if (game != null && start == false
                && p.get("gameId").getAsString().equals(game.get("gameId").getAsString())
                && p.get("revision").getAsInt() < game.get("revision").getAsInt()) {
            return;   // 이미 받은 것보다 오래된 상태는 무시
        }
        if (start) {
            chat.addSystem("게임이 시작되었습니다!");
            handledChoiceId = null;
        }
        game = p;
        waitingResponse = false;
        pendingChoice = p.has("pendingChoice") && p.get("pendingChoice").isJsonObject()
                ? p.getAsJsonObject("pendingChoice") : null;
        centerCards.show(center, "game");
        refreshGame();
    }

    /** CHOICE_REQUIRED 이벤트 (같은 choiceId는 한 번만 처리) */
    public void onChoiceRequired(JsonObject p) {
        String id = p.get("choiceId").getAsString();
        if (id.equals(handledChoiceId)) return;
        pendingChoice = p;
        waitingResponse = false;
        refreshGame();
    }

    /** GAME_OVER 이벤트 */
    public void onGameOver(JsonObject p) {
        pendingChoice = null;
        refreshGame();
        String reason = p.get("reason").getAsString();
        String winner = p.get("winnerId").isJsonNull() ? null : p.get("winnerId").getAsString();

        StringBuilder sb = new StringBuilder();
        if (winner == null) sb.append("STOP".equals(reason) ? "무승부입니다." : "DRAW".equals(reason) ? "나가리(무승부)입니다." : "상대가 나가서 게임이 취소되었습니다.");
        else sb.append(winner.equals(myPlayerId) ? "승리했습니다!" : name(winner) + " 님이 이겼습니다.");
        sb.append("\n\n");
        for (JsonElement el : p.getAsJsonArray("results")) {
            JsonObject r = el.getAsJsonObject();
            String id = r.get("playerId").getAsString();
            sb.append(name(id)).append(id.equals(myPlayerId) ? " (나)" : "")
              .append("  ").append(r.get("score").getAsInt()).append("점, ")
              .append(r.get("goCount").getAsInt()).append("고\n");
        }
        chat.addSystem("게임 종료 — " + sb.toString().split("\n")[0]);
        JOptionPane.showMessageDialog(this, sb.toString(), "게임 결과", JOptionPane.INFORMATION_MESSAGE);
    }

    /** CHAT_MESSAGE 이벤트 */
    public void addChat(JsonObject p) {
        String id = p.get("playerId").getAsString();
        chat.addMessage(p.get("nickname").getAsString(), p.get("message").getAsString(), id.equals(myPlayerId));
    }

    /** 이 화면에서 보낸 요청이 ERROR로 거절되었을 때 */
    public void onError(String requestType, String message) {
        waitingResponse = false;
        if ("READY".equals(requestType)) readyButton.setEnabled(true);
        chat.addSystem("요청 실패: " + message);
        refreshGame();
    }

    public void onReadyAck() {
        readyButton.setEnabled(true);
    }

    // ============================================================ 화면 갱신

    private String name(String playerId) {
        String n = nicknames.get(playerId);
        return n != null ? n : playerId;
    }

    private void refreshWaiting() {
        seatsPanel.removeAll();
        List<String> ids = new ArrayList<>(nicknames.keySet());
        for (int i = 0; i < maxPlayers; i++) {
            if (i < ids.size()) {
                String id = ids.get(i);
                seatsPanel.add(seat(nicknames.get(id), id.equals(myPlayerId), Boolean.TRUE.equals(readyMap.get(id))));
            } else {
                seatsPanel.add(seat(null, false, false));
            }
        }
        seatsPanel.revalidate();
        seatsPanel.repaint();

        boolean meReady = Boolean.TRUE.equals(readyMap.get(myPlayerId));
        readyButton.setText(meReady ? "준비 취소" : "준비");
        Theme.style(readyButton, meReady ? Color.WHITE : Theme.RED, meReady ? Theme.INK : Color.WHITE);
        readyButton.setFont(Theme.font(Font.BOLD, 18));

        if (nicknames.size() < maxPlayers) waitingHint.setText("빈 자리가 있습니다. 상대가 들어오면 함께 준비하세요.");
        else waitingHint.setText("모두 준비하면 게임이 자동으로 시작됩니다.");
    }

    private JPanel seat(String nickname, boolean me, boolean ready) {
        JPanel s = new JPanel();
        s.setLayout(new BoxLayout(s, BoxLayout.Y_AXIS));
        s.setPreferredSize(new Dimension(220, 150));
        s.setBackground(nickname == null ? new Color(0, 0, 0, 0) : Theme.PAPER);
        s.setOpaque(nickname != null);
        s.setBorder(nickname == null
                ? BorderFactory.createDashedBorder(new Color(0xBF, 0xD9, 0xC7), 2, 6, 4, true)
                : BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 4, 0, ready ? Theme.RED : Theme.PAPER_DARK),
                        BorderFactory.createEmptyBorder(16, 16, 16, 16)));
        JLabel n = Theme.label(nickname == null ? "빈 자리" : nickname + (me ? " (나)" : ""), Font.BOLD, 20,
                nickname == null ? new Color(0xBF, 0xD9, 0xC7) : Theme.INK);
        JLabel st = Theme.label(nickname == null ? "참가자를 기다리는 중" : ready ? "준비 완료" : "준비 전", Font.BOLD, 14,
                nickname == null ? new Color(0xBF, 0xD9, 0xC7) : ready ? Theme.RED : Theme.MUTED);
        for (JLabel l : new JLabel[]{n, st}) {
            l.setAlignmentX(Component.CENTER_ALIGNMENT);
            l.setHorizontalAlignment(SwingConstants.CENTER);
        }
        s.add(Box.createVerticalGlue());
        s.add(n);
        s.add(Box.createVerticalStrut(10));
        s.add(st);
        s.add(Box.createVerticalGlue());
        return s;
    }

    private void refreshGame() {
        if (game == null) return;
        String phase = game.get("phase").getAsString();
        String current = game.get("currentPlayerId").isJsonNull() ? null : game.get("currentPlayerId").getAsString();
        boolean myTurn = myPlayerId != null && myPlayerId.equals(current);

        // 플레이어별 정보
        for (JsonElement el : game.getAsJsonArray("players")) {
            JsonObject pl = el.getAsJsonObject();
            String id = pl.get("playerId").getAsString();
            PlayerArea area = id.equals(myPlayerId) ? myArea : opponentArea;
            area.update(name(id), pl.get("handCount").getAsInt(), toIds(pl.getAsJsonArray("capturedCards")),
                    pl.get("score").getAsInt(), pl.get("goCount").getAsInt(), id.equals(current));
        }

        tableRow.setCards(toIds(game.getAsJsonArray("tableCards")));
        int deck = game.get("deckCount").getAsInt();
        deckRow.setBacks(Math.min(deck, 5));
        deckLabel.setText("덱 " + deck + "장");
        handRow.setCards(toIds(game.getAsJsonArray("myHand")));

        // 선택 상태
        String kind = pendingChoice == null ? null : pendingChoice.get("kind").getAsString();
        boolean capture = "CAPTURE".equals(kind) && !waitingResponse;
        boolean goStop = "GO_STOP".equals(kind) && !waitingResponse;
        boolean canPlay = myTurn && "WAIT_PLAY".equals(phase) && pendingChoice == null && !waitingResponse;

        handRow.setClickable(canPlay);
        handRow.setHighlight(null);
        if (capture) {
            Set<Integer> cand = new HashSet<>(toIds(pendingChoice.getAsJsonArray("cardIds")));
            tableRow.setHighlight(cand);
            tableRow.setClickable(true);
        } else {
            tableRow.setHighlight(null);
            tableRow.setClickable(false);
        }
        goButton.setVisible(goStop);
        stopButton.setVisible(goStop);
        if (goStop) {
            int myGo = 0;
            for (JsonElement el : game.getAsJsonArray("players")) {
                JsonObject pl = el.getAsJsonObject();
                if (pl.get("playerId").getAsString().equals(myPlayerId)) myGo = pl.get("goCount").getAsInt();
            }
            goButton.setText((myGo + 1) + "고");
        }

        // 안내 문구
        String text;
        if ("FINISHED".equals(phase)) text = "게임이 끝났습니다.";
        else if (waitingResponse) text = "서버 응답을 기다리는 중...";
        else if (capture) text = "먹을 바닥패를 고르세요.";
        else if (goStop) text = "고 또는 스톱을 선택하세요.";
        else if (canPlay) text = "내 차례입니다. 낼 패를 고르세요.";
        else if (myTurn) text = "내 차례 진행 중...";
        else text = (current == null ? "상대" : name(current)) + " 님의 차례입니다.";
        turnLabel.setText(text);
        turnLabel.setForeground(canPlay || capture || goStop ? Theme.GOLD : Color.WHITE);
    }

    private static List<Integer> toIds(JsonArray arr) {
        List<Integer> ids = new ArrayList<>();
        if (arr != null) for (JsonElement e : arr) ids.add(e.getAsInt());
        return ids;
    }

    // ============================================================ 사용자 동작

    private void onHandClick(int cardId) {
        if (waitingResponse) return;
        waitingResponse = true;
        actions.playCard(cardId);
        refreshGame();
    }

    private void onTableClick(int cardId) {
        if (pendingChoice == null || waitingResponse) return;
        String id = pendingChoice.get("choiceId").getAsString();
        handledChoiceId = id;
        waitingResponse = true;
        actions.selectCapture(id, cardId);
        refreshGame();
    }

    private void onDecide(String decision) {
        if (pendingChoice == null || waitingResponse) return;
        String id = pendingChoice.get("choiceId").getAsString();
        handledChoiceId = id;
        waitingResponse = true;
        actions.decide(id, decision);
        refreshGame();
    }

    private void onLeave() {
        if ("PLAYING".equals(roomStatus)) {
            int r = JOptionPane.showConfirmDialog(this, "게임 중에 나가면 이번 판이 종료됩니다. 나갈까요?",
                    "방 나가기", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (r != JOptionPane.YES_OPTION) return;
        }
        actions.leave();
    }

    // ============================================================ 내부 컴포넌트

    /** 플레이어 한 명의 영역: 이름·점수 줄 + (상대) 뒷면 손패 + 종류별 획득패 */
    private static class PlayerArea extends JPanel {
        private final boolean me;
        private final JLabel nameLabel = Theme.label(" ", Font.BOLD, 15, Color.WHITE);
        private final JLabel infoLabel = Theme.label(" ", Font.PLAIN, 13, new Color(0xD9, 0xE8, 0xDE));
        private final JLabel turnBadge = Theme.label(" 차례 ", Font.BOLD, 12, Theme.INK);
        private final CardRow backs = new CardRow(40, 22);
        private final Map<String, CardRow> groups = new LinkedHashMap<>();
        private final Map<String, JLabel> groupLabels = new LinkedHashMap<>();

        PlayerArea(boolean me) {
            this.me = me;
            setOpaque(false);
            setLayout(new BorderLayout(14, 4));

            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
            top.setOpaque(false);
            turnBadge.setOpaque(true);
            turnBadge.setBackground(Theme.GOLD);
            top.add(nameLabel);
            top.add(turnBadge);
            top.add(infoLabel);
            add(top, BorderLayout.NORTH);

            if (!me) add(backs, BorderLayout.WEST);

            JPanel cap = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
            cap.setOpaque(false);
            for (String t : GROUP_TYPES) {
                JPanel g = new JPanel(new BorderLayout(0, 2));
                g.setOpaque(false);
                JLabel l = Theme.label(CardInfo.typeName(t) + " 0", Font.BOLD, 12, new Color(0xBF, 0xD9, 0xC7));
                CardRow row = new CardRow(34, 11);
                g.add(l, BorderLayout.NORTH);
                g.add(row, BorderLayout.CENTER);
                groups.put(t, row);
                groupLabels.put(t, l);
                cap.add(g);
            }
            add(cap, BorderLayout.CENTER);
        }

        void update(String nickname, int handCount, List<Integer> captured, int score, int goCount, boolean turn) {
            nameLabel.setText(nickname + (me ? " (나)" : ""));
            infoLabel.setText("손패 " + handCount + "장  ·  점수 " + score + "점  ·  " + goCount + "고");
            turnBadge.setVisible(turn);
            if (!me) backs.setBacks(handCount);

            Map<String, List<Integer>> byType = new LinkedHashMap<>();
            for (String t : GROUP_TYPES) byType.put(t, new ArrayList<>());
            CardCatalog cat = CardCatalog.get();
            for (int id : captured) {
                CardInfo info = cat.info(id);
                String t = info == null ? "PI" : info.getType();
                if ("BONUS".equals(t)) t = "PI";       // 보너스는 피 묶음에 함께 표시
                byType.get(t).add(id);
            }
            for (String t : GROUP_TYPES) {
                groups.get(t).setCards(byType.get(t));
                groupLabels.get(t).setText(CardInfo.typeName(t) + " " + byType.get(t).size());
            }
        }
    }

    /** 초록 담요 배경 */
    private static class FeltPanel extends JPanel {
        FeltPanel(java.awt.LayoutManager lm) {
            super(lm);
            setOpaque(true);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0;
            g.setPaint(new GradientPaint(0, 0, Theme.FELT, 0, getHeight(), Theme.FELT_DARK));
            g.fillRect(0, 0, getWidth(), getHeight());
        }
    }
}
