package com.enhort.gnomes.menu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.enhort.gnomes.GnomesGame;
import com.enhort.gnomes.GameAudio;
import com.enhort.gnomes.game.model.EnemyType;
import com.enhort.gnomes.draw.Draw;
import com.enhort.gnomes.save.SaveRepository;
import com.enhort.gnomes.ui.UiTheme;

import java.util.Random;

public final class MenuScreen extends ScreenAdapter {
    private enum Mode { MAIN, SLOTS, DIFFICULTY, BESTIARY, SETTINGS, ABOUT, DELETE }

    private static final class Box {
        float l,t,r,b;
        Box() {}
        void set(float l,float t,float r,float b){this.l=l;this.t=t;this.r=r;this.b=b;}
        boolean hit(float x,float y){return x>=l&&x<=r&&y>=t&&y<=b;}
        float cx(){return(l+r)*.5f;}
        float cy(){return(t+b)*.5f;}
        float h(){return b-t;}
    }

    private static final class Dust {
        float x,y,s,v,p;
        Dust(float x,float y,float s,float v,float p){this.x=x;this.y=y;this.s=s;this.v=v;this.p=p;}
    }

    private final GnomesGame game;
    private final Random rnd=new Random(0x6E6F6D6573L);
    private final Dust[] dust=new Dust[34];
    private final Box[] main=new Box[7];
    private final Box[] slots=new Box[SaveRepository.SLOT_COUNT];
    private final Box[] slotDelete=new Box[SaveRepository.SLOT_COUNT];
    private final Box[] difficultyButtons={new Box(),new Box(),new Box(),new Box()};
    private final Box back=new Box();
    private final Box yes=new Box(),no=new Box();
    private final Box infoCard=new Box();
    private final Box soundToggle=new Box(),vibrationToggle=new Box(),volumeDown=new Box(),volumeUp=new Box(),cheatG=new Box();

    private Mode mode=Mode.MAIN;
    private float width,height,ui,elapsed;
    private int pendingDelete=-1;
    private int pendingNewSlot=-1;
    private int gTapCount;
    private float cheatNotice;

    public MenuScreen(GnomesGame game){
        this.game=game;
        for(int i=0;i<main.length;i++)main[i]=new Box();
        for(int i=0;i<slots.length;i++){slots[i]=new Box();slotDelete[i]=new Box();}
        for(int i=0;i<dust.length;i++)dust[i]=new Dust(rnd.nextFloat(),rnd.nextFloat(),.5f+rnd.nextFloat()*1.3f,2f+rnd.nextFloat()*8f,rnd.nextFloat()*6.28f);
    }

    @Override public void show(){
        Gdx.input.setCatchKey(Input.Keys.BACK,true);
        Gdx.input.setInputProcessor(new InputAdapter(){
            @Override public boolean keyDown(int keycode){
                if(keycode==Input.Keys.BACK||keycode==Input.Keys.ESCAPE){
                    if(mode!=Mode.MAIN){mode=Mode.MAIN;pendingDelete=-1;}
                    return true;
                }
                return false;
            }
            @Override public boolean touchDown(int x,int y,int pointer,int button){tap(x,y);return true;}
        });
    }

    @Override public void resize(int w,int h){
        width=w;height=h;
        ui=Math.max(.70f,Math.min(w/420f,h/820f));
        game.draw.resize(w,h);
        layout();
    }

    private void layout(){
        float side=18f*ui;
        float headerBottom=Math.min(height*.25f,155f*ui);
        float footerTop=height-64f*ui;
        float maxWidth=Math.min(width-side*2,304f*ui);
        float x=(width-maxWidth)/2f;
        float gap=8f*ui;
        float available=Math.max(240f*ui,footerTop-headerBottom-18f*ui);
        float bh=Math.min(50f*ui,(available-gap*(main.length-1))/main.length);
        bh=Math.max(36f*ui,bh);
        float stack=bh*main.length+gap*(main.length-1);
        float y=headerBottom+Math.max(8f*ui,(footerTop-headerBottom-stack)*.5f);
        for(int i=0;i<main.length;i++)main[i].set(x,y+i*(bh+gap),x+maxWidth,y+i*(bh+gap)+bh);

        back.set(side,height-55f*ui,width-side,height-11f*ui);

        float sw=Math.min(width-side*2,344f*ui),sx=(width-sw)/2f;
        float slotTop=Math.min(height*.24f,150f*ui);
        float slotBottom=back.t-10f*ui;
        float sg=7f*ui;
        float sh=(slotBottom-slotTop-sg*(slots.length-1))/slots.length;
        sh=Math.max(43f*ui,Math.min(68f*ui,sh));
        float total=sh*slots.length+sg*(slots.length-1);
        float sy=slotTop+Math.max(0,(slotBottom-slotTop-total)*.5f);
        for(int i=0;i<slots.length;i++){
            float top=sy+i*(sh+sg);
            slots[i].set(sx,top,sx+sw,top+sh);
            float dw=Math.min(40f*ui,sh*.72f);
            slotDelete[i].set(slots[i].r-dw-7f*ui,top+7f*ui,slots[i].r-7f*ui,top+sh-7f*ui);
        }

        float diffW=Math.min(width-side*2,330f*ui),diffX=(width-diffW)/2f;
        float diffTop=Math.min(height*.25f,160f*ui),diffBottom=back.t-12f*ui,diffGap=9f*ui;
        float diffH=Math.max(42f*ui,Math.min(58f*ui,(diffBottom-diffTop-diffGap*3)/4f));
        float diffTotal=diffH*4+diffGap*3,diffY=diffTop+Math.max(0,(diffBottom-diffTop-diffTotal)*.5f);
        for(int i=0;i<4;i++)difficultyButtons[i].set(diffX,diffY+i*(diffH+diffGap),diffX+diffW,diffY+i*(diffH+diffGap)+diffH);

        float confirmW=Math.min(width-42f*ui,330f*ui),confirmX=(width-confirmW)/2f;
        float confirmY=Math.min(height*.58f,height-130f*ui);
        float confirmGap=10f*ui,confirmButton=(confirmW-confirmGap)/2f;
        yes.set(confirmX,confirmY,confirmX+confirmButton,confirmY+48f*ui);
        no.set(confirmX+confirmButton+confirmGap,confirmY,confirmX+confirmW,confirmY+48f*ui);

        float cardW=Math.min(width-42f*ui,340f*ui);
        float cardTop=Math.max(150f*ui,height*.30f);
        float cardBottom=Math.min(back.t-18f*ui,cardTop+190f*ui);
        infoCard.set((width-cardW)/2f,cardTop,(width+cardW)/2f,cardBottom);

        float rowR=infoCard.r-14f*ui;
        soundToggle.set(rowR-104f*ui,infoCard.t+16f*ui,rowR,infoCard.t+50f*ui);
        vibrationToggle.set(rowR-104f*ui,infoCard.t+58f*ui,rowR,infoCard.t+92f*ui);
        volumeDown.set(rowR-142f*ui,infoCard.t+100f*ui,rowR-100f*ui,infoCard.t+134f*ui);
        volumeUp.set(rowR-42f*ui,infoCard.t+100f*ui,rowR,infoCard.t+134f*ui);
        // GNOMES is centered at y=74. This box covers only the first G, not the whole title.
        cheatG.set(width/2f-86f*ui,44f*ui,width/2f-45f*ui,96f*ui);
    }

    @Override public void render(float delta){
        elapsed+=Math.min(delta,.05f);
        if(cheatNotice>0)cheatNotice-=Math.min(delta,.05f);
        Draw d=game.draw;
        d.beginFrame();
        background(d);
        switch(mode){case MAIN->main(d);case SLOTS->slots(d);case DIFFICULTY->difficulty(d);case BESTIARY->bestiary(d);case SETTINGS->settings(d);case ABOUT->about(d);case DELETE->delete(d);}
        d.endFrame();
    }

    private void background(Draw d){
        d.setColor(0xFF08090A);d.fillRect(0,0,width,height);
        float tw=88f*ui;
        d.setColor(0xFF1D1A17);d.strokeWidth=tw*1.18f;paths(d);
        d.setColor(0xFF302A24);d.strokeWidth=tw*.82f;paths(d);
        d.setColor(0xFF201D1A);d.strokeWidth=tw*.62f;paths(d);
        for(Dust p:dust){
            float x=p.x*width+(float)Math.sin(elapsed*.18f+p.p)*12f*ui;
            float y=(p.y*height+elapsed*p.v*ui)%height;
            d.setColor(0x18D9C8A8);d.fillCircle(x,y,p.s*ui);
        }
        torch(d,width*.17f,height*.31f,0);
        torch(d,width*.83f,height*.32f,2);
        torch(d,width*.44f,height*.69f,4);
        menuGnome(d,width*.18f+(float)Math.sin(elapsed*.55f)*width*.10f,height*.67f,elapsed*7f,0xFF69B9E7);
        menuGnome(d,width*.69f+(float)Math.sin(elapsed*.43f+2)*width*.09f,height*.30f,elapsed*7.7f+1,0xFFF0B85A);
        menuImp(d,width*.82f,height*.56f+(float)Math.sin(elapsed*1.8f)*7f*ui);

        // Soft vignette strips frame the actual UI and stop animated background details fighting the text.
        d.setColor(0x99000000);d.fillRect(0,0,width,136f*ui);
        d.setColor(0x66000000);d.fillRect(0,height-82f*ui,width,height);
    }

    private void paths(Draw d){
        d.line(width*.17f,height*.15f,width*.17f,height*.86f);
        d.line(width*.17f,height*.32f,width*.83f,height*.32f);
        d.line(width*.83f,height*.32f,width*.83f,height*.78f);
        d.line(width*.17f,height*.69f,width*.66f,height*.69f);
    }

    private void torch(Draw d,float x,float y,int seed){
        float f=.82f+.18f*(float)Math.sin(elapsed*8.5f+seed);
        d.setColor(0x18FF9A30);d.fillCircle(x,y,28f*ui*f);
        d.setColor(0x2AFFB54C);d.fillCircle(x,y,12f*ui*f);
        d.setColor(0xFF6C4930);d.strokeWidth=2.4f*ui;d.line(x,y+7f*ui,x,y+19f*ui);
        d.setColor(0xFFFF902E);d.fillOval(x-4f*ui,y-8f*ui,x+4f*ui,y+4f*ui);
        d.setColor(0xFFFFD46A);d.fillOval(x-1.6f*ui,y-5f*ui,x+1.6f*ui,y+1f*ui);
    }

    private void menuGnome(Draw d,float x,float y,float p,int col){
        float s=18f*ui,stride=(float)Math.sin(p),bob=Math.abs((float)Math.cos(p))*1.5f*ui;
        d.save();d.translate(x,y-bob);
        d.setColor(0x55000000);d.fillOval(-s*.38f,s*.47f,s*.38f,s*.60f);
        d.setColor(0xFF3A2C25);d.strokeWidth=s*.11f;d.line(-s*.10f,s*.24f,-s*.18f+stride*s*.12f,s*.52f);d.line(s*.10f,s*.24f,s*.18f-stride*s*.12f,s*.52f);
        d.setColor(adjust(col,.68f));d.fillOval(-s*.28f,-s*.02f,s*.28f,s*.35f);
        d.setColor(0xFFE4B584);d.fillCircle(0,-s*.22f,s*.24f);
        d.setColor(0xFFE9E5DA);d.pathReset();d.moveTo(-s*.22f,-s*.12f);d.quadTo(-stride*s*.03f,s*.35f,s*.23f,-s*.12f);d.quadTo(0,s*.18f,-s*.22f,-s*.12f);d.closePath();d.fillPath();
        d.setColor(col);d.pathReset();d.moveTo(-s*.25f,-s*.38f);d.quadTo(0,-s*.78f,s*.20f,-s*.42f);d.lineTo(s*.30f,-s*.35f);d.lineTo(-s*.26f,-s*.34f);d.closePath();d.fillPath();
        d.setColor(0xFF171615);d.fillCircle(s*.13f,-s*.28f,s*.025f);
        d.restore();
    }

    private void menuImp(Draw d,float x,float y){
        float s=16f*ui,f=(float)Math.sin(elapsed*14f);
        d.save();d.translate(x,y);
        d.setColor(0xFFA53B34);d.pathReset();d.moveTo(-s*.18f,-s*.05f);d.lineTo(-s*(.55f+.10f*f),-s*.32f);d.lineTo(-s*.38f,s*.13f);d.closePath();d.fillPath();d.pathReset();d.moveTo(s*.18f,-s*.05f);d.lineTo(s*(.55f+.10f*f),-s*.32f);d.lineTo(s*.38f,s*.13f);d.closePath();d.fillPath();
        d.setColor(0xFFD34D3F);d.fillOval(-s*.28f,-s*.18f,s*.28f,s*.48f);d.fillCircle(0,-s*.30f,s*.27f);
        d.setColor(0xFFFFE45A);d.fillCircle(-s*.10f,-s*.34f,s*.04f);d.fillCircle(s*.10f,-s*.34f,s*.04f);
        d.restore();
    }

    private void heading(Draw d,String sub){
        d.align=Draw.Align.CENTER;
        d.bold=true;d.textSize=34f*ui;d.setColor(0xFFF2EFE7);d.text("GNOMES",width/2,74f*ui);
        d.textSize=9f*ui;d.setColor(UiTheme.GOLD);d.text(sub,width/2,101f*ui);
        d.setColor(0x558A7450);d.fillRoundRect(width/2-42f*ui,111f*ui,width/2+42f*ui,113f*ui,1f*ui);
        d.bold=false;d.align=Draw.Align.LEFT;
    }

    private void main(Draw d){
        heading(d,"DEEP MINE • ALPHA 0.4");
        button(d,main[0],"ИГРАТЬ",true,UiTheme.GOLD,false,1f);
        button(d,main[1],"ПРОДОЛЖИТЬ",game.saves.anySave(),UiTheme.GREEN,false,.94f);
        button(d,main[2],"СОХРАНЕНИЯ",true,UiTheme.STEEL,false,.92f);
        button(d,main[3],"НАСТРОЙКИ",true,UiTheme.STEEL,false,.92f);
        button(d,main[4],"БЕСТИАРИЙ",true,UiTheme.COPPER,false,.92f);
        button(d,main[5],"ОБ ИГРЕ",true,UiTheme.COPPER,false,.92f);
        button(d,main[6],"ВЫХОД",true,UiTheme.RED,false,.92f);
        if(game.settings.freeShop||cheatNotice>0){
            d.align=Draw.Align.CENTER;d.bold=true;d.textSize=7.5f*ui;
            d.setColor(game.settings.freeShop?0xFFFFC74A:0xFF8D979D);
            d.text(game.settings.freeShop?"TEST MODE • ВСЁ БЕСПЛАТНО":"TEST MODE ВЫКЛЮЧЕН",width/2,126f*ui);
            d.align=Draw.Align.LEFT;d.bold=false;
        }
    }

    private void slots(Draw d){
        heading(d,"ШАХТНЫЕ ЖУРНАЛЫ");
        d.align=Draw.Align.CENTER;d.textSize=8f*ui;d.setColor(0xFFAEB8BF);d.text("5 независимых экспедиций",width/2,127f*ui);d.align=Draw.Align.LEFT;
        for(int i=0;i<slots.length;i++){
            int slot=i+1;
            SaveRepository.Snapshot s=game.saves.summary(slot);
            Box b=slots[i];
            d.setColor(0x77000000);d.fillRoundRect(b.l+2f*ui,b.t+3f*ui,b.r+2f*ui,b.b+3f*ui,8f*ui);
            d.setColor(s==null?0xEE171C1F:0xF020262A);d.fillRoundRect(b.l,b.t,b.r,b.b,8f*ui);
            d.setColor(s==null?0xFF465159:UiTheme.GOLD);d.fillRoundRect(b.l+2f*ui,b.t+3f*ui,b.l+5f*ui,b.b-3f*ui,1.5f*ui);
            d.bold=true;d.textSize=9f*ui;d.setColor(0xFFF1F0EA);d.text("ЯЧЕЙКА "+slot,b.l+14f*ui,b.t+b.h()*.38f);
            d.bold=false;d.textSize=7.2f*ui;
            if(s==null){
                d.setColor(0xFF7D8991);d.text("пусто • начать новую шахту",b.l+14f*ui,b.t+b.h()*.72f);
            }else{
                d.setColor(0xFFB8C1C7);d.text("глуб. "+Math.max(1,s.depth)+"  •  "+difficultyShort(s.difficulty)+"  •  кам "+fmt(s.stone)+"  •  ◆ "+fmt(s.diamond),b.l+14f*ui,b.t+b.h()*.72f);
                button(d,slotDelete[i],"×",true,UiTheme.RED,false,1.05f);
            }
        }
        button(d,back,"НАЗАД",true,UiTheme.STEEL,false,.92f);
    }

    private void difficulty(Draw d){
        heading(d,"СЛОЖНОСТЬ ЭКСПЕДИЦИИ");
        d.align=Draw.Align.CENTER;d.textSize=7.8f*ui;d.setColor(0xFF9EAAAF);
        d.text("Деньги делятся по сложности, а половина отряда идёт глубже.",width/2,128f*ui);
        d.align=Draw.Align.LEFT;
        button(d,difficultyButtons[0],"ЛЁГКАЯ  •  перенос 1/2",true,UiTheme.GREEN,false,.82f);
        button(d,difficultyButtons[1],"СРЕДНЯЯ  •  перенос 1/3",true,UiTheme.GOLD,false,.82f);
        button(d,difficultyButtons[2],"СЛОЖНАЯ  •  перенос 1/4",true,UiTheme.COPPER,false,.82f);
        button(d,difficultyButtons[3],"БЕЗ ПЕРЕНОСА  •  0",true,UiTheme.RED,false,.82f);
        button(d,back,"НАЗАД",true,UiTheme.STEEL,false,.92f);
    }

    private void bestiary(Draw d){
        heading(d,"КНИГА ВРАГОВ");
        float l=18f*ui,r=width-18f*ui,t=135f*ui,b=back.t-10f*ui,mid=width/2f;
        d.setColor(0xFF5A3D27);d.fillRoundRect(l-3f*ui,t-3f*ui,r+3f*ui,b+3f*ui,10f*ui);
        d.setColor(0xFFF0E1B8);d.fillRoundRect(l,t,mid-3f*ui,b,7f*ui);d.fillRoundRect(mid+3f*ui,t,r,b,7f*ui);
        d.setColor(0x33805B35);d.fillRect(mid-1f*ui,t+3f*ui,mid+1f*ui,b-3f*ui);
        EnemyType[] all=EnemyType.values();int deepest=game.saves.deepestDepth();
        float row=(b-t-18f*ui)/5f;
        for(int i=0;i<all.length;i++){
            EnemyType e=all[i];int col=i/5,ri=i%5;float x=col==0?l+10f*ui:mid+12f*ui,y=t+11f*ui+ri*row;
            int unlock=enemyUnlock(e);boolean known=deepest>=unlock||game.settings.freeShop;
            drawEnemyStamp(d,e,x+14f*ui,y+20f*ui,known);
            d.bold=true;d.textSize=7.6f*ui;d.setColor(known?0xFF3B2A20:0xFF95876F);d.text(known?e.title.toUpperCase():"???",x+31f*ui,y+13f*ui);
            d.bold=false;d.textSize=6.2f*ui;d.setColor(known?0xFF6B5844:0xFF9C907C);
            d.text(known?enemyRole(e):"встречается глубже",x+31f*ui,y+29f*ui);
            if(known){d.text("база HP "+Math.round(e.hp),x+31f*ui,y+43f*ui);}
        }
        d.align=Draw.Align.CENTER;d.textSize=6.8f*ui;d.setColor(0xFF7D6B52);d.text("Записи открываются по мере погружения. В TEST MODE книга полная.",width/2,b-8f*ui);d.align=Draw.Align.LEFT;
        button(d,back,"ЗАКРЫТЬ КНИГУ",true,UiTheme.STEEL,false,.90f);
    }

    private int enemyUnlock(EnemyType e){return switch(e){case IMP->1;case GHOST->2;case DEMON->7;case SUCCUBUS->9;case IMP_KING->10;case STONE_GOLEM->12;case WATER_GOLEM->15;case FIRE_GOLEM->18;case DEMON_KING->20;case ELEMENTAL_KING->30;};}
    private String enemyRole(EnemyType e){return switch(e){case IMP->"вор • боится отпора";case DEMON->"охотник на гномов";case SUCCUBUS->"чары • сеет драку";case GHOST->"летает сквозь стены";case STONE_GOLEM->"тяжёлый элементаль";case WATER_GOLEM->"водный элементаль";case FIRE_GOLEM->"не боится лавы";case IMP_KING->"босс • вор и призыватель";case DEMON_KING->"босс • убийца";case ELEMENTAL_KING->"босс • стихии";};}
    private void drawEnemyStamp(Draw d,EnemyType e,float x,float y,boolean known){
        int c=known?e.color:0xFF9B8F79;d.setColor(0x33614A31);d.fillCircle(x,y,13f*ui);d.setColor(c);d.fillCircle(x,y,known?7f*ui:5f*ui);
        if(known&&e.isImp()){d.pathReset();d.moveTo(x-6f*ui,y-5f*ui);d.lineTo(x-11f*ui,y-12f*ui);d.lineTo(x-2f*ui,y-8f*ui);d.closePath();d.fillPath();d.pathReset();d.moveTo(x+6f*ui,y-5f*ui);d.lineTo(x+11f*ui,y-12f*ui);d.lineTo(x+2f*ui,y-8f*ui);d.closePath();d.fillPath();}
        if(!known){d.bold=true;d.textSize=8f*ui;d.setColor(0xFFF0E1B8);d.align=Draw.Align.CENTER;d.text("?",x,y+3f*ui);d.align=Draw.Align.LEFT;d.bold=false;}
    }

    private void settings(Draw d){
        heading(d,"НАСТРОЙКИ");
        card(d,infoCard);
        float lx=infoCard.l+16f*ui;
        d.bold=true;d.textSize=8.8f*ui;d.setColor(0xFFE7E2D7);
        d.text("ЗВУКИ",lx,infoCard.t+38f*ui);
        d.text("ВИБРАЦИЯ",lx,infoCard.t+80f*ui);
        d.text("ГРОМКОСТЬ",lx,infoCard.t+122f*ui);
        d.bold=false;
        button(d,soundToggle,game.settings.soundEnabled?"ВКЛ":"ВЫКЛ",true,game.settings.soundEnabled?UiTheme.GREEN:UiTheme.STEEL,false,.82f);
        button(d,vibrationToggle,game.settings.vibrationEnabled?"ВКЛ":"ВЫКЛ",true,game.settings.vibrationEnabled?UiTheme.GREEN:UiTheme.STEEL,false,.82f);
        button(d,volumeDown,"−",game.settings.soundVolume>0.01f,UiTheme.COPPER,false,1f);
        button(d,volumeUp,"+",game.settings.soundVolume<.99f,UiTheme.COPPER,false,1f);
        d.align=Draw.Align.CENTER;d.bold=true;d.textSize=7.8f*ui;d.setColor(UiTheme.GOLD);
        d.text(Math.round(game.settings.soundVolume*100)+"%",(volumeDown.r+volumeUp.l)/2f,volumeDown.cy()+3f*ui);
        d.align=Draw.Align.LEFT;d.bold=false;
        d.textSize=7.3f*ui;d.setColor(0xFF89949A);d.text("SFX шахты, боя и интерфейса",lx,infoCard.t+155f*ui);
        if(game.settings.freeShop){d.setColor(0xFFFFC74A);d.text("TEST MODE: покупки бесплатны",lx,infoCard.t+176f*ui);}
        button(d,back,"НАЗАД",true,UiTheme.STEEL,false,.92f);
    }

    private void about(Draw d){
        heading(d,"ОБ ИГРЕ");
        card(d,infoCard);
        d.align=Draw.Align.CENTER;
        d.bold=true;d.textSize=11f*ui;d.setColor(0xFFF0EEE7);d.text("GNOMES",width/2,infoCard.t+36f*ui);
        d.bold=false;d.textSize=8.2f*ui;d.setColor(0xFFB6BFC5);d.text("туннели • добыча • эволюция",width/2,infoCard.t+70f*ui);d.text("руны • обвалы • демоны",width/2,infoCard.t+94f*ui);
        d.setColor(UiTheme.GOLD);d.text("Developer: Ponikarov Artem",width/2,infoCard.t+137f*ui);
        d.align=Draw.Align.LEFT;
        button(d,back,"НАЗАД",true,UiTheme.STEEL,false,.92f);
    }

    private void delete(Draw d){
        heading(d,"УДАЛИТЬ СОХРАНЕНИЕ?");
        card(d,infoCard);
        d.align=Draw.Align.CENTER;d.bold=true;d.textSize=14f*ui;d.setColor(0xFFF1F0EA);d.text("Ячейка "+pendingDelete,width/2,infoCard.t+58f*ui);
        d.bold=false;d.textSize=8.5f*ui;d.setColor(0xFFB2BBC1);d.text("Прогресс этой шахты будет удалён.",width/2,infoCard.t+94f*ui);d.align=Draw.Align.LEFT;
        button(d,yes,"УДАЛИТЬ",true,UiTheme.RED,false,.88f);
        button(d,no,"ОТМЕНА",true,UiTheme.STEEL,false,.88f);
    }

    private void card(Draw d,Box b){
        d.setColor(0x77000000);d.fillRoundRect(b.l+2f*ui,b.t+4f*ui,b.r+2f*ui,b.b+4f*ui,12f*ui);
        d.setColor(0xEE171B1E);d.fillRoundRect(b.l,b.t,b.r,b.b,12f*ui);
        d.setColor(0xFF323A40);d.fillRoundRect(b.l+1f*ui,b.t+1f*ui,b.r-1f*ui,b.b-1f*ui,11f*ui);
        d.setColor(0xFF171B1E);d.fillRoundRect(b.l+2f*ui,b.t+3f*ui,b.r-2f*ui,b.b-2f*ui,10f*ui);
        d.setColor(UiTheme.GOLD);d.fillRoundRect(b.l+14f*ui,b.t+3f*ui,b.r-14f*ui,b.t+5f*ui,1f*ui);
    }

    private void button(Draw d,Box b,String text,boolean enabled,int accent,boolean pressed,float scale){
        UiTheme.button(d,b.l,b.t,b.r,b.b,ui,text,enabled,accent,pressed,scale);
    }

    private void tap(float x,float y){
        if(mode==Mode.MAIN){
            if(cheatG.hit(x,y)){
                gTapCount++;game.audio.play(GameAudio.Sfx.UI,.25f);
                if(gTapCount>=10){gTapCount=0;game.settings.toggleFreeShop();game.syncCheats();cheatNotice=2.6f;game.audio.play(GameAudio.Sfx.COIN,.95f);game.audio.vibrate(70);}
                return;
            }
            if(main[0].hit(x,y)||main[2].hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.5f);mode=Mode.SLOTS;return;}
            if(main[1].hit(x,y)&&game.saves.anySave()){game.audio.play(GameAudio.Sfx.UI,.5f);game.playSlot(game.saves.lastSlot());return;}
            if(main[3].hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.5f);mode=Mode.SETTINGS;return;}
            if(main[4].hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.5f);mode=Mode.BESTIARY;return;}
            if(main[5].hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.5f);mode=Mode.ABOUT;return;}
            if(main[6].hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.5f);Gdx.app.exit();return;}
        }else if(mode==Mode.SLOTS){
            for(int i=0;i<slots.length;i++){
                int slot=i+1;
                if(game.saves.exists(slot)&&slotDelete[i].hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.45f);pendingDelete=slot;mode=Mode.DELETE;return;}
                if(slots[i].hit(x,y)){
                    game.audio.play(GameAudio.Sfx.UI,.5f);
                    if(game.saves.exists(slot))game.playSlot(slot);else{pendingNewSlot=slot;mode=Mode.DIFFICULTY;}
                    return;
                }
            }
            if(back.hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.4f);mode=Mode.MAIN;}
        }else if(mode==Mode.DIFFICULTY){
            for(int i=0;i<difficultyButtons.length;i++)if(difficultyButtons[i].hit(x,y)){
                game.audio.play(GameAudio.Sfx.UI,.6f);int slot=pendingNewSlot;pendingNewSlot=-1;game.playNewSlot(slot,i+1);return;
            }
            if(back.hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.4f);pendingNewSlot=-1;mode=Mode.SLOTS;}
        }else if(mode==Mode.BESTIARY){
            if(back.hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.4f);mode=Mode.MAIN;}
        }else if(mode==Mode.SETTINGS){
            if(soundToggle.hit(x,y)){game.settings.toggleSound();game.audio.refreshMusic();if(game.settings.soundEnabled)game.audio.play(GameAudio.Sfx.UI,.75f);return;}
            if(vibrationToggle.hit(x,y)){game.settings.toggleVibration();if(game.settings.vibrationEnabled)game.audio.vibrate(45);game.audio.play(GameAudio.Sfx.UI,.5f);return;}
            if(volumeDown.hit(x,y)){game.settings.setSoundVolume(game.settings.soundVolume-.10f);game.audio.refreshMusic();game.audio.play(GameAudio.Sfx.UI,.6f);return;}
            if(volumeUp.hit(x,y)){game.settings.setSoundVolume(game.settings.soundVolume+.10f);game.audio.refreshMusic();game.audio.play(GameAudio.Sfx.UI,.6f);return;}
            if(back.hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.4f);mode=Mode.MAIN;}
        }else if(mode==Mode.ABOUT){
            if(back.hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.4f);mode=Mode.MAIN;}
        }else if(mode==Mode.DELETE){
            if(yes.hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.5f);game.saves.delete(pendingDelete);pendingDelete=-1;mode=Mode.SLOTS;}
            else if(no.hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.4f);pendingDelete=-1;mode=Mode.SLOTS;}
        }
    }

    private static int adjust(int c,float f){
        int a=(c>>>24)&255,r=(c>>>16)&255,g=(c>>>8)&255,b=c&255;
        r=Math.min(255,Math.max(0,(int)(r*f)));g=Math.min(255,Math.max(0,(int)(g*f)));b=Math.min(255,Math.max(0,(int)(b*f)));
        return(a<<24)|(r<<16)|(g<<8)|b;
    }

    private static String difficultyShort(int d){return switch(d){case 1->"лёгк.";case 3->"сложн.";case 4->"без перен.";default->"средн.";};}

    private static String fmt(long n){
        if(n>=1_000_000_000L)return String.format(java.util.Locale.US,"%.1fB",n/1_000_000_000d);
        if(n>=1_000_000L)return String.format(java.util.Locale.US,"%.1fM",n/1_000_000d);
        if(n>=10_000L)return String.format(java.util.Locale.US,"%.1fK",n/1_000d);
        return Long.toString(n);
    }
}
