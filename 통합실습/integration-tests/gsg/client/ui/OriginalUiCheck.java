package gsg.client.ui;

import com.google.gson.*;
import server.RepositoryIntegrationCheck.TestServer;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.swing.*;

/** 원본 MainFrame/LobbyPanel/GamePanel을 그대로 생성해 실제 버튼·카드와 TCP를 검사한다. */
public final class OriginalUiCheck {
    private static final List<Ui> opened=new CopyOnWriteArrayList<>();
    private static final Set<Window> recorded=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final List<String> messages=new CopyOnWriteArrayList<>();
    private static int moves, selections, normalEnds;
    private static boolean serviceRejection,scoreMismatch;

    private static Object field(Object object,String name) {
        try { Field f=object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object); }
        catch(ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    private static void edt(Runnable action) throws Exception {
        if(SwingUtilities.isEventDispatchThread()) action.run(); else SwingUtilities.invokeAndWait(action);
    }
    private static void check(boolean valid,String message) { if(!valid) throw new AssertionError(message); }
    private static void await(BooleanSupplier condition,String message) throws Exception {
        long until=System.nanoTime()+10_000_000_000L;
        while(!condition.getAsBoolean()) {
            if(System.nanoTime()>until) throw new AssertionError(message);
            Thread.sleep(10);
        }
        edt(() -> {});
    }
    private static List<Component> components(Component root) {
        List<Component> result=new ArrayList<>(); result.add(root);
        if(root instanceof Container c) for(Component child:c.getComponents()) result.addAll(components(child));
        return result;
    }
    private static JButton button(Component root,String text) {
        for(Component c:components(root)) if(c instanceof JButton b && text.equals(b.getText())) return b;
        return null;
    }
    private static void click(Component root,String text) throws Exception {
        edt(() -> {
            JButton b=button(root,text); check(b!=null && b.isEnabled(),"버튼 사용 불가: "+text);
            b.doClick();
        });
    }
    private static void clickCard(CardView card) {
        card.dispatchEvent(new MouseEvent(card,MouseEvent.MOUSE_CLICKED,System.currentTimeMillis(),0,20,20,1,false));
    }

    private static final class Ui implements AutoCloseable {
        MainFrame frame; GameClient client; Session session; GamePanel panel; FeltPanel felt;
        volatile JsonObject state,over;
        volatile int enteredGames;
        final int index;
        @SuppressWarnings("unchecked") Ui(int port,String name,int index) throws Exception {
            this.index=index;
            edt(() -> {
                frame=new MainFrame("127.0.0.1",port,name);
                client=(GameClient)field(frame,"client"); session=(Session)field(frame,"session");
                panel=(GamePanel)field(frame,"game"); felt=(FeltPanel)field(panel,"felt");
                Consumer<JsonObject> original=(Consumer<JsonObject>)field(client,"onEvent");
                client.setOnEvent(msg -> {
                    String type=Jx.str(msg,"type"); JsonObject b=Jx.body(msg);
                    if("GAME_START".equals(type) || "GAME_STATE".equals(type)) {
                        state=b;
                        if("GAME_START".equals(type)) enteredGames++;
                    }
                    if("GAME_OVER".equals(type)) { over=b; state=null; }
                    original.accept(msg);
                });
                client.setOnClosed(() -> {}); // 검사 종료가 JVM 전체를 종료하지 않게 콜백만 바꾼다.
                frame.setLocation(40+index*80,40+index*50);
                frame.setVisible(true);
                opened.add(this);
            });
            await(() -> session.playerId!=null,"JOIN 응답 대기");
        }
        String id() { return session.playerId; }
        boolean inRoom() { return session.roomId!=null; }
        @Override public void close() throws Exception {
            edt(() -> {
                client.setOnClosed(() -> {}); client.close();
                for(Window w:Window.getWindows()) if(w.getOwner()==frame) w.dispose();
                frame.dispose(); opened.remove(this);
            });
        }
    }

    /** 검사 JVM의 원본 대화상자만 실제 카드·옵션 버튼으로 선택한다. 별도 UI를 만들지 않는다. */
    private static void handleDialogs() {
        for(Window window:Window.getWindows()) {
            if(!(window instanceof JDialog dialog) || !window.isShowing()) continue;
            Ui owner=null;
            for(Ui candidate:opened) if(dialog.getOwner()==candidate.frame) owner=candidate;
            if(owner==null) continue;
            String title=dialog.getTitle();
            if("선 정하기".equals(title) || "바닥패 선택".equals(title)) {
                List<CardView> cards=new ArrayList<>();
                for(Component c:components(dialog)) if(c instanceof CardView card) cards.add(card);
                if(!cards.isEmpty()) {
                    selections++;
                    clickCard(cards.get(owner.index==0 ? 0 : cards.size()-1));
                }
                continue;
            }
            JButton option=button(dialog,"스톱");
            if(option==null) option=button(dialog,"피로 사용");
            if(option!=null) { selections++; option.doClick(); continue; }
            for(Component c:components(dialog)) if(c instanceof JOptionPane pane) {
                if(recorded.add(dialog)) messages.add(title+": "+pane.getMessage());
                if("방 만들기".equals(title)) pane.setInputValue("GitHub 원본 UI 검사");
                pane.setValue(JOptionPane.OK_OPTION);
                break;
            }
        }
    }

    private static void save(Ui ui,String name) throws Exception {
        edt(() -> {
            try {
                BufferedImage image=new BufferedImage(ui.frame.getWidth(),ui.frame.getHeight(),BufferedImage.TYPE_INT_RGB);
                Graphics2D g=image.createGraphics(); ui.frame.paintAll(g); g.dispose();
                Path file=Path.of("target/ui-check",name+".png"); Files.createDirectories(file.getParent());
                ImageIO.write(image,"png",file.toFile());
            } catch(Exception e) { throw new IllegalStateException(e); }
        });
    }

    public static void main(String[] args) throws Exception {
        javax.swing.Timer dialogs=new javax.swing.Timer(50,e -> handleDialogs());
        edt(dialogs::start);
        TestServer seeded=args.length==0 ? new TestServer(5) : null;
        int port=seeded==null ? Integer.parseInt(args[0]) : seeded.port();
        try(AutoCloseable server=seeded==null ? () -> {} : seeded) {
            for(int attempt=0;attempt<4;attempt++) {
                try(Ui a=new Ui(port,"화면A"+attempt,0); Ui b=new Ui(port,"화면B"+attempt,1)) {
                    save(a,"lobby");
                    click(a.frame,"방 만들기"); await(a::inRoom,"방 생성 후 입장");
                    click(b.frame,"새로고침");
                    await(() -> button(b.frame,"입장")!=null && button(b.frame,"입장").isEnabled(),"방 목록 입장 버튼");
                    click(b.frame,"입장"); await(b::inRoom,"상대 입장");
                    await(() -> button(a.frame,"준비").isEnabled() && button(b.frame,"준비").isEnabled(),"준비 버튼 활성화");
                    click(a.frame,"준비"); click(b.frame,"준비");
                    await(() -> a.state!=null || a.over!=null,"원본 선결정 창 후 게임 시작");
                    await(() -> b.state!=null || b.over!=null,"상대 게임 시작");
                    for(int step=0;step<100 && a.over==null;step++) {
                        await(() -> a.over!=null || (a.state!=null && b.state!=null
                                && Jx.lng(a.state,"version",-1)==Jx.lng(b.state,"version",-2)),"양쪽 상태 버전 동기화");
                        if(a.over!=null) break;
                        JsonObject state=a.state;
                        Ui actor=Objects.equals(Jx.str(state,"currentPlayerId"),a.id())?a:b;
                        if(!"PLAY".equals(Jx.str(actor.state,"phase"))) {
                            long version=Jx.lng(actor.state,"version",-1);
                            await(() -> a.over!=null || Jx.lng(actor.state,"version",-1)>version,"원본 선택창 응답");
                            continue;
                        }
                        AtomicReference<JsonObject> clicked=new AtomicReference<>();
                        AtomicReference<Boolean> wasService=new AtomicReference<>(false);
                        edt(() -> {
                            JsonObject live=(JsonObject)field(actor.panel,"lastState");
                            if(live==null || !actor.id().equals(Jx.str(live,"currentPlayerId")) || !"PLAY".equals(Jx.str(live,"phase"))) return;
                            JsonArray hand=Jx.arr(live,"ownHand");
                            int choice=-1;
                            if(!serviceRejection) {
                                for(int i=0;i<hand.size();i++) if(Jx.num(hand.get(i).getAsJsonObject(),"id",0)>=49) { choice=i; wasService.set(true); break; }
                            }
                            if(choice<0) for(int i=0;i<hand.size();i++) if(Jx.num(hand.get(i).getAsJsonObject(),"id",0)<49) { choice=i; break; }
                            if(choice<0) return;
                            clicked.set(live.deepCopy());
                            JPanel handPanel=(JPanel)field(actor.felt,"hand");
                            clickCard((CardView)handPanel.getComponent(choice));
                        });
                        JsonObject old=clicked.get();
                        if(old==null) break; // 원본 화면에는 손패 없이 DECK_ONLY를 보내는 조작이 없다.
                        if(wasService.get()) {
                            await(() -> messages.stream().anyMatch(m -> m.contains("서비스패는 SERVICE로 사용")),"원본 서비스패 PLAY 거절 확인");
                            check(Jx.lng(actor.state,"version",-1)==Jx.lng(old,"version",-2),"거절 시 상태 보존");
                            serviceRejection=true;
                            System.out.println("KNOWN LIMIT: 원본 UI 서비스패 클릭은 PLAY를 전송해 서버가 거절함");
                        } else {
                            long version=Jx.lng(old,"version",-1);
                            await(() -> a.over!=null || Jx.lng(actor.state,"version",-1)>version,"원본 카드 제출 응답");
                            moves++;
                        }
                        if(a.state!=null && b.state!=null) {
                            JsonObject score=Jx.obj(actor.state,"ownScore");
                            if(score!=null && Jx.num(score,"total",0)>0 && Jx.num(actor.state,"ownScore",0)==0) scoreMismatch=true;
                        }
                        if(moves>=2 && moves<=4) { save(a,"game-A"); save(b,"game-B"); }
                    }
                    if(a.over!=null) {
                        await(() -> b.over!=null,"양쪽 결과 수신");
                        check(a.over.equals(b.over),"양쪽 UI 정산 결과 일치");
                        if("NORMAL".equals(Jx.str(a.over,"reason"))) normalEnds++;
                    } else {
                        click(a.frame,"나가기");
                        await(() -> !a.inRoom() && b.over!=null,"원본 퇴장 버튼과 상대 결과");
                    }
                    save(a,"after-A"); save(b,"after-B");
                    if(normalEnds>0 && serviceRejection && scoreMismatch) break;
                }
            }
        } finally { edt(dialogs::stop); }
        check(moves>0 && selections>0,"원본 카드와 선택 입력 검사 필요");
        System.out.println("PASS: 원본 MainFrame 로비·방 생성/입장·준비·선결정·카드 제출/선택 (카드 "+moves+"회, 선택 "+selections+"회)");
        System.out.println("PASS: 원본 화면에서 정상 종료/정산 "+normalEnds+"판 (부족한 행동은 수정하지 않음)");
        System.out.println("KNOWN LIMIT: 점수 객체를 숫자로 읽어 0점 표시="+scoreMismatch+", 서비스패 연결 누락 재현="+serviceRejection);
        System.out.println("PASS: 원본 화면 캡처 target/ui-check/*.png");
    }
}
