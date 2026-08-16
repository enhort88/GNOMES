package com.enhort.gnomes.menu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.enhort.gnomes.GnomesGame;
import com.enhort.gnomes.draw.Draw;
import com.enhort.gnomes.save.SaveRepository;

import java.util.Random;

public final class MenuScreen extends ScreenAdapter {
    private enum Mode { MAIN, SLOTS, SETTINGS, ABOUT, DELETE }
    private static final class Box {
        float l,t,r,b;
        Box(){} Box(float l,float t,float r,float b){set(l,t,r,b);}
        void set(float l,float t,float r,float b){this.l=l;this.t=t;this.r=r;this.b=b;}
        boolean hit(float x,float y){return x>=l&&x<=r&&y>=t&&y<=b;}
        float cx(){return(l+r)*.5f;} float cy(){return(t+b)*.5f;}
    }
    private static final class Dust {float x,y,s,v,p;Dust(float x,float y,float s,float v,float p){this.x=x;this.y=y;this.s=s;this.v=v;this.p=p;}}

    private final GnomesGame game;
    private final Random rnd=new Random(0x6E6F6D6573L);
    private final Dust[] dust=new Dust[34];
    private final Box[] main=new Box[6];
    private final Box[] slots=new Box[SaveRepository.SLOT_COUNT];
    private final Box back=new Box();
    private final Box yes=new Box(),no=new Box();
    private Mode mode=Mode.MAIN;
    private float width,height,ui,elapsed;
    private int pendingDelete=-1;

    public MenuScreen(GnomesGame game){
        this.game=game;
        for(int i=0;i<main.length;i++)main[i]=new Box();
        for(int i=0;i<slots.length;i++)slots[i]=new Box();
        for(int i=0;i<dust.length;i++)dust[i]=new Dust(rnd.nextFloat(),rnd.nextFloat(),.5f+rnd.nextFloat()*1.3f,2f+rnd.nextFloat()*8f,rnd.nextFloat()*6.28f);
    }

    @Override public void show(){
        Gdx.input.setCatchKey(Input.Keys.BACK,true);
        Gdx.input.setInputProcessor(new InputAdapter(){
            @Override public boolean keyDown(int keycode){
                if(keycode==Input.Keys.BACK||keycode==Input.Keys.ESCAPE){if(mode!=Mode.MAIN){mode=Mode.MAIN;pendingDelete=-1;}return true;}return false;
            }
            @Override public boolean touchDown(int x,int y,int pointer,int button){tap(x,y);return true;}
        });
    }

    @Override public void resize(int w,int h){width=w;height=h;ui=Math.max(.72f,w/420f);game.draw.resize(w,h);layout();}
    private void layout(){
        float bw=Math.min(width-64f*ui,292f*ui),x=(width-bw)/2,bh=44f*ui,g=9f*ui,y=height*.49f;
        for(int i=0;i<main.length;i++)main[i].set(x,y+i*(bh+g),x+bw,y+i*(bh+g)+bh);
        back.set(14f*ui,height-58f*ui,width-14f*ui,height-12f*ui);
        float sw=Math.min(width-42f*ui,330f*ui),sx=(width-sw)/2,sy=height*.27f,sh=64f*ui;
        for(int i=0;i<slots.length;i++)slots[i].set(sx,sy+i*(sh+8f*ui),sx+sw,sy+i*(sh+8f*ui)+sh);
        yes.set(width*.16f,height*.57f,width*.47f,height*.57f+50f*ui);no.set(width*.53f,height*.57f,width*.84f,height*.57f+50f*ui);
    }

    @Override public void render(float delta){elapsed+=Math.min(delta,.05f);Draw d=game.draw;d.beginFrame();background(d);switch(mode){case MAIN->main(d);case SLOTS->slots(d);case SETTINGS->settings(d);case ABOUT->about(d);case DELETE->delete(d);}d.endFrame();}

    private void background(Draw d){
        d.setColor(0xFF090A0B);d.fillRect(0,0,width,height);
        float tw=88f*ui;d.setColor(0xFF201D19);d.strokeWidth=tw*1.15f;paths(d);d.setColor(0xFF302A24);d.strokeWidth=tw*.82f;paths(d);
        for(Dust p:dust){float x=p.x*width+(float)Math.sin(elapsed*.18f+p.p)*12f*ui,y=(p.y*height+elapsed*p.v*ui)%height;d.setColor(0x18D9C8A8);d.fillCircle(x,y,p.s*ui);}
        torch(d,width*.17f,height*.31f,0);torch(d,width*.83f,height*.32f,2);torch(d,width*.44f,height*.69f,4);
        menuGnome(d,width*.18f+(float)Math.sin(elapsed*.55f)*width*.10f,height*.67f,elapsed*7f,0xFF69B9E7);
        menuGnome(d,width*.69f+(float)Math.sin(elapsed*.43f+2)*width*.09f,height*.30f,elapsed*7.7f+1,0xFFF0B85A);
        menuImp(d,width*.82f,height*.56f+(float)Math.sin(elapsed*1.8f)*7f*ui);
        d.setColor(0x77000000);d.fillRect(0,0,width,82f*ui);d.fillRect(0,height-82f*ui,width,height);
    }
    private void paths(Draw d){d.line(width*.17f,height*.15f,width*.17f,height*.86f);d.line(width*.17f,height*.32f,width*.83f,height*.32f);d.line(width*.83f,height*.32f,width*.83f,height*.78f);d.line(width*.17f,height*.69f,width*.66f,height*.69f);}
    private void torch(Draw d,float x,float y,int seed){float f=.82f+.18f*(float)Math.sin(elapsed*8.5f+seed);d.setColor(0x18FF9A30);d.fillCircle(x,y,28f*ui*f);d.setColor(0x2AFFB54C);d.fillCircle(x,y,12f*ui*f);d.setColor(0xFF6C4930);d.strokeWidth=2.4f*ui;d.line(x,y+7f*ui,x,y+19f*ui);d.setColor(0xFFFF902E);d.fillOval(x-4f*ui,y-8f*ui,x+4f*ui,y+4f*ui);d.setColor(0xFFFFD46A);d.fillOval(x-1.6f*ui,y-5f*ui,x+1.6f*ui,y+1f*ui);}
    private void menuGnome(Draw d,float x,float y,float p,int col){float s=18f*ui,stride=(float)Math.sin(p),bob=Math.abs((float)Math.cos(p))*1.5f*ui;d.save();d.translate(x,y-bob);d.setColor(0x55000000);d.fillOval(-s*.38f,s*.47f,s*.38f,s*.60f);d.setColor(0xFF3A2C25);d.strokeWidth=s*.11f;d.line(-s*.10f,s*.24f,-s*.18f+stride*s*.12f,s*.52f);d.line(s*.10f,s*.24f,s*.18f-stride*s*.12f,s*.52f);d.setColor(adjust(col,.68f));d.fillOval(-s*.28f,-s*.02f,s*.28f,s*.35f);d.setColor(0xFFE4B584);d.fillCircle(0,-s*.22f,s*.24f);d.setColor(0xFFE9E5DA);d.pathReset();d.moveTo(-s*.22f,-s*.12f);d.quadTo(-stride*s*.03f,s*.35f,s*.23f,-s*.12f);d.quadTo(0,s*.18f,-s*.22f,-s*.12f);d.closePath();d.fillPath();d.setColor(col);d.pathReset();d.moveTo(-s*.25f,-s*.38f);d.quadTo(0,-s*.78f,s*.20f,-s*.42f);d.lineTo(s*.30f,-s*.35f);d.lineTo(-s*.26f,-s*.34f);d.closePath();d.fillPath();d.setColor(0xFF171615);d.fillCircle(s*.13f,-s*.28f,s*.025f);d.restore();}
    private void menuImp(Draw d,float x,float y){float s=16f*ui,f=(float)Math.sin(elapsed*14f);d.save();d.translate(x,y);d.setColor(0xFFA53B34);d.pathReset();d.moveTo(-s*.18f,-s*.05f);d.lineTo(-s*(.55f+.10f*f),-s*.32f);d.lineTo(-s*.38f,s*.13f);d.closePath();d.fillPath();d.pathReset();d.moveTo(s*.18f,-s*.05f);d.lineTo(s*(.55f+.10f*f),-s*.32f);d.lineTo(s*.38f,s*.13f);d.closePath();d.fillPath();d.setColor(0xFFD34D3F);d.fillOval(-s*.28f,-s*.18f,s*.28f,s*.48f);d.fillCircle(0,-s*.30f,s*.27f);d.setColor(0xFFFFE45A);d.fillCircle(-s*.10f,-s*.34f,s*.04f);d.fillCircle(s*.10f,-s*.34f,s*.04f);d.restore();}

    private void heading(Draw d,String sub){d.align=Draw.Align.CENTER;d.bold=true;d.textSize=35f*ui;d.setColor(0xFFF2F1EC);d.text("GNOMES",width/2,92f*ui);d.textSize=9.5f*ui;d.setColor(0xFFD6A94A);d.text(sub,width/2,117f*ui);d.bold=false;d.align=Draw.Align.LEFT;}
    private void main(Draw d){heading(d,"DEEP MINE • ALPHA 0.2");button(d,main[0],"ИГРАТЬ",true);button(d,main[1],"ПРОДОЛЖИТЬ",game.saves.anySave());button(d,main[2],"СОХРАНЕНИЯ",true);button(d,main[3],"НАСТРОЙКИ",true);button(d,main[4],"ОБ ИГРЕ",true);button(d,main[5],"ВЫХОД",true);}
    private void slots(Draw d){
        heading(d,"ШАХТНЫЕ ЖУРНАЛЫ");d.align=Draw.Align.CENTER;d.textSize=8.5f*ui;d.setColor(0xFFAEB8BF);d.text("Пять независимых экспедиций",width/2,143f*ui);d.align=Draw.Align.LEFT;
        for(int i=0;i<slots.length;i++){int slot=i+1;SaveRepository.Snapshot s=game.saves.summary(slot);Box b=slots[i];d.setColor(s==null?0xCC1D2429:0xE5232C32);d.fillRoundRect(b.l,b.t,b.r,b.b,8f*ui);d.setColor(s==null?0xFF4B555C:0xFFD4A745);d.fillRect(b.l,b.t,b.l+3f*ui,b.b);d.bold=true;d.textSize=9.5f*ui;d.setColor(0xFFF1F3F4);d.text("ЯЧЕЙКА "+slot,b.l+14f*ui,b.t+21f*ui);d.bold=false;d.textSize=7.8f*ui;if(s==null){d.setColor(0xFF7D8991);d.text("пусто • начать новую шахту",b.l+14f*ui,b.t+43f*ui);}else{d.setColor(0xFFB8C1C7);d.text("глубина "+Math.max(1,s.depth)+" • камень "+fmt(s.stone)+" • ◆ "+fmt(s.diamond),b.l+14f*ui,b.t+42f*ui);d.setColor(0xFF8E999F);d.text("пород "+s.rocksBroken+" • врагов "+s.enemiesDefeated,b.l+14f*ui,b.t+57f*ui);d.setColor(0xFF8C4B45);d.fillRoundRect(b.r-40f*ui,b.t+12f*ui,b.r-8f*ui,b.b-12f*ui,5f*ui);d.align=Draw.Align.CENTER;d.bold=true;d.textSize=12f*ui;d.setColor(0xFFFFD6D1);d.text("×",b.r-24f*ui,b.cy()+4f*ui);d.align=Draw.Align.LEFT;d.bold=false;}}
        button(d,back,"НАЗАД",true);
    }
    private void settings(Draw d){heading(d,"НАСТРОЙКИ");d.align=Draw.Align.CENTER;d.textSize=10f*ui;d.setColor(0xFFCED4D8);d.text("Визуальные эффекты: ВЫСОКИЕ",width/2,height*.39f);d.text("Вибрация: будет подключена вместе со SFX",width/2,height*.39f+34f*ui);d.textSize=8f*ui;d.setColor(0xFF869199);d.text("Сначала доводим саму шахту, потом прикручиваем звук и тактильную отдачу.",width/2,height*.39f+72f*ui);d.align=Draw.Align.LEFT;button(d,back,"НАЗАД",true);}
    private void about(Draw d){heading(d,"ОБ ИГРЕ");d.align=Draw.Align.CENTER;d.bold=true;d.textSize=12f*ui;d.setColor(0xFFF0F2F3);d.text("Живая шахта, а не таблица с бородами.",width/2,height*.36f);d.bold=false;d.textSize=8.5f*ui;d.setColor(0xFFB6BFC5);d.text("туннели • добыча • эволюция • руны • обвалы • демоны",width/2,height*.36f+30f*ui);d.setColor(0xFFD4A745);d.text("Developer: Ponikarov Artem",width/2,height*.36f+75f*ui);d.align=Draw.Align.LEFT;button(d,back,"НАЗАД",true);}
    private void delete(Draw d){heading(d,"УДАЛИТЬ СОХРАНЕНИЕ?");d.align=Draw.Align.CENTER;d.bold=true;d.textSize=14f*ui;d.setColor(0xFFF1F2F2);d.text("Ячейка "+pendingDelete,width/2,height*.42f);d.bold=false;d.textSize=8.5f*ui;d.setColor(0xFFB2BBC1);d.text("Прогресс этой шахты будет удалён.",width/2,height*.42f+28f*ui);d.align=Draw.Align.LEFT;button(d,yes,"УДАЛИТЬ",true);button(d,no,"ОТМЕНА",true);}

    private void button(Draw d,Box b,String text,boolean enabled){d.setColor(enabled?0xE5242C32:0xBB171B1E);d.fillRoundRect(b.l,b.t,b.r,b.b,8f*ui);d.setColor(enabled?0xFF536570:0xFF262D31);d.strokeWidth=1f*ui;d.line(b.l+7f*ui,b.t,b.r-7f*ui,b.t);d.align=Draw.Align.CENTER;d.bold=true;d.textSize=10f*ui;d.setColor(enabled?0xFFF1F3F4:0xFF5D676D);d.text(text,b.cx(),b.cy()+3.4f*ui);d.align=Draw.Align.LEFT;d.bold=false;}

    private void tap(float x,float y){
        if(mode==Mode.MAIN){if(main[0].hit(x,y)||main[2].hit(x,y)){mode=Mode.SLOTS;return;}if(main[1].hit(x,y)&&game.saves.anySave()){game.playSlot(game.saves.lastSlot());return;}if(main[3].hit(x,y)){mode=Mode.SETTINGS;return;}if(main[4].hit(x,y)){mode=Mode.ABOUT;return;}if(main[5].hit(x,y)){Gdx.app.exit();return;}}
        else if(mode==Mode.SLOTS){for(int i=0;i<slots.length;i++){Box b=slots[i];if(!b.hit(x,y))continue;int slot=i+1;if(game.saves.exists(slot)&&x>b.r-50f*ui){pendingDelete=slot;mode=Mode.DELETE;}else if(game.saves.exists(slot))game.playSlot(slot);else game.playNewSlot(slot);return;}if(back.hit(x,y))mode=Mode.MAIN;}
        else if(mode==Mode.SETTINGS||mode==Mode.ABOUT){if(back.hit(x,y))mode=Mode.MAIN;}
        else if(mode==Mode.DELETE){if(yes.hit(x,y)){game.saves.delete(pendingDelete);pendingDelete=-1;mode=Mode.SLOTS;}else if(no.hit(x,y)){pendingDelete=-1;mode=Mode.SLOTS;}}
    }

    private static int adjust(int c,float f){int a=(c>>>24)&255,r=(c>>>16)&255,g=(c>>>8)&255,b=c&255;r=Math.min(255,Math.max(0,(int)(r*f)));g=Math.min(255,Math.max(0,(int)(g*f)));b=Math.min(255,Math.max(0,(int)(b*f)));return(a<<24)|(r<<16)|(g<<8)|b;}
    private static String fmt(long n){if(n>=1_000_000_000L)return String.format(java.util.Locale.US,"%.1fB",n/1_000_000_000d);if(n>=1_000_000L)return String.format(java.util.Locale.US,"%.1fM",n/1_000_000d);if(n>=10_000L)return String.format(java.util.Locale.US,"%.1fK",n/1_000d);return Long.toString(n);}
}
