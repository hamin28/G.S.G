package server.protocol;

import com.google.gson.JsonObject;

/** 한 줄 JSON 메시지. 서버 이벤트에서는 requestId를 생략한다. */
public record Message(String type, String requestId, JsonObject payload) {
    public static Message event(String type, JsonObject payload) {
        return new Message(type, null, payload);
    }
}
