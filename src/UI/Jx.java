package gsg.client.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

// [8-2] Gson의 JsonObject에서 값을 꺼낼 때 "없으면 어떡하지?"를 매번 쓰지 않으려고 만든 도우미 모음.
//       서버 문서에 필드 이름이 정확히 적혀 있지 않은 곳은 여러 이름을 시도하게 해 두었다. (TODO(확인) 표시)
public final class Jx {
    private Jx() {}

    // 서버 메시지의 본문. {"type":..,"payload":{...}} 이면 payload, 아니면 메시지 자체.
    // TODO(확인): 실제 서버가 이벤트도 payload 안에 담아 보내는지 콘솔 로그로 확인
    public static JsonObject body(JsonObject msg) {
        JsonElement p = msg.get("payload");
        if (p != null && p.isJsonObject()) return p.getAsJsonObject();
        return msg;
    }

    // 문자열 값 (없거나 null이면 null)
    public static String str(JsonObject o, String key) {
        if (o == null) return null;
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) return null;
        if (e.isJsonPrimitive()) return e.getAsString();
        return e.toString();
    }

    // 점수 값: 서버의 ownScore/opponentScore는 숫자가 아니라 {total, ...} 객체이므로 total을 꺼냄 (숫자면 그대로)
    public static int score(JsonObject o, String key) {
        if (o == null) return 0;
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) return 0;
        if (e.isJsonPrimitive()) {
            try { return e.getAsInt(); } catch (Exception ex) { return 0; }
        }
        if (e.isJsonObject()) return num(e.getAsJsonObject(), "total", 0);
        return 0;
    }

    // 정수 값 (없거나 숫자가 아니면 def)
    public static int num(JsonObject o, String key, int def) {
        if (o == null) return def;
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) return def;
        try {
            return e.getAsInt();
        } catch (Exception ex) {
            return def;
        }
    }

    public static long lng(JsonObject o, String key, long def) {
        if (o == null) return def;
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) return def;
        try {
            return e.getAsLong();
        } catch (Exception ex) {
            return def;
        }
    }

    public static boolean bool(JsonObject o, String key, boolean def) {
        if (o == null) return def;
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) return def;
        try {
            return e.getAsBoolean();
        } catch (Exception ex) {
            return def;
        }
    }

    // 배열 (없으면 빈 배열)
    public static JsonArray arr(JsonObject o, String key) {
        if (o == null) return new JsonArray();
        JsonElement e = o.get(key);
        if (e != null && e.isJsonArray()) return e.getAsJsonArray();
        return new JsonArray();
    }

    // 객체 (없으면 null)
    public static JsonObject obj(JsonObject o, String key) {
        if (o == null) return null;
        JsonElement e = o.get(key);
        if (e != null && e.isJsonObject()) return e.getAsJsonObject();
        return null;
    }

    // "장수"를 세는 도우미: 배열이면 길이, 객체면 안의 배열 길이의 합, 숫자면 그 값
    // TODO(확인): ownCaptures / opponentCaptures가 어떤 모양인지(배열 or 종류별 객체) 서버 로그로 확인
    public static int count(JsonElement e) {
        if (e == null || e.isJsonNull()) return 0;
        if (e.isJsonArray()) return e.getAsJsonArray().size();
        if (e.isJsonPrimitive()) {
            try {
                return e.getAsInt();
            } catch (Exception ex) {
                return 0;
            }
        }
        if (e.isJsonObject()) {
            int sum = 0;
            for (java.util.Map.Entry<String, JsonElement> en : e.getAsJsonObject().entrySet()) {
                sum += count(en.getValue());
            }
            return sum;
        }
        return 0;
    }

    // Card 객체(또는 숫자)에서 카드 ID를 꺼냄. 못 찾으면 -1
    // 문서: "항목의 id를 요청에 사용합니다" -> "id", cards.json 쪽 이름 "cardId"도 시도
    // TODO(확인): 서버 Card 객체의 실제 필드 이름
    public static int cardId(JsonElement e) {
        if (e == null || e.isJsonNull()) return -1;
        if (e.isJsonPrimitive()) {
            try {
                return e.getAsInt();
            } catch (Exception ex) {
                return -1;
            }
        }
        if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            int id = num(o, "id", Integer.MIN_VALUE);
            if (id != Integer.MIN_VALUE) return id;
            id = num(o, "cardId", Integer.MIN_VALUE);
            if (id != Integer.MIN_VALUE) return id;
        }
        return -1;
    }
}
