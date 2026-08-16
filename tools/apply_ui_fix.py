from pathlib import Path

path = Path("core/src/main/java/com/enhort/gnomes/game/CaveScreen.java")
s = path.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global s
    count = s.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, got {count}")
    s = s.replace(old, new, 1)

replace_once(
    "import com.enhort.gnomes.draw.Draw;\n",
    "import com.enhort.gnomes.draw.Draw;\nimport com.enhort.gnomes.ui.UiTheme;\n",
    "UiTheme import",
)

replace_once(
'''    @Override public void resize(int w,int h){
        width=w;height=h;ui=Math.max(.72f,w/420f);
        game.draw.resize(w,h);
        worldL=0;worldR=w;worldT=82f*ui;worldB=h-252f*ui;
        if(worldB<worldT+250f*ui)worldB=worldT+250f*ui;
        layoutUi();
        generateDepth(false);
    }

    private void layoutUi(){
        back.set(8f*ui,10f*ui,48f*ui,48f*ui);
        float panel=worldB;
        float tabH=36f*ui;
        for(int i=0;i<4;i++)tabs[i].set(i*width/4f,panel,(i+1)*width/4f,panel+tabH);
        float contentT=panel+tabH+8f*ui;
        left.set(10f*ui,contentT+30f*ui,45f*ui,contentT+70f*ui);
        right.set(width-45f*ui,contentT+30f*ui,width-10f*ui,contentT+70f*ui);
        primary.set(56f*ui,contentT+84f*ui,width/2f-5f*ui,contentT+126f*ui);
        secondary.set(width/2f+5f*ui,contentT+84f*ui,width-56f*ui,contentT+126f*ui);
        tertiary.set(56f*ui,contentT+132f*ui,width/2f-5f*ui,contentT+170f*ui);
        quaternary.set(width/2f+5f*ui,contentT+132f*ui,width-56f*ui,contentT+170f*ui);
        speed.set(16f*ui,height-58f*ui,width-16f*ui,height-10f*ui);
    }
''',
'''    @Override public void resize(int w,int h){
        width=w;height=h;
        // UI scale is constrained by both dimensions. Wide/short screens no longer inflate controls until
        // they collide with each other, which was a surprisingly ambitious career choice for a button.
        ui=Math.max(.70f,Math.min(w/420f,h/820f));
        game.draw.resize(w,h);
        worldL=0;worldR=w;worldT=80f*ui;
        float panelH=Math.min(238f*ui,Math.max(190f*ui,h*.30f));
        worldB=h-panelH;
        if(worldB<worldT+210f*ui)worldB=worldT+210f*ui;
        layoutUi();
        generateDepth(false);
    }

    private void layoutUi(){
        back.set(8f*ui,9f*ui,48f*ui,47f*ui);
        float panel=worldB;
        float tabH=Math.min(36f*ui,Math.max(29f*ui,(height-panel)*.16f));
        for(int i=0;i<4;i++)tabs[i].set(i*width/4f,panel,(i+1)*width/4f,panel+tabH);

        float speedH=Math.min(46f*ui,Math.max(36f*ui,(height-panel)*.22f));
        speed.set(12f*ui,height-speedH-9f*ui,width-12f*ui,height-9f*ui);

        float contentT=panel+tabH+5f*ui;
        float navH=Math.min(38f*ui,Math.max(30f*ui,(speed.t-contentT)*.27f));
        left.set(9f*ui,contentT+3f*ui,42f*ui,contentT+3f*ui+navH);
        right.set(width-42f*ui,contentT+3f*ui,width-9f*ui,contentT+3f*ui+navH);

        float actionsTop=contentT+navH+7f*ui;
        float actionsBottom=speed.t-7f*ui;
        float gap=7f*ui;
        float side=9f*ui;
        float colGap=7f*ui;
        float rowH=Math.max(26f*ui,(actionsBottom-actionsTop-gap)*.5f);
        float mid=(width-colGap)*.5f;
        primary.set(side,actionsTop,mid,Math.min(actionsBottom,actionsTop+rowH));
        secondary.set(mid+colGap,actionsTop,width-side,Math.min(actionsBottom,actionsTop+rowH));
        float row2=Math.min(actionsBottom,actionsTop+rowH+gap);
        tertiary.set(side,row2,mid,actionsBottom);
        quaternary.set(mid+colGap,row2,width-side,actionsBottom);
    }
''',
    "responsive cave layout",
)

replace_once(
'''    @Override public void render(float rawDelta){
        float real=Math.min(.05f,rawDelta);
        float dt=real*(speedHeld?4f:1f);
        elapsed+=real;
        update(dt);
        Draw d=game.draw;d.beginFrame();
        if(screenShake>0){float sx=(float)Math.sin(elapsed*71f)*screenShake,sy=(float)Math.cos(elapsed*53f)*screenShake*.55f;d.save();d.translate(sx,sy);drawWorld(d);d.restore();}
        else drawWorld(d);
        drawHud(d);drawPanel(d);drawToast(d);d.endFrame();
    }

    private void update(float dt){
        if(map==null)return;
        if(toastTime>0)toastTime-=dt;
        if(screenShake>0)screenShake=Math.max(0,screenShake-dt*18f*ui);
        updateVeins(dt);updateGuardian(dt);updateMobs(dt);updateHazards(dt);updateWorkers(dt);updateFx(dt);
        enemyTimer-=dt;hazardTimer-=dt;saveTimer+=dt;
        if(enemyTimer<=0){spawnEnemyWave();enemyTimer=Math.max(8f,24f-state.depth*.25f)+random.nextFloat()*10f;}
        if(hazardTimer<=0){spawnHazard();hazardTimer=20f+random.nextFloat()*18f;}
        if(saveTimer>=4f){saveNow();saveTimer=0;}
        boolean any=false;for(Vein v:veins)if(!v.dead){any=true;break;}
        if(!any&&levelClearTimer<0){levelClearTimer=1.4f;toast="УРОВЕНЬ ОЧИЩЕН";toastTime=1.4f;}
        if(levelClearTimer>=0){levelClearTimer-=dt;if(levelClearTimer<=0)generateDepth(true);}
    }
''',
'''    @Override public void render(float rawDelta){
        float real=Math.min(.05f,rawDelta);
        elapsed+=real;
        update(real,speedHeld?4f:1f);
        Draw d=game.draw;d.beginFrame();
        if(screenShake>0){float sx=(float)Math.sin(elapsed*71f)*screenShake,sy=(float)Math.cos(elapsed*53f)*screenShake*.55f;d.save();d.translate(sx,sy);drawWorld(d);d.restore();}
        else drawWorld(d);
        drawHud(d);drawPanel(d);drawToast(d);d.endFrame();
    }

    private void update(float dt,float workerTimeScale){
        if(map==null)return;
        if(toastTime>0)toastTime-=dt;
        if(screenShake>0)screenShake=Math.max(0,screenShake-dt*18f*ui);

        // Acceleration belongs to the workforce, not to the universe. Enemies, hazards, regeneration,
        // boss summons, save cadence and level transitions continue in real game time.
        updateVeins(dt);
        updateGuardian(dt);
        updateMobs(dt);
        updateHazards(dt);
        updateWorkers(dt*workerTimeScale);
        updateFx(dt);

        enemyTimer-=dt;hazardTimer-=dt;saveTimer+=dt;
        if(enemyTimer<=0){spawnEnemyWave();enemyTimer=Math.max(8f,24f-state.depth*.25f)+random.nextFloat()*10f;}
        if(hazardTimer<=0){spawnHazard();hazardTimer=20f+random.nextFloat()*18f;}
        if(saveTimer>=4f){saveNow();saveTimer=0;}
        boolean any=false;for(Vein v:veins)if(!v.dead){any=true;break;}
        if(!any&&levelClearTimer<0){levelClearTimer=1.4f;toast="УРОВЕНЬ ОЧИЩЕН";toastTime=1.4f;}
        if(levelClearTimer>=0){levelClearTimer-=dt;if(levelClearTimer<=0)generateDepth(true);}
    }
''',
    "worker-only acceleration",
)

replace_once(
'''    private void drawHud(Draw d){
        d.setColor(0xFF111418);d.fillRect(0,0,width,worldT);d.setColor(0xFF20262C);d.fillRect(0,worldT-1f*ui,width,worldT);
        button(d,back,"‹",true,1.25f);d.align=Draw.Align.LEFT;d.bold=true;d.textSize=13f*ui;d.setColor(0xFFF0F3F5);d.text("GNOMES",58f*ui,24f*ui);d.bold=false;d.textSize=9.5f*ui;d.setColor(0xFF8E9AA3);d.text("ГЛУБИНА "+state.depth,58f*ui,43f*ui);
        float y=66f*ui;drawResource(d,12f*ui,y,0xFF888D92,"●",state.stone);drawResource(d,112f*ui,y,0xFFC6D0D8,"Ag",state.silver);drawResource(d,211f*ui,y,0xFFE2B544,"Au",state.gold);drawResource(d,309f*ui,y,0xFF67D7F2,"◆",state.diamond);
    }
''',
'''    private void drawHud(Draw d){
        d.setColor(0xFF0D1012);d.fillRect(0,0,width,worldT);
        d.setColor(0xFF252B30);d.fillRect(0,worldT-2f*ui,width,worldT);
        button(d,back,"‹",true,1.20f);
        d.align=Draw.Align.LEFT;d.bold=true;d.textSize=13f*ui;d.setColor(0xFFF2EFE7);d.text("GNOMES",58f*ui,23f*ui);
        d.bold=false;d.textSize=8.7f*ui;d.setColor(UiTheme.GOLD);d.text("ГЛУБИНА "+state.depth,58f*ui,42f*ui);
        float y=65f*ui,section=width/4f;
        drawResource(d,7f*ui,y,0xFF888D92,"●",state.stone);
        drawResource(d,section+5f*ui,y,0xFFC6D0D8,"Ag",state.silver);
        drawResource(d,section*2+5f*ui,y,0xFFE2B544,"Au",state.gold);
        drawResource(d,section*3+5f*ui,y,0xFF67D7F2,"◆",state.diamond);
    }
''',
    "hud redesign",
)

replace_once(
'''    private void drawPanel(Draw d){
        d.setColor(0xFF121519);d.fillRect(0,worldB,width,height);String[] names={"ГНОМЫ","АПГРЕЙДЫ","АРТЕФ.","РУНЫ"};for(int i=0;i<4;i++){if(tab.ordinal()==i){d.setColor(0xFF2A3239);d.fillRoundRect(tabs[i].l+2f*ui,tabs[i].t+2f*ui,tabs[i].r-2f*ui,tabs[i].b-2f*ui,5f*ui);}d.align=Draw.Align.CENTER;d.bold=tab.ordinal()==i;d.textSize=9f*ui;d.setColor(tab.ordinal()==i?0xFFF2F5F7:0xFF89949C);d.text(names[i],tabs[i].cx(),tabs[i].cy()+3f*ui);}d.align=Draw.Align.LEFT;d.bold=false;
        switch(tab){case GNOMES->drawGnomePanel(d);case UPGRADES->drawUpgradePanel(d);case ARTIFACTS->drawArtifactPanel(d);case RUNES->drawRunePanel(d);}button(d,speed,speedHeld?"УСКОРЕНИЕ ×4":"УДЕРЖИВАЙ • УСКОРИТЬ ×4",true,.92f);
    }

    private float contentTop(){return worldB+44f*ui;}
''',
'''    private void drawPanel(Draw d){
        UiTheme.panel(d,0,worldB,width,height,ui);
        String[] names={"ГНОМЫ","АПГРЕЙДЫ","АРТЕФ.","РУНЫ"};
        for(int i=0;i<4;i++)UiTheme.tab(d,tabs[i].l,tabs[i].t,tabs[i].r,tabs[i].b,ui,names[i],tab.ordinal()==i,UiTheme.GOLD);
        switch(tab){case GNOMES->drawGnomePanel(d);case UPGRADES->drawUpgradePanel(d);case ARTIFACTS->drawArtifactPanel(d);case RUNES->drawRunePanel(d);}
        button(d,speed,speedHeld?"ГНОМЫ РАБОТАЮТ ×4":"УСКОРИТЬ ГНОМОВ ×4",true,.86f);
    }

    private float contentTop(){return tabs[0].b+5f*ui;}
''',
    "panel redesign",
)

replace_once(
'''    private void button(Draw d,Box b,String text,boolean enabled,float scale){d.setColor(enabled?0xFF263039:0xFF1B2024);d.fillRoundRect(b.l,b.t,b.r,b.b,6f*ui);d.setColor(enabled?0xFF526775:0xFF2B3338);d.strokeWidth=1f*ui;d.line(b.l+5f*ui,b.t,b.r-5f*ui,b.t);d.align=Draw.Align.CENTER;d.bold=true;d.textSize=10f*ui*scale;d.setColor(enabled?0xFFF1F4F6:0xFF68727A);if(text!=null&&!text.isEmpty())d.text(text,b.cx(),b.cy()+3.2f*ui);d.align=Draw.Align.LEFT;d.bold=false;}
''',
'''    private void button(Draw d,Box b,String text,boolean enabled,float scale){
        int accent=b==speed?UiTheme.GOLD:(b==back?UiTheme.STEEL:UiTheme.COPPER);
        UiTheme.button(d,b.l,b.t,b.r,b.b,ui,text,enabled,accent,b==speed&&speedHeld,scale);
    }
''',
    "button theme",
)

path.write_text(s, encoding="utf-8")
print("CaveScreen UI patch applied")
