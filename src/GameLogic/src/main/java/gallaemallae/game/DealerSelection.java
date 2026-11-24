package gallaemallae.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import gallaemallae.game.GameState.Seat;

/**
 * 새 대결의 선 결정. 기본 화투에서 5장을 공개하고 양쪽 선택을 비교한다.
 * 방 담당 코드는 서버에 요청이 도착한 순서대로 choose를 호출한다.
 */
public final class DealerSelection {
    // 상대 선택 대기, 같은 패 충돌로 재선택, 동률로 재추첨, 선 확정이다.
    public enum ChoiceResult { WAITING, RESELECT, REDRAW, DECIDED }

    private final Random random;
    private final Map<Seat, Card> selected = new EnumMap<>(Seat.class);
    private final Set<Seat> reselecting = EnumSet.noneOf(Seat.class);
    private List<Card> offer;
    private int drawNumber;
    private Seat dealer;

    public DealerSelection(Random random) {
        this.random = Objects.requireNonNull(random);
        redraw();
    }

    public int drawNumber() { return drawNumber; }
    public List<Card> offer() { return offer; }
    public Optional<Seat> dealer() { return Optional.ofNullable(dealer); }

    /** 처음에는 5장을 보여 주고, 선택 충돌로 재선택하는 사람에게는 남은 4장을 보여 준다. */
    public List<Card> candidates(Seat seat) {
        Objects.requireNonNull(seat);
        if (selected.containsKey(seat)) {
            return Collections.emptyList();
        }
        if (!reselecting.contains(seat)) {
            return offer;
        }
        List<Card> remaining = new ArrayList<>(offer);
        remaining.removeAll(selected.values());
        return Collections.unmodifiableList(remaining);
    }

    /** 추첨 번호와 카드 ID를 검증하고 선택 결과를 반환한다. 제한 시간은 서버가 관리한다. */
    public ChoiceResult choose(Seat seat, int expectedDrawNumber, int cardId) {
        Objects.requireNonNull(seat);
        if (dealer != null || expectedDrawNumber != drawNumber || selected.containsKey(seat)) {
            throw new IllegalStateException("This selection is no longer open");
        }
        Card card = offer.stream().filter(candidate -> candidate.id() == cardId)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Card is not in the offer"));
        // 먼저 접수된 패의 소유권을 유지하고 나중에 고른 사람에게 재선택을 요청한다.
        if (selected.containsValue(card)) {
            if (!reselecting.add(seat)) {
                throw new IllegalArgumentException("Choose one of the four remaining cards");
            }
            return ChoiceResult.RESELECT;
        }
        selected.put(seat, card);
        if (selected.size() < 2) {
            return ChoiceResult.WAITING;
        }
        int comparison = compare(selected.get(Seat.FIRST), selected.get(Seat.SECOND));
        if (comparison == 0) {
            redraw();
            return ChoiceResult.REDRAW;
        }
        dealer = comparison > 0 ? Seat.FIRST : Seat.SECOND;
        return ChoiceResult.DECIDED;
    }

    /** 두 패의 순위가 같으면 새 5장을 뽑고 이전 선택 및 재선택 기록을 초기화한다. */
    private void redraw() {
        List<Card> cards = DeckManager.standardCards();
        Collections.shuffle(cards, random);
        offer = Collections.unmodifiableList(new ArrayList<>(cards.subList(0, 5)));
        selected.clear();
        reselecting.clear();
        drawNumber++;
    }

    /** 월을 먼저 비교하고 같은 월이면 광 → 열끗 → 띠 → 피 순위를 적용한다. */
    static int compare(Card first, Card second) {
        int month = Integer.compare(first.month(), second.month());
        return month != 0 ? month : Integer.compare(rank(first.type()), rank(second.type()));
    }

    /** 같은 월의 패를 비교하기 위해 광·열끗·띠·피에 우선순위 값을 부여한다. */
    private static int rank(CardType type) {
        switch (type) {
            case GWANG: return 4;
            case YEOLKKEUT: return 3;
            case TTI: return 2;
            case PI: return 1;
            default: throw new IllegalArgumentException("Service cards cannot decide the dealer");
        }
    }
}
