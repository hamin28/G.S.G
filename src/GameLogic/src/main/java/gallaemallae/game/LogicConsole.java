package gallaemallae.game;

import java.util.*;
import gallaemallae.game.GameState.Seat;
import gallaemallae.game.MatchEngine.*;

/** UI·서버 없이 두 사람의 차례를 번갈아 직접 입력하는 로직 확인용 콘솔이다. */
public final class LogicConsole {
    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : System.nanoTime();
        MatchEngine engine = new MatchEngine(1,Seat.FIRST,DeckManager.shuffleAndDeal(new Random(seed)),
                1000000,1000000,0,0);
        Scanner input = new Scanner(System.in, "UTF-8");
        long logicalTime = 0;
        System.out.println("두 플레이어를 한 콘솔에서 조작합니다. seed=" + seed);
        System.out.println("이 실습은 생각하는 동안 기권되지 않도록 가상 시각을 사용합니다. 실제 30초 검사는 FullGameCheck에 있습니다.");
        while (engine.result() == null) {
            View v = engine.viewFor(engine.viewFor(Seat.FIRST).turn);
            System.out.println("\n" + v.turn + " / " + v.phase + " / " + v.ownScore + " / 기회=" + v.ownTokens);
            print("바닥",v.floor); print("내 손패",v.ownHand);
            print("내 획득",v.ownCaptures); print("상대 획득",v.opponentCaptures);
            if (!v.choices.isEmpty()) print("선택 후보",v.choices);
            String allowed = v.phase == Phase.PLAY ? "PLAY 카드ID, SERVICE 카드ID, SHAKE 카드ID, BOMB 카드ID, DECK_ONLY"
                    : v.phase == Phase.GUKJIN ? "GUKJIN_PI 또는 GUKJIN_ANIMAL"
                    : v.phase == Phase.GO_STOP ? "GO 또는 STOP" : "SELECT 카드ID";
            System.out.println("입력: " + allowed + " (QUIT: 실습 종료)");
            if (!input.hasNextLine()) break;
            String line = input.nextLine().trim();
            if (line.equalsIgnoreCase("QUIT")) break;
            try {
                String[] parts = line.split("\\s+");
                Action action = Action.valueOf(parts[0].toUpperCase(Locale.ROOT));
                int id = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                View result = engine.submit(v.turn,v.roundId,v.version,action,id,++logicalTime);
                for (String event : result.events) System.out.println("- " + event);
            } catch (IllegalArgumentException | IllegalStateException error) {
                System.out.println("거절: " + error.getMessage());
            }
        }
        Result result = engine.result();
        if (result != null) System.out.println("종료="+result.end+", 승자="+result.winner+", 잔액="
                +result.money(Seat.FIRST)+" / "+result.money(Seat.SECOND));
    }
    private static void print(String title,List<Card> cards) {
        StringBuilder line = new StringBuilder(title + ": ");
        for (Card c : cards) line.append(c.id()).append('[').append(c.isService() ? "서비스"+c.servicePiValue()
                : c.month()+"월 "+c.type()).append("] ");
        System.out.println(line);
    }
}
