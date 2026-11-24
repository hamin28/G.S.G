package server;

import com.google.gson.*;
import gsg.client.ui.CardCatalog;
import gsg.client.ui.CardView;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/** GitHub 서버 소스 그대로 실제 TCP를 사용한다. 검사에만 가상 시각을 주입한다. */
public final class RepositoryIntegrationCheck {
    private static int assertions;
    public static void check(boolean condition,String message) {
        assertions++;
        if(!condition) throw new AssertionError(message);
    }

    public static final class TestServer implements AutoCloseable {
        public final AtomicLong now = new AtomicLong(1000);
        public final RoomManager manager;
        public final ServerSocket socket;
        public TestServer(long seed) throws IOException {
            manager=new RoomManager(now::get,new Random(seed),1000000);
            socket=new ServerSocket(0,50,InetAddress.getLoopbackAddress());
            Thread accept=new Thread(() -> {
                while(!socket.isClosed()) {
                    try { new GameUser(socket.accept(),manager).start(); }
                    catch(IOException error) { if(!socket.isClosed()) error.printStackTrace(); }
                }
            },"repository-test-accept");
            accept.setDaemon(true); accept.start();
        }
        public int port() { return socket.getLocalPort(); }
        @Override public void close() throws IOException { socket.close(); manager.close(); }
    }

    public static final class Peer implements AutoCloseable {
        final Socket socket;
        final BufferedWriter writer;
        final BlockingQueue<JsonObject> queue=new LinkedBlockingQueue<>();
        public final List<JsonObject> received=new CopyOnWriteArrayList<>();
        public JsonObject room,selection,game,over,left;
        public String playerId;
        private int sequence;
        public Peer(int port,String name) throws Exception {
            socket=new Socket(InetAddress.getLoopbackAddress(),port);
            writer=new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.UTF_8));
            Thread reader=new Thread(() -> {
                try {
                    BufferedReader in=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
                    String line;
                    while((line=in.readLine())!=null) queue.add(JsonParser.parseString(line).getAsJsonObject());
                } catch(IOException ignored) { }
            },"test-peer-reader");
            reader.setDaemon(true); reader.start();
            JsonObject joined=request("JOIN",obj("nickname",name));
            check("ACK".equals(joined.get("type").getAsString()),"JOIN ACK");
            playerId=joined.getAsJsonObject("payload").get("playerId").getAsString();
        }
        public JsonObject request(String type,JsonObject payload) throws Exception {
            String id="test-"+(++sequence);
            JsonObject request=obj("type",type,"requestId",id);
            request.add("payload",payload);
            writer.write(request.toString()); writer.write('\n'); writer.flush();
            for(;;) {
                JsonObject message=next();
                if(message.has("requestId") && id.equals(message.get("requestId").getAsString())) return message;
            }
        }
        JsonObject next() throws Exception {
            JsonObject msg=queue.poll(8,TimeUnit.SECONDS);
            if(msg==null) throw new AssertionError("TCP 응답 지연: "+playerId);
            consume(msg); return msg;
        }
        void consume(JsonObject msg) {
            received.add(msg);
            JsonObject b=msg.getAsJsonObject("payload");
            switch(msg.get("type").getAsString()) {
                case "ROOM_STATE" -> room=b;
                case "DEALER_SELECTION" -> selection=b;
                case "GAME_STATE","GAME_START" -> game=b;
                case "GAME_OVER" -> over=b;
                case "ROOM_LEFT" -> left=b;
            }
        }
        public void await(BooleanSupplier condition) throws Exception {
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
            while(!condition.getAsBoolean()) {
                if(System.nanoTime()>until) throw new AssertionError("기대 상태 대기 실패");
                next();
            }
        }
        public JsonObject action(String action,Integer card) throws Exception {
            JsonObject payload=game.deepCopy(); // 실제로는 식별자·버전·행동만 보낸다.
            JsonObject minimal=obj("gameId",payload.get("gameId").getAsString(),
                    "roundNumber",payload.get("roundNumber").getAsLong(),"version",payload.get("version").getAsLong(),"action",action);
            if(card!=null) minimal.addProperty("cardId",card);
            return request("GAME_ACTION",minimal);
        }
        @Override public void close() throws IOException { socket.close(); }
    }

    public static JsonObject obj(Object... pairs) {
        JsonObject value=new JsonObject();
        for(int i=0;i<pairs.length;i+=2) {
            String key=(String)pairs[i]; Object v=pairs[i+1];
            if(v instanceof Number n) value.addProperty(key,n);
            else if(v instanceof Boolean b) value.addProperty(key,b);
            else value.addProperty(key,(String)v);
        }
        return value;
    }
    private static long round(JsonObject v) { return v==null ? -1 : v.get("roundNumber").getAsLong(); }
    private static long version(Peer p) { return p.game.get("version").getAsLong(); }
    private static boolean ended(Peer p,long round) { return round(p.over)==round; }
    private static void ack(JsonObject response) { check(response.get("type").getAsString().equals("ACK"),"ACK가 아님: "+response); }

    private static void start(Peer a,Peer b,long expected) throws Exception {
        ack(a.request("READY",obj("ready",true)));
        ack(b.request("READY",obj("ready",true)));
        for(Peer p:List.of(a,b)) p.await(() -> (p.room!=null && "DEALER_SELECTION".equals(p.room.get("status").getAsString()))
                || round(p.game)==expected || ended(p,expected));
        if(round(a.game)!=expected && !ended(a,expected)) {
            for(int tries=0;tries<30;tries++) {
                a.await(() -> a.selection!=null && !a.selection.getAsJsonArray("choices").isEmpty());
                b.await(() -> b.selection!=null && !b.selection.getAsJsonArray("choices").isEmpty());
                JsonObject s=a.selection;
                int draw=s.get("drawNumber").getAsInt();
                JsonArray choices=s.getAsJsonArray("choices");
                int first=choices.get(0).getAsJsonObject().get("id").getAsInt();
                int second=choices.get(choices.size()-1).getAsJsonObject().get("id").getAsInt();
                ack(a.request("SELECT_DEALER",obj("selectionId",s.get("selectionId").getAsString(),"drawNumber",draw,"cardId",first)));
                JsonObject result=b.request("SELECT_DEALER",obj("selectionId",s.get("selectionId").getAsString(),"drawNumber",draw,"cardId",second));
                ack(result);
                String outcome=result.getAsJsonObject("payload").get("choiceResult").getAsString();
                if(outcome.equals("DECIDED")) break;
                check(outcome.equals("REDRAW"),"예상 선결정 결과");
                for(Peer p:List.of(a,b)) p.await(() -> p.selection!=null && p.selection.get("drawNumber").getAsInt()>draw);
            }
        }
        for(Peer p:List.of(a,b)) p.await(() -> round(p.game)==expected || ended(p,expected));
    }

    public static void main(String[] args) throws Exception {
        assets();
        EnumMap<gallaemallae.game.MatchEngine.End,Integer> endings=new EnumMap<>(gallaemallae.game.MatchEngine.End.class);
        Set<String> actions=new TreeSet<>(), phases=new TreeSet<>();
        int completed=0;
        try(TestServer server=new TestServer(118)) {
            Random random=new Random(77);
            for(int pair=0;pair<40;pair++) {
                try(Peer a=new Peer(server.port(),"검사A"+pair);Peer b=new Peer(server.port(),"검사B"+pair)) {
                    JsonObject made=a.request("CREATE_ROOM",obj("title","원본 통신 검사")); ack(made);
                    String roomId=made.getAsJsonObject("payload").get("roomId").getAsString();
                    ack(b.request("JOIN_ROOM",obj("roomId",roomId)));
                    for(Peer p:List.of(a,b)) p.await(() -> p.room!=null && p.room.getAsJsonArray("players").size()==2);
                    for(int r=1;r<=2;r++) {
                        final int currentRound=r;
                        start(a,b,r);
                        int steps=0;
                        while(!ended(a,r)) {
                            if(a.game!=null && a.game.get("phase").getAsString().equals("FINISHED")) {
                                a.await(() -> ended(a,currentRound));
                                b.await(() -> ended(b,currentRound));
                                break;
                            }
                            check(++steps<250,"한 판 진행 정체");
                            Peer actor=a.game.get("currentPlayerId").getAsString().equals(a.playerId)?a:b;
                            Peer other=actor==a?b:a;
                            JsonObject state=actor.game;
                            String phase=state.get("phase").getAsString(); phases.add(phase);
                            check(!state.has("opponentHand") && !state.has("stock") && !state.has("players"),"비공개 내부 정보 누출");
                            check(other.game.getAsJsonArray("choices").isEmpty(),"상대 선택 후보 누출");
                            check(state.get("floor").equals(other.game.get("floor")),"양쪽 바닥 상태 다름");
                            check(state.get("ownCaptures").equals(other.game.get("opponentCaptures")),"양쪽 획득패 다름");
                            if(steps==1) {
                                JsonObject wrong=other.action("PLAY",0);
                                check("NOT_YOUR_TURN".equals(wrong.getAsJsonObject("payload").get("code").getAsString()),"다른 차례 거절");
                            }
                            String action; Integer card=null;
                            switch(phase) {
                                case "HAND_CHOICE","STOCK_CHOICE" -> { action="SELECT"; card=state.getAsJsonArray("choices").get(0).getAsJsonObject().get("id").getAsInt(); }
                                case "GUKJIN" -> action="GUKJIN_PI";
                                case "GO_STOP" -> action=random.nextInt(4)==0 && state.get("ownGo").getAsInt()<2 ? "GO":"STOP";
                                case "PLAY" -> {
                                    JsonArray hand=state.getAsJsonArray("ownHand");
                                    if(state.get("ownTokens").getAsInt()>0 && (hand.isEmpty() || random.nextBoolean())) action="DECK_ONLY";
                                    else {
                                        JsonObject c=hand.get(random.nextInt(hand.size())).getAsJsonObject(); card=c.get("id").getAsInt();
                                        action=c.get("type").getAsString().equals("SERVICE")?"SERVICE":"PLAY";
                                        if(!action.equals("SERVICE")) {
                                            int month=c.get("month").getAsInt(), hc=0,fc=0;
                                            for(JsonElement e:hand) if(e.getAsJsonObject().get("month").getAsInt()==month) hc++;
                                            for(JsonElement e:state.getAsJsonArray("floor")) if(e.getAsJsonObject().get("month").getAsInt()==month) fc++;
                                            if(hc==3 && fc==1) action="BOMB";
                                            else if(hc==3 && fc==0 && !state.getAsJsonArray("ownShakenMonths").contains(new JsonPrimitive(month))) action="SHAKE";
                                        }
                                    }
                                }
                                default -> throw new AssertionError("상태: "+phase);
                            }
                            actions.add(action);
                            long oldVersion=version(actor);
                            ack(actor.action(action,card));
                            for(Peer p:List.of(a,b)) p.await(() -> ended(p,currentRound) || (round(p.game)==currentRound && version(p)>oldVersion));
                            if(ended(b,r) && !ended(a,r)) a.await(() -> ended(a,currentRound));
                        }
                        b.await(() -> ended(b,currentRound));
                        check(a.over.equals(b.over),"양쪽 정산 결과 다름");
                        long total=0;
                        for(JsonElement e:a.over.getAsJsonArray("results")) total+=e.getAsJsonObject().get("balance").getAsLong();
                        check(total==2000000,"정산 잔액 합계 보존");
                        String end=a.over.get("reason").getAsString();
                        endings.merge(gallaemallae.game.MatchEngine.End.valueOf(end),1,Integer::sum); completed++;
                        if(a.over.getAsJsonArray("results").asList().stream().anyMatch(e -> e.getAsJsonObject().get("bankrupt").getAsBoolean())) break;
                    }
                }
            }
            deadlines(server);
        }
        check(endings.getOrDefault(gallaemallae.game.MatchEngine.End.NORMAL,0)>0,"정상 정산 완주 필요");
        check(actions.containsAll(List.of("PLAY","SERVICE","SELECT","STOP")),"주요 행동 전송 필요");
        System.out.println("PASS: 원본 서버 실제 TCP "+completed+"판 종료 "+endings);
        System.out.println("PASS: 전송한 행동 "+actions+", 도달 상태 "+phases);
        System.out.println("PASS: 개인별 패·상태 일치·정산·다음 판·잘못된 차례·시간 초과 ("+assertions+" checks)");
    }

    private static void deadlines(TestServer server) throws Exception {
        server.manager.startTimer();
        try(Peer a=new Peer(server.port(),"시간검사A");Peer b=new Peer(server.port(),"시간검사B")) {
            JsonObject made=a.request("CREATE_ROOM",obj("title","시간 경계"));
            String roomId=made.getAsJsonObject("payload").get("roomId").getAsString();
            ack(b.request("JOIN_ROOM",obj("roomId",roomId)));
            ack(a.request("READY",obj("ready",true))); ack(b.request("READY",obj("ready",true)));
            a.await(() -> a.selection!=null);
            server.now.addAndGet(30000);
            a.await(() -> a.left!=null);
            check(a.left.get("balance").getAsLong()==1000000,"분배 전 시간 초과 무료");
        }
        try(Peer a=new Peer(server.port(),"기권검사A");Peer b=new Peer(server.port(),"기권검사B")) {
            JsonObject made=a.request("CREATE_ROOM",obj("title","기권 경계"));
            ack(b.request("JOIN_ROOM",obj("roomId",made.getAsJsonObject("payload").get("roomId").getAsString())));
            for(Peer p:List.of(a,b)) p.await(() -> p.room!=null && p.room.getAsJsonArray("players").size()==2);
            long round=1;
            start(a,b,round);
            while(ended(a,round)) { round++; start(a,b,round); }
            server.now.addAndGet(30000);
            final long finalRound=round;
            a.await(() -> ended(a,finalRound)); b.await(() -> ended(b,finalRound));
            check(a.over.get("reason").getAsString().equals("FORFEIT"),"분배 후 시간 초과 기권");
            check(a.over.getAsJsonObject("settlement").get("paidAmount").getAsLong()==100000,"기권 정산10만원");
        }
        System.out.println("PASS: 원본 서버 100ms 예약 작업 + 가상 시각 30초 전진으로 무료 퇴장/기권 정산");
    }

    private static void assets() throws Exception {
        check(CardView.load(CardCatalog.BACK)!=null,"뒷면 이미지 없음");
        for(int id=1;id<=50;id++) check(CardView.load(CardCatalog.pathOf(id))!=null,"카드 이미지 없음: "+id);
        JsonArray catalog=JsonParser.parseString(Files.readString(Path.of("data/cards.json"))).getAsJsonArray();
        for(JsonElement element:catalog) {
            JsonObject c=element.getAsJsonObject(); int id=c.get("cardId").getAsInt();
            if(id<=50) check(CardCatalog.pathOf(id).endsWith(c.get("imagePath").getAsString()),"이미지 ID 대응 오류");
        }
        System.out.println("PASS: 저장소 카드 50장과 뒷면 이미지 로딩·ID 대응");
    }
}
