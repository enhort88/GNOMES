from pathlib import Path
import re, math, struct, wave

ROOT=Path('.')

def read(p): return (ROOT/p).read_text()
def write(p,s): (ROOT/p).write_text(s)
def replace_once(s,old,new,label):
    if old not in s: raise SystemExit(f'missing anchor: {label}')
    return s.replace(old,new,1)
def sub_once(s,pat,repl,label,flags=re.S):
    out,n=re.subn(pat,repl,s,count=1,flags=flags)
    if n!=1: raise SystemExit(f'regex failed: {label} ({n})')
    return out

# -----------------------------------------------------------------------------
# Text readability: requested literally ~2x everywhere. Keep logical textSize
# values so existing layout code stays understandable, but render glyphs at 2x.
p='core/src/main/java/com/enhort/gnomes/draw/Draw.java'
s=read(p)
s=replace_once(s,'        float scale = textSize / 32f;','        float effectiveSize = textSize * 2.0f;\n        float scale = effectiveSize / 32f;','global 2x text')
s=replace_once(s,'        f.draw(batch, s, drawX, y - textSize * 0.78f);','        f.draw(batch, s, drawX, y - effectiveSize * 0.78f);','global 2x baseline')
write(p,s)

# -----------------------------------------------------------------------------
# Gnome silhouettes and naming.
p='core/src/main/java/com/enhort/gnomes/game/model/GnomeTier.java'
s=read(p)
s=s.replace('EXCAVATOR("Экскаватор", 280.0f, 55f, 110.0f, 0xFFF4D35E, 31, 430f)',
            'EXCAVATOR("Лазерный экскаватор", 280.0f, 55f, 110.0f, 0xFFF4D35E, 28, 430f)')
s=s.replace('IRON_GOLEM("Железный гном", 1500.0f, 48f, 650.0f, 0xFFB7C5D1, 36, 1200f)',
            'IRON_GOLEM("Лазерный голем", 1500.0f, 48f, 650.0f, 0xFFB7C5D1, 24, 1200f)')
write(p,s)

p='core/src/main/java/com/enhort/gnomes/game/model/EnemyType.java'
s=read(p)
s=s.replace('STONE_GOLEM("Каменный голем", 260f, 22f, 5.0f, 0xFF7D766A, 29, Family.ELEMENTAL)',
            'STONE_GOLEM("Каменный голем", 260f, 22f, 5.0f, 0xFF7D766A, 24, Family.ELEMENTAL)')
s=s.replace('WATER_GOLEM("Водный голем", 320f, 25f, 5.0f, 0xFF4BA3D9, 29, Family.ELEMENTAL)',
            'WATER_GOLEM("Водный голем", 320f, 25f, 5.0f, 0xFF4BA3D9, 24, Family.ELEMENTAL)')
s=s.replace('FIRE_GOLEM("Огненный голем", 360f, 27f, 6.0f, 0xFFE9652C, 29, Family.ELEMENTAL)',
            'FIRE_GOLEM("Огненный голем", 360f, 27f, 6.0f, 0xFFE9652C, 25, Family.ELEMENTAL)')
write(p,s)

# -----------------------------------------------------------------------------
# Separate music and SFX settings.
p='core/src/main/java/com/enhort/gnomes/GameSettings.java'
s=read(p)
s=replace_once(s,'    public boolean soundEnabled;\n    public boolean vibrationEnabled;\n    public float soundVolume;',
'''    public boolean soundEnabled;
    public boolean musicEnabled;
    public boolean vibrationEnabled;
    public float soundVolume;
    public float musicVolume;''','settings fields')
s=replace_once(s,'        soundEnabled = prefs.getBoolean("sound", true);\n        vibrationEnabled = prefs.getBoolean("vibration", true);\n        soundVolume = clamp(prefs.getFloat("volume", 0.75f));',
'''        soundEnabled = prefs.getBoolean("sound", true);
        musicEnabled = prefs.getBoolean("music", true);
        vibrationEnabled = prefs.getBoolean("vibration", true);
        soundVolume = clamp(prefs.getFloat("volume", 0.75f));
        musicVolume = clamp(prefs.getFloat("musicVolume", 0.62f));''','settings load')
s=replace_once(s,'    public void toggleSound() { soundEnabled = !soundEnabled; save(); }\n    public void toggleVibration() { vibrationEnabled = !vibrationEnabled; save(); }\n    public void setSoundVolume(float value) { soundVolume = clamp(value); save(); }',
'''    public void toggleSound() { soundEnabled = !soundEnabled; save(); }
    public void toggleMusic() { musicEnabled = !musicEnabled; save(); }
    public void toggleVibration() { vibrationEnabled = !vibrationEnabled; save(); }
    public void setSoundVolume(float value) { soundVolume = clamp(value); save(); }
    public void setMusicVolume(float value) { musicVolume = clamp(value); save(); }''','settings methods')
s=replace_once(s,'        prefs.putBoolean("sound", soundEnabled);\n        prefs.putBoolean("vibration", vibrationEnabled);\n        prefs.putFloat("volume", soundVolume);',
'''        prefs.putBoolean("sound", soundEnabled);
        prefs.putBoolean("music", musicEnabled);
        prefs.putBoolean("vibration", vibrationEnabled);
        prefs.putFloat("volume", soundVolume);
        prefs.putFloat("musicVolume", musicVolume);''','settings save')
write(p,s)

p='core/src/main/java/com/enhort/gnomes/GameAudio.java'
s=read(p)
s=s.replace('Gdx.files.internal("music/mine_loop.wav")','Gdx.files.internal("music/mine_loop_v2.wav")')
s=replace_once(s,'        float volume = settings.soundEnabled ? Math.max(0f, Math.min(1f, settings.soundVolume * .38f)) : 0f;',
                   '        float volume = settings.musicEnabled ? Math.max(0f, Math.min(1f, settings.musicVolume * .50f)) : 0f;','music volume')
write(p,s)

p='core/src/main/java/com/enhort/gnomes/GnomesGame.java'
s=read(p)
s=replace_once(s,'    public void openMenu() { changeScreen(new MenuScreen(this)); }',
                   '    public void openMenu() { changeScreen(new MenuScreen(this)); }\n    public void openIntro() { changeScreen(new IntroScreen(this)); }','open intro')
write(p,s)

# -----------------------------------------------------------------------------
# Intro text: less jokey, more actual world-building. Image files are replaced
# separately with generated art.
p='core/src/main/java/com/enhort/gnomes/intro/IntroScreen.java'
s=read(p)
s=sub_once(s,r'    private static final String\[] TITLE = \{.*?    \};',
'''    private static final String[] TITLE = {
            "ПОД КАМНЕМ", "НАША РАБОТА", "ТО, ЧТО ЖИВЁТ ГЛУБЖЕ"
    };''','intro titles')
s=sub_once(s,r'    private static final String\[] BODY = \{.*?    \};',
'''    private static final String[] BODY = {
            "Здесь наш дом. Камень держит тепло, сундуки хранят добычу, а каждый новый тоннель становится частью поселения.",
            "Мы режем породу, открываем жилы и уносим наверх всё ценное. Чем глубже шахта, тем богаче находки и тяжелее работа.",
            "В глубине мы не одни. Бесы идут за золотом, демоны за гномами, а древние существа просыпаются вместе с самой горой."
    };''','intro body')
s=s.replace('yy += d.textSize * 1.55f;','yy += d.textSize * 3.10f;')
write(p,s)

# -----------------------------------------------------------------------------
# Menu: remove redundant Save button, make difficulty labels simple, real
# bestiary portraits, music controls and replay-intro control.
p='core/src/main/java/com/enhort/gnomes/menu/MenuScreen.java'
s=read(p)
s=s.replace('private final Box[] main=new Box[7];','private final Box[] main=new Box[6];')
s=replace_once(s,'    private final Box soundToggle=new Box(),vibrationToggle=new Box(),volumeDown=new Box(),volumeUp=new Box(),cheatG=new Box();',
'''    private final Box soundToggle=new Box(),vibrationToggle=new Box(),volumeDown=new Box(),volumeUp=new Box(),cheatG=new Box();
    private final Box musicToggle=new Box(),musicDown=new Box(),musicUp=new Box(),introReplay=new Box();''','menu setting boxes')
# settings card gets more vertical room
s=s.replace('float cardBottom=Math.min(back.t-18f*ui,cardTop+190f*ui);','float cardBottom=Math.min(back.t-18f*ui,cardTop+282f*ui);')
old='''        float rowR=infoCard.r-14f*ui;
        soundToggle.set(rowR-104f*ui,infoCard.t+16f*ui,rowR,infoCard.t+50f*ui);
        vibrationToggle.set(rowR-104f*ui,infoCard.t+58f*ui,rowR,infoCard.t+92f*ui);
        volumeDown.set(rowR-142f*ui,infoCard.t+100f*ui,rowR-100f*ui,infoCard.t+134f*ui);
        volumeUp.set(rowR-42f*ui,infoCard.t+100f*ui,rowR,infoCard.t+134f*ui);'''
new='''        float rowR=infoCard.r-14f*ui;
        musicToggle.set(rowR-104f*ui,infoCard.t+12f*ui,rowR,infoCard.t+44f*ui);
        musicDown.set(rowR-142f*ui,infoCard.t+50f*ui,rowR-100f*ui,infoCard.t+82f*ui);
        musicUp.set(rowR-42f*ui,infoCard.t+50f*ui,rowR,infoCard.t+82f*ui);
        soundToggle.set(rowR-104f*ui,infoCard.t+94f*ui,rowR,infoCard.t+126f*ui);
        volumeDown.set(rowR-142f*ui,infoCard.t+132f*ui,rowR-100f*ui,infoCard.t+164f*ui);
        volumeUp.set(rowR-42f*ui,infoCard.t+132f*ui,rowR,infoCard.t+164f*ui);
        vibrationToggle.set(rowR-104f*ui,infoCard.t+176f*ui,rowR,infoCard.t+208f*ui);
        introReplay.set(infoCard.l+14f*ui,infoCard.t+222f*ui,infoCard.r-14f*ui,infoCard.t+260f*ui);'''
s=replace_once(s,old,new,'menu settings layout')

old='''        button(d,main[0],"ИГРАТЬ",true,UiTheme.GOLD,false,1f);
        button(d,main[1],"ПРОДОЛЖИТЬ",game.saves.anySave(),UiTheme.GREEN,false,.94f);
        button(d,main[2],"СОХРАНЕНИЯ",true,UiTheme.STEEL,false,.92f);
        button(d,main[3],"НАСТРОЙКИ",true,UiTheme.STEEL,false,.92f);
        button(d,main[4],"БЕСТИАРИЙ",true,UiTheme.COPPER,false,.92f);
        button(d,main[5],"ОБ ИГРЕ",true,UiTheme.COPPER,false,.92f);
        button(d,main[6],"ВЫХОД",true,UiTheme.RED,false,.92f);'''
new='''        button(d,main[0],"ИГРАТЬ",true,UiTheme.GOLD,false,1f);
        button(d,main[1],"ПРОДОЛЖИТЬ",game.saves.anySave(),UiTheme.GREEN,false,.94f);
        button(d,main[2],"НАСТРОЙКИ",true,UiTheme.STEEL,false,.92f);
        button(d,main[3],"БЕСТИАРИЙ",true,UiTheme.COPPER,false,.92f);
        button(d,main[4],"ОБ ИГРЕ",true,UiTheme.COPPER,false,.92f);
        button(d,main[5],"ВЫХОД",true,UiTheme.RED,false,.92f);'''
s=replace_once(s,old,new,'main menu buttons')

s=sub_once(s,r'    private void difficulty\(Draw d\)\{.*?\n    \}\n\n    private void bestiary',
'''    private void difficulty(Draw d){
        heading(d,"СЛОЖНОСТЬ ЭКСПЕДИЦИИ");
        d.align=Draw.Align.CENTER;d.textSize=7.8f*ui;d.setColor(0xFF9EAAAF);
        d.text("Выберите, насколько суровой будет экспедиция.",width/2,128f*ui);
        d.align=Draw.Align.LEFT;
        button(d,difficultyButtons[0],"ЛЁГКАЯ",true,UiTheme.GREEN,false,.90f);
        button(d,difficultyButtons[1],"СРЕДНЯЯ",true,UiTheme.GOLD,false,.90f);
        button(d,difficultyButtons[2],"СЛОЖНАЯ",true,UiTheme.COPPER,false,.90f);
        button(d,difficultyButtons[3],"ХАРДКОР",true,UiTheme.RED,false,.90f);
        button(d,back,"НАЗАД",true,UiTheme.STEEL,false,.92f);
    }

    private void bestiary''','difficulty screen')

s=s.replace('drawEnemyStamp(d,e,x+14f*ui,y+20f*ui,known);','drawEnemyPortrait(d,e,x+14f*ui,y+22f*ui,known);')
s=s.replace('d.bold=true;d.textSize=7.6f*ui;','d.bold=true;d.textSize=6.8f*ui;',1)
s=s.replace('d.bold=false;d.textSize=6.2f*ui;','d.bold=false;d.textSize=5.4f*ui;',1)
pat=r'    private void drawEnemyStamp\(Draw d,EnemyType e,float x,float y,boolean known\)\{.*?\n    \}\n\n    private void settings'
repl=r'''    private void drawEnemyPortrait(Draw d,EnemyType e,float x,float y,boolean known){
        float s=12f*ui;
        d.save();d.translate(x,y);
        if(!known){d.setColor(0x55614A31);d.fillRoundRect(-s,-s,s,s,5f*ui);d.align=Draw.Align.CENTER;d.bold=true;d.textSize=8f*ui;d.setColor(0xFF7C6E5B);d.text("?",0,4f*ui);d.align=Draw.Align.LEFT;d.bold=false;d.restore();return;}
        d.setColor(0x44201916);d.fillOval(-s*.9f,s*.65f,s*.9f,s*.95f);
        switch(e){
            case IMP,IMP_KING -> {
                d.setColor(0xFF8E251F);d.fillOval(-s*.55f,-s*.25f,s*.55f,s*.72f);d.setColor(e.color);d.fillCircle(0,-s*.38f,s*.46f);
                d.pathReset();d.moveTo(-s*.28f,-s*.66f);d.lineTo(-s*.76f,-s*.98f);d.lineTo(-s*.48f,-s*.43f);d.closePath();d.fillPath();
                d.pathReset();d.moveTo(s*.28f,-s*.66f);d.lineTo(s*.76f,-s*.98f);d.lineTo(s*.48f,-s*.43f);d.closePath();d.fillPath();
                d.setColor(0xFFFFE46B);d.fillCircle(-s*.16f,-s*.42f,s*.055f);d.fillCircle(s*.16f,-s*.42f,s*.055f);
                if(e==EnemyType.IMP_KING)miniCrown(d,0,-s*.93f,s*.72f);
            }
            case DEMON,DEMON_KING -> {
                d.setColor(0xFF631B20);d.fillRoundRect(-s*.62f,-s*.05f,s*.62f,s*.70f,s*.18f);d.setColor(e.color);d.fillCircle(0,-s*.44f,s*.48f);
                d.setColor(0xFF2A1717);d.strokeWidth=s*.16f;d.line(-s*.24f,-s*.64f,-s*.74f,-s*.98f);d.line(s*.24f,-s*.64f,s*.74f,-s*.98f);
                d.setColor(0xFFFFB84E);d.fillCircle(-s*.17f,-s*.45f,s*.05f);d.fillCircle(s*.17f,-s*.45f,s*.05f);
                if(e==EnemyType.DEMON_KING)miniCrown(d,0,-s*.98f,s*.80f);
            }
            case SUCCUBUS -> {
                d.setColor(0xFF6B2448);d.pathReset();d.moveTo(-s*.16f,-s*.10f);d.lineTo(-s*.90f,-s*.62f);d.lineTo(-s*.66f,s*.35f);d.closePath();d.fillPath();d.pathReset();d.moveTo(s*.16f,-s*.10f);d.lineTo(s*.90f,-s*.62f);d.lineTo(s*.66f,s*.35f);d.closePath();d.fillPath();
                d.setColor(0xFFC94979);d.fillOval(-s*.38f,-s*.08f,s*.38f,s*.70f);d.setColor(0xFFE7B3A3);d.fillCircle(0,-s*.42f,s*.36f);d.setColor(0xFF2C1823);d.fillOval(-s*.38f,-s*.76f,s*.38f,-s*.34f);
            }
            case GHOST -> {
                d.setColor(0x774AD7E3);d.fillCircle(0,-s*.34f,s*.40f);d.pathReset();d.moveTo(-s*.48f,-s*.12f);d.quadTo(-s*.65f,s*.42f,-s*.34f,s*.78f);d.lineTo(-s*.06f,s*.54f);d.lineTo(s*.18f,s*.78f);d.lineTo(s*.48f,s*.42f);d.quadTo(s*.62f,s*.05f,s*.42f,-s*.12f);d.closePath();d.fillPath();d.setColor(0xFFE7FFFF);d.fillCircle(-s*.13f,-s*.39f,s*.04f);d.fillCircle(s*.13f,-s*.39f,s*.04f);
            }
            case STONE_GOLEM,WATER_GOLEM,FIRE_GOLEM,ELEMENTAL_KING -> {
                int base=e==EnemyType.STONE_GOLEM?0xFF625D55:e==EnemyType.WATER_GOLEM?0xFF397EA8:e==EnemyType.FIRE_GOLEM?0xFFB74620:0xFF625AA9;
                d.setColor(base);d.fillRoundRect(-s*.57f,-s*.16f,s*.57f,s*.60f,s*.13f);d.fillCircle(0,-s*.47f,s*.42f);d.fillCircle(-s*.64f,s*.08f,s*.22f);d.fillCircle(s*.64f,s*.08f,s*.22f);
                d.setColor(e==EnemyType.FIRE_GOLEM?0xFFFFD34E:0xFFBFF5FF);d.fillCircle(-s*.14f,-s*.48f,s*.045f);d.fillCircle(s*.14f,-s*.48f,s*.045f);
                if(e==EnemyType.WATER_GOLEM){d.setColor(0x885BD3F2);d.strokeWidth=1.3f*ui;d.line(-s*.45f,s*.40f,s*.45f,s*.32f);}
                if(e==EnemyType.FIRE_GOLEM){d.setColor(0x88FF6A22);d.fillCircle(0,s*.20f,s*.18f);}
                if(e==EnemyType.ELEMENTAL_KING)miniCrown(d,0,-s*.96f,s*.85f);
            }
        }
        d.restore();
    }
    private void miniCrown(Draw d,float x,float y,float w){d.setColor(0xFFD9A62E);d.pathReset();d.moveTo(x-w*.45f,y+w*.22f);d.lineTo(x-w*.38f,y-w*.18f);d.lineTo(x-w*.13f,y+w*.02f);d.lineTo(x,y-w*.30f);d.lineTo(x+w*.15f,y+w*.02f);d.lineTo(x+w*.40f,y-w*.18f);d.lineTo(x+w*.45f,y+w*.22f);d.closePath();d.fillPath();}

    private void settings'''
s=sub_once(s,pat,repl,'bestiary portraits')

pat=r'    private void settings\(Draw d\)\{.*?\n    \}\n\n    private void about'
repl=r'''    private void settings(Draw d){
        heading(d,"НАСТРОЙКИ");card(d,infoCard);float lx=infoCard.l+16f*ui;
        d.bold=true;d.textSize=7.5f*ui;d.setColor(0xFFE7E2D7);
        d.text("МУЗЫКА",lx,infoCard.t+34f*ui);d.text("ГРОМКОСТЬ МУЗЫКИ",lx,infoCard.t+74f*ui);
        d.text("ЗВУКИ",lx,infoCard.t+116f*ui);d.text("ГРОМКОСТЬ SFX",lx,infoCard.t+156f*ui);d.text("ВИБРАЦИЯ",lx,infoCard.t+198f*ui);d.bold=false;
        button(d,musicToggle,game.settings.musicEnabled?"ВКЛ":"ВЫКЛ",true,game.settings.musicEnabled?UiTheme.GREEN:UiTheme.STEEL,false,.75f);
        button(d,musicDown,"−",game.settings.musicVolume>.01f,UiTheme.COPPER,false,1f);button(d,musicUp,"+",game.settings.musicVolume<.99f,UiTheme.COPPER,false,1f);
        button(d,soundToggle,game.settings.soundEnabled?"ВКЛ":"ВЫКЛ",true,game.settings.soundEnabled?UiTheme.GREEN:UiTheme.STEEL,false,.75f);
        button(d,volumeDown,"−",game.settings.soundVolume>.01f,UiTheme.COPPER,false,1f);button(d,volumeUp,"+",game.settings.soundVolume<.99f,UiTheme.COPPER,false,1f);
        button(d,vibrationToggle,game.settings.vibrationEnabled?"ВКЛ":"ВЫКЛ",true,game.settings.vibrationEnabled?UiTheme.GREEN:UiTheme.STEEL,false,.75f);
        d.align=Draw.Align.CENTER;d.bold=true;d.textSize=6.7f*ui;d.setColor(UiTheme.GOLD);d.text(Math.round(game.settings.musicVolume*100)+"%",(musicDown.r+musicUp.l)/2f,musicDown.cy()+3f*ui);d.text(Math.round(game.settings.soundVolume*100)+"%",(volumeDown.r+volumeUp.l)/2f,volumeDown.cy()+3f*ui);d.align=Draw.Align.LEFT;d.bold=false;
        button(d,introReplay,"ПРОИГРАТЬ ВСТУПЛЕНИЕ",true,UiTheme.GOLD,false,.72f);
        button(d,back,"НАЗАД",true,UiTheme.STEEL,false,.92f);
    }

    private void about'''
s=sub_once(s,pat,repl,'settings screen')

# main tap indexes
old='''            if(main[0].hit(x,y)||main[2].hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.5f);mode=Mode.SLOTS;return;}
            if(main[1].hit(x,y)&&game.saves.anySave()){game.audio.play(GameAudio.Sfx.UI,.5f);game.playSlot(game.saves.lastSlot());return;}
            if(main[3].hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.5f);mode=Mode.SETTINGS;return;}
            if(main[4].hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.5f);mode=Mode.BESTIARY;return;}
            if(main[5].hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.5f);mode=Mode.ABOUT;return;}
            if(main[6].hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.5f);Gdx.app.exit();return;}'''
new='''            if(main[0].hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.5f);mode=Mode.SLOTS;return;}
            if(main[1].hit(x,y)&&game.saves.anySave()){game.audio.play(GameAudio.Sfx.UI,.5f);game.playSlot(game.saves.lastSlot());return;}
            if(main[2].hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.5f);mode=Mode.SETTINGS;return;}
            if(main[3].hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.5f);mode=Mode.BESTIARY;return;}
            if(main[4].hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.5f);mode=Mode.ABOUT;return;}
            if(main[5].hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.5f);Gdx.app.exit();return;}'''
s=replace_once(s,old,new,'main tap indexes')
old='''        }else if(mode==Mode.SETTINGS){
            if(soundToggle.hit(x,y)){game.settings.toggleSound();game.audio.refreshMusic();if(game.settings.soundEnabled)game.audio.play(GameAudio.Sfx.UI,.75f);return;}
            if(vibrationToggle.hit(x,y)){game.settings.toggleVibration();if(game.settings.vibrationEnabled)game.audio.vibrate(45);game.audio.play(GameAudio.Sfx.UI,.5f);return;}
            if(volumeDown.hit(x,y)){game.settings.setSoundVolume(game.settings.soundVolume-.10f);game.audio.refreshMusic();game.audio.play(GameAudio.Sfx.UI,.6f);return;}
            if(volumeUp.hit(x,y)){game.settings.setSoundVolume(game.settings.soundVolume+.10f);game.audio.refreshMusic();game.audio.play(GameAudio.Sfx.UI,.6f);return;}
            if(back.hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.4f);mode=Mode.MAIN;}'''
new='''        }else if(mode==Mode.SETTINGS){
            if(musicToggle.hit(x,y)){game.settings.toggleMusic();game.audio.refreshMusic();return;}
            if(musicDown.hit(x,y)){game.settings.setMusicVolume(game.settings.musicVolume-.10f);game.audio.refreshMusic();return;}
            if(musicUp.hit(x,y)){game.settings.setMusicVolume(game.settings.musicVolume+.10f);game.audio.refreshMusic();return;}
            if(soundToggle.hit(x,y)){game.settings.toggleSound();if(game.settings.soundEnabled)game.audio.play(GameAudio.Sfx.UI,.75f);return;}
            if(vibrationToggle.hit(x,y)){game.settings.toggleVibration();if(game.settings.vibrationEnabled)game.audio.vibrate(45);game.audio.play(GameAudio.Sfx.UI,.5f);return;}
            if(volumeDown.hit(x,y)){game.settings.setSoundVolume(game.settings.soundVolume-.10f);game.audio.play(GameAudio.Sfx.UI,.6f);return;}
            if(volumeUp.hit(x,y)){game.settings.setSoundVolume(game.settings.soundVolume+.10f);game.audio.play(GameAudio.Sfx.UI,.6f);return;}
            if(introReplay.hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.6f);game.openIntro();return;}
            if(back.hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.4f);mode=Mode.MAIN;}'''
s=replace_once(s,old,new,'settings taps')
s=s.replace('case 4->"без перен."','case 4->"хардкор"')
write(p,s)

# -----------------------------------------------------------------------------
# Cave mechanics and art.
p='core/src/main/java/com/enhort/gnomes/game/CaveScreen.java'
s=read(p)
s=s.replace('private enum ObjectiveType { CLEAR_VEINS, GUARDIAN, DEMON_PURGE, BOSS_HUNT, TREASURE }',
            'private enum ObjectiveType { ASCEND_GNOME, CLEAR_VEINS, GUARDIAN, DEMON_PURGE, BOSS_HUNT, TREASURE }')

# Spread miners across viable veins instead of dog-piling the nearest stone.
old='''    private Vein chooseVein(Worker w){Vein best=null;float bd=Float.MAX_VALUE;for(Vein v:veins){if(v.dead||map.isBlocked(v.cell))continue;float d=dist2(w.x,w.y,cx(map.col(v.cell)),cy(map.row(v.cell)));if(d<bd){bd=d;best=v;}}return best;}'''
new='''    private Vein chooseVein(Worker w){
        Vein best=null;float bestScore=Float.MAX_VALUE,unit=Math.min(cellW,cellH);float crowdPenalty=unit*unit*3.8f;
        for(Vein v:veins){
            if(v.dead||map.isBlocked(v.cell))continue;
            int assigned=0;for(Worker other:workers)if(other!=w&&other.vein==v&&!v.dead)assigned++;
            int[] route=map.pathAvoiding(cellFor(w.x,w.y),v.cell,dangerMask(w));if(route.length==0)continue;
            float d=dist2(w.x,w.y,cx(map.col(v.cell)),cy(map.row(v.cell)));
            float score=d+assigned*crowdPenalty+(Math.floorMod(w.visualId+v.seed,11)*.015f*crowdPenalty);
            if(score<bestScore){bestScore=score;best=v;}
        }return best;
    }'''
s=replace_once(s,old,new,'spread vein targeting')

# A collapse gets workers before any player priority, so it can never permanently
# seal the only remaining vein.
s=s.replace('int cleanupLeft=cleanup==null?0:Math.max(1,Math.min(8,(workers.size()+17)/18));',
            'int cleanupLeft=cleanup==null?0:Math.max(2,Math.min(14,(workers.size()+5)/6));')
anchor='''            if(w.charm>0){fightAlly(w,dt);continue;}

            if(priorityKind==PriorityKind.VEIN'''
insert='''            if(w.charm>0){fightAlly(w,dt);continue;}
            if(cleanupLeft>0&&cleanup!=null&&!cleanup.cleared){cleanupLeft--;w.vein=null;w.mob=null;clearCollapse(w,cleanup,dt);continue;}

            if(priorityKind==PriorityKind.VEIN'''
s=replace_once(s,anchor,insert,'collapse priority')
# remove old later cleanup once
old='''            if(cleanupLeft>0&&cleanup!=null&&!cleanup.cleared){cleanupLeft--;w.vein=null;clearCollapse(w,cleanup,dt);continue;}
'''
if old in s: s=s.replace(old,'',1)

# Wet green corridors / animated water seams.
s=s.replace('drawRockMass(d);drawTunnels(d);drawCaveDecor(d);','drawRockMass(d);drawTunnels(d);drawWetCorridors(d);drawCaveDecor(d);')
anchor='''    private void drawTunnelEdges(Draw d){for(int r=0;r<map.rows;r++)for(int c=0;c<map.cols;c++){if(map.connected(c,r,CaveMap.E))d.line(cx(c),cy(r),cx(c+1),cy(r));if(map.connected(c,r,CaveMap.S))d.line(cx(c),cy(r),cx(c),cy(r+1));}}
'''
extra='''    private void drawTunnelEdges(Draw d){for(int r=0;r<map.rows;r++)for(int c=0;c<map.cols;c++){if(map.connected(c,r,CaveMap.E))d.line(cx(c),cy(r),cx(c+1),cy(r));if(map.connected(c,r,CaveMap.S))d.line(cx(c),cy(r),cx(c),cy(r+1));}}
    private void drawWetCorridors(Draw d){
        float base=Math.min(cellW,cellH);int wet=0;
        for(int r=0;r<map.rows;r++)for(int c=0;c<map.cols;c++){
            int cell=map.index(c,r);long h=map.seed^cell*0x94D049BB133111EBL;if(hash01(h)>.13f)continue;
            int dir=map.connected(c,r,CaveMap.E)?CaveMap.E:(map.connected(c,r,CaveMap.S)?CaveMap.S:0);if(dir==0)continue;
            float x1=cx(c),y1=cy(r),x2=cx(c+CaveMap.dx(dir)),y2=cy(r+CaveMap.dy(dir));
            d.setColor(0xAA244B35);d.strokeWidth=base*.20f;d.line(x1,y1,x2,y2);
            d.setColor(0x884E9A65);d.strokeWidth=base*.11f;d.line(x1,y1,x2,y2);
            d.setColor(0xAA3CA9B5);d.strokeWidth=base*.045f;d.line(x1,y1+2f*ui*(float)Math.sin(elapsed*2.4f+cell),x2,y2+2f*ui*(float)Math.sin(elapsed*2.4f+cell+1.7f));
            for(int i=0;i<4;i++){float q=(i*.27f+elapsed*(.08f+.02f*(cell%3)))%1f;float x=x1+(x2-x1)*q,y=y1+(y2-y1)*q+(float)Math.sin(elapsed*4f+i+cell)*2f*ui;d.setColor(0x665BD5C2);d.fillCircle(x,y,(1.1f+i%2*.7f)*ui);}wet++;if(wet>10)return;
        }
    }
'''
s=replace_once(s,anchor,extra,'wet corridors')

# Organic fog-of-war blobs instead of flat/rectangular darkness.
pat=r'    private void drawDarkZones\(Draw d\)\{.*?\n    private void drawPortal'
repl=r'''    private void drawDarkZones(Draw d){
        for(int r=0;r<map.rows;r++)for(int c=0;c<map.cols;c++){
            int cell=map.index(c,r);if(!isDarkCell(cell))continue;float rr=Math.min(cellW,cellH)*.56f,x=cx(c),y=cy(r);
            for(int i=0;i<6;i++){float a=i*1.0472f+hash01(map.seed+cell*31L)*2f,ox=(float)Math.cos(a)*rr*.24f,oy=(float)Math.sin(a)*rr*.20f,rad=rr*(.52f+.12f*hash01(cell*97L+i));d.setColor(i<2?0x76060708:0x54060708);d.fillCircle(x+ox,y+oy,rad);}
            d.setColor(0x220D1113);d.strokeWidth=2f*ui;d.strokeCircle(x,y,rr*.88f);
        }
        for(Worker w:workers)if(isDarkCell(cellFor(w.x,w.y))){float rr=(20f+w.tier.ordinal()*2f)*ui;d.setColor(0x145CB7D4);d.fillCircle(w.x,w.y,rr);d.setColor(0x20FFD170);d.fillCircle(w.x,w.y,rr*.42f);}
    }
    private void drawPortal'''
s=sub_once(s,pat,repl,'fog blobs')

# Better water: translucent stream, moving foam, green algae edge.
pat=r'    private void drawFloodHazard\(Draw d,CaveHazard h,float warning\)\{.*?\n    \}\n\n    private void drawFx'
repl=r'''    private void drawFloodHazard(Draw d,CaveHazard h,float warning){
        float t=Math.max(0,h.age-1.25f),dir=(h.cell&1)==0?1f:-1f,width=h.r*1.16f;
        if(h.age<1.25f){d.setColor(alpha(0xFF73D1D8,.22f+.42f*warning));d.strokeWidth=(2f+warning*3f)*ui;for(int i=0;i<4;i++){float yy=h.y+(i-1.5f)*h.r*.12f;d.line(h.x-width*.72f,yy,h.x+width*.72f,yy+(float)Math.sin(h.age*10+i)*h.r*.08f);}return;}
        d.setColor(0x66397668);d.strokeWidth=h.r*.46f;d.line(h.x-width,h.y,h.x+width,h.y);
        d.setColor(0x88439EAF);d.strokeWidth=h.r*.34f;d.line(h.x-width,h.y,h.x+width,h.y);
        d.setColor(0xAA68D0DB);d.strokeWidth=h.r*.10f;
        for(int j=0;j<3;j++){float yy=h.y+(j-1)*h.r*.12f;for(int i=0;i<10;i++){float x1=h.x-width+i*(width*2/10f),x2=h.x-width+(i+1)*(width*2/10f),w1=(float)Math.sin(i*1.3f+t*7.5f*dir+j)*h.r*.055f,w2=(float)Math.sin((i+1)*1.3f+t*7.5f*dir+j)*h.r*.055f;d.line(x1,yy+w1,x2,yy+w2);}}
        for(int i=0;i<12;i++){float q=(i*.113f+t*(.32f+.035f*(i%3)))%1f,x=h.x-width+q*width*2,y=h.y-h.r*.18f+(float)Math.sin(i*2.4f+t*5f)*h.r*.13f;d.setColor(i%3==0?0xAAE9FFFF:0x665ED5E8);d.fillCircle(x,y,(1.2f+i%3*.65f)*ui);}
        d.setColor(0x664E9C5A);for(int i=0;i<6;i++){float q=(i*.19f+t*.05f)%1f,x=h.x-width+q*width*2;d.fillOval(x-4f*ui,h.y+h.r*.20f,x+5f*ui,h.y+h.r*.33f);}
    }

    private void drawFx'''
s=sub_once(s,pat,repl,'water animation')

# Laser excavator: visible dwarf operating a compact tracked mining laser.
pat=r'    private void drawExcavator\(Draw d,Worker w,float s\)\{.*?\n\n    private void drawIron'
repl=r'''    private void drawExcavator(Draw d,Worker w,float s){
        float dir=facing(w),pulse=.5f+.5f*(float)Math.sin(elapsed*18f+w.phase);float tx=w.vein!=null?w.vein.x:(w.mob!=null?w.mob.x:w.x+dir*s),ty=w.vein!=null?w.vein.y:(w.mob!=null?w.mob.y:w.y);
        if((w.action==WorkerAction.MINE||w.action==WorkerAction.FIGHT)&&w.swing>0){d.setColor(0x3346E7FF);d.strokeWidth=5f*ui;d.line(w.x+dir*s*.38f,w.y-s*.10f,tx,ty);d.setColor(0xFF8AF3FF);d.strokeWidth=(1.2f+pulse)*ui;d.line(w.x+dir*s*.38f,w.y-s*.10f,tx,ty);d.fillCircle(tx,ty,(2.2f+pulse*2f)*ui);}
        d.save();d.translate(w.x,w.y);d.scale(dir,1);d.setColor(0x55000000);d.fillOval(-s*.62f,s*.44f,s*.62f,s*.62f);
        d.setColor(0xFF2D3235);d.fillRoundRect(-s*.58f,s*.18f,s*.52f,s*.48f,s*.10f);d.setColor(0xFF666F74);for(int i=0;i<5;i++)d.fillCircle(-s*.42f+i*s*.20f,s*.43f,s*.085f);
        d.setColor(0xFF4E6A78);d.fillRoundRect(-s*.31f,-s*.02f,s*.23f,s*.26f,s*.07f);
        d.setColor(0xFFE2B080);d.fillCircle(-s*.05f,-s*.30f,s*.18f);d.setColor(0xFFF1EEE3);d.pathReset();d.moveTo(-s*.16f,-s*.20f);d.quadTo(-s*.02f,s*.02f,s*.16f,-s*.18f);d.lineTo(s*.08f,s*.05f);d.lineTo(-s*.12f,s*.03f);d.closePath();d.fillPath();d.setColor(w.tier.color);d.pathReset();d.moveTo(-s*.22f,-s*.46f);d.lineTo(s*.02f,-s*.66f);d.lineTo(s*.20f,-s*.42f);d.closePath();d.fillPath();
        d.setColor(0xFF89979E);d.strokeWidth=s*.10f;d.line(s*.16f,s*.05f,s*.48f,-s*.12f);d.setColor(0xFFB8EAF5);d.fillRoundRect(s*.37f,-s*.22f,s*.58f,-s*.03f,s*.04f);d.setColor(0xFF62ECFF);d.fillCircle(s*.57f,-s*.13f,s*.06f);d.restore();if(w.hasCargo())drawSack(d,w,s);
    }

    private void drawIron'''
s=sub_once(s,pat,repl,'laser excavator')

# Laser golem: compact and shoots from one eye, no pickaxe/punch pantomime.
pat=r'    private void drawIron\(Draw d,Worker w,float s\)\{.*?\n    private float facing'
repl=r'''    private void drawIron(Draw d,Worker w,float s){
        float stride=(float)Math.sin(w.walkCycle+w.phase),dir=facing(w),tx=w.vein!=null?w.vein.x:(w.mob!=null?w.mob.x:w.x+dir*s),ty=w.vein!=null?w.vein.y:(w.mob!=null?w.mob.y:w.y),beam=(w.action==WorkerAction.MINE||w.action==WorkerAction.FIGHT)&&w.swing>0?(float)Math.sin(strikeProgress(w,w.action==WorkerAction.FIGHT?.46f:.58f)*Math.PI):0;
        if(beam>.12f){d.setColor(0x445DEBFF);d.strokeWidth=5f*ui*beam;d.line(w.x+dir*s*.10f,w.y-s*.38f,tx,ty);d.setColor(0xFFD3FBFF);d.strokeWidth=(1.1f+beam)*ui;d.line(w.x+dir*s*.10f,w.y-s*.38f,tx,ty);d.setColor(0xFF67E9FF);d.fillCircle(tx,ty,(2f+3f*beam)*ui);}
        d.save();d.translate(w.x,w.y-Math.abs(stride)*.8f*ui);d.scale(dir,1);d.setColor(0x66000000);d.fillOval(-s*.50f,s*.48f,s*.50f,s*.65f);d.setColor(0xFF56636A);d.fillRoundRect(-s*.36f,-s*.02f,s*.36f,s*.44f,s*.09f);d.setColor(0xFF9EACB5);d.fillCircle(0,-s*.34f,s*.29f);d.setColor(0xFF232A2E);d.fillRect(-s*.21f,-s*.43f,s*.21f,-s*.32f);d.setColor(beam>.12f?0xFFE6FFFF:0xFF67E9FF);d.fillCircle(s*.10f,-s*.38f,s*.055f);d.setColor(0xFF3E515A);d.fillCircle(-s*.10f,-s*.38f,s*.035f);d.setColor(0xFFCAD4D9);d.pathReset();d.moveTo(-s*.20f,-s*.18f);d.lineTo(0,s*.20f);d.lineTo(s*.20f,-s*.18f);d.lineTo(s*.08f,s*.14f);d.lineTo(-s*.08f,s*.14f);d.closePath();d.fillPath();d.setColor(0xFF69757B);d.strokeWidth=s*.15f;d.line(-s*.30f,s*.04f,-s*.52f,s*.34f);d.line(s*.30f,s*.04f,s*.52f,s*.34f);d.line(-s*.15f,s*.42f,-s*.22f+stride*s*.04f,s*.66f);d.line(s*.15f,s*.42f,s*.22f-stride*s*.04f,s*.66f);d.restore();if(w.hasCargo())drawSack(d,w,s);
    }
    private float facing'''
s=sub_once(s,pat,repl,'laser golem')

# Guardian animation: actual axe swing with head/blade, not a stick at range.
pat=r'    private void drawGuardian\(Draw d,float x,float y,float s\)\{.*?\n    \}\n    private void drawGuardianHealth'
repl=r'''    private void drawGuardian(Draw d,float x,float y,float s){
        float bob=(float)Math.sin(elapsed*3.1f)*1.0f*ui,attack=guardianAttackAnim>0?(float)Math.sin((.34f-guardianAttackAnim)/.34f*Math.PI):0,dir=guardianTarget!=null&&guardianTarget.x<x?-1f:1f,appear=guardianSpawnAnim>0?appearScale(guardianSpawnAnim,.70f):1f;
        if(guardianSpawnAnim>0)drawArrivalRing(d,x,y,s*.80f,guardianSpawnAnim,.70f,0xFF70D7FF);d.save();d.translate(x,y+bob);d.scale(dir*appear,appear);
        d.setColor(0x66000000);d.fillOval(-s*.38f,s*.48f,s*.40f,s*.62f);d.setColor(0xFF44352B);d.strokeWidth=s*.13f;d.line(-s*.12f,s*.28f,-s*.20f,s*.55f);d.line(s*.12f,s*.28f,s*.20f,s*.55f);
        d.setColor(guardianHitFlash>0?0xFFF0C080:0xFF496A78);d.fillRoundRect(-s*.30f,-s*.02f,s*.30f,s*.35f,s*.08f);d.setColor(0xFFE2B080);d.fillCircle(0,-s*.28f,s*.23f);d.setColor(0xFFEEE9DB);d.pathReset();d.moveTo(-s*.20f,-s*.16f);d.quadTo(0,s*.16f,s*.20f,-s*.16f);d.lineTo(s*.10f,s*.10f);d.lineTo(-s*.10f,s*.10f);d.closePath();d.fillPath();d.setColor(0xFF70828C);d.fillRoundRect(-s*.25f,-s*.50f,s*.25f,-s*.37f,s*.04f);
        d.save();d.translate(s*.24f,-s*.02f);d.rotate(-74f+attack*118f);d.setColor(0xFF6B4329);d.strokeWidth=s*.085f;d.line(0,0,s*.70f,0);d.setColor(0xFFB9C3C9);d.pathReset();d.moveTo(s*.54f,-s*.27f);d.lineTo(s*.82f,-s*.20f);d.lineTo(s*.84f,s*.20f);d.lineTo(s*.55f,s*.30f);d.lineTo(s*.63f,0);d.closePath();d.fillPath();d.setColor(0xFFE4EDF1);d.strokeWidth=1.2f*ui;d.line(s*.58f,-s*.20f,s*.76f,-s*.15f);d.restore();d.restore();
    }
    private void drawGuardianHealth'''
s=sub_once(s,pat,repl,'guardian axe')

# Level one is always the merge tutorial / advanced gnome unlock.
s=s.replace('if(state.depth==1)objectiveType=ObjectiveType.CLEAR_VEINS;', 'if(state.depth==1)objectiveType=ObjectiveType.ASCEND_GNOME;')
s=s.replace('return switch(objectiveType){case CLEAR_VEINS->noLivingVeins();case GUARDIAN->state.guardianLevel>=objectiveTarget&&!guardianDead;',
            'return switch(objectiveType){case ASCEND_GNOME->state.tierCounts[GnomeTier.VETERAN.ordinal()]>=1;case CLEAR_VEINS->noLivingVeins();case GUARDIAN->state.guardianLevel>=objectiveTarget&&!guardianDead;')
s=s.replace('return switch(objectiveType){case CLEAR_VEINS->"ЦЕЛЬ: ОЧИСТИТЬ ЖИЛЫ";',
            'return switch(objectiveType){case ASCEND_GNOME->"ЦЕЛЬ: ОТКРЫТЬ ПРОДВИНУТОГО ГНОМА";case CLEAR_VEINS->"ЦЕЛЬ: ОЧИСТИТЬ ЖИЛЫ";')
write(p,s)

# -----------------------------------------------------------------------------
# A simple but actual musical loop: low cave drone + plucked pentatonic melody.
# WAV keeps runtime dependencies zero.
out=ROOT/'assets/music/mine_loop_v2.wav';out.parent.mkdir(parents=True,exist_ok=True)
sr=16000;duration=24.0;n=int(sr*duration)
notes=[110.0,130.81,146.83,164.81,196.0,164.81,146.83,130.81,
       110.0,146.83,164.81,220.0,196.0,164.81,130.81,98.0]
with wave.open(str(out),'wb') as wf:
    wf.setnchannels(1);wf.setsampwidth(2);wf.setframerate(sr)
    frames=bytearray()
    for i in range(n):
        t=i/sr;step=int(t/1.5)%len(notes);local=t%1.5;f=notes[step]
        env=math.exp(-local*2.3)
        pluck=(math.sin(2*math.pi*f*t)+.34*math.sin(2*math.pi*f*2*t)+.14*math.sin(2*math.pi*f*3*t))*env
        drone=.42*math.sin(2*math.pi*55*t)+.18*math.sin(2*math.pi*82.41*t)
        shimmer=.10*math.sin(2*math.pi*(f*4)*t)*math.exp(-local*4.0)
        # slow amplitude movement prevents the loop from sounding like a test tone
        v=(pluck*.30+drone*.22+shimmer*.18)*(.80+.20*math.sin(2*math.pi*t/12))
        sample=max(-1,min(1,v));frames+=struct.pack('<h',int(sample*32767))
    wf.writeframes(frames)

print('GNOMES v0.6 patch applied')
