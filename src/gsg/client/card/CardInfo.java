package gsg.client.card;

/**
 * data/cards.json 항목 하나 (docs/card-resources.md "데이터 필드").
 * 서버와 같은 cardId로 카드를 찾는다. 월·종류를 ID 값에서 계산하지 않는다.
 */
public class CardInfo {

    private int cardId;
    private Integer month;        // 보너스는 null
    private int variant;
    private String type;          // GWANG, ANIMAL, RIBBON, PI, BONUS
    private String imagePath;     // 저장소 루트 기준 상대 경로
    private Integer printedValue; // 보너스에만 존재

    public int getCardId()          { return cardId; }
    public Integer getMonth()       { return month; }
    public int getVariant()         { return variant; }
    public String getType()         { return type; }
    public String getImagePath()    { return imagePath; }
    public Integer getPrintedValue(){ return printedValue; }

    /** 화면 표시용 한글 이름. 예: "3월 광", "보너스(2)" */
    public String displayName() {
        if ("BONUS".equals(type)) return "보너스(" + printedValue + ")";
        return month + "월 " + typeName(type);
    }

    public static String typeName(String type) {
        switch (type) {
            case "GWANG":  return "광";
            case "ANIMAL": return "열끗";
            case "RIBBON": return "띠";
            case "PI":     return "피";
            case "BONUS":  return "보너스";
            default:       return type;
        }
    }
}
