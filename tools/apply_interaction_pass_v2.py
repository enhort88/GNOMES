from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
source = (ROOT / "tools" / "apply_interaction_pass.py").read_text()

# Reuse the already-audited gameplay/map/shop patch, but stop before its outdated menu section.
prefix = source.split("# ---------------- MenuScreen:", 1)[0]
exec(compile(prefix, "apply_interaction_pass.py", "exec"), globals(), globals())

path = "core/src/main/java/com/enhort/gnomes/menu/MenuScreen.java"
rep(path,
"import com.enhort.gnomes.GnomesGame;",
"import com.enhort.gnomes.GnomesGame;\nimport com.enhort.gnomes.GameAudio;")
rep(path,
"    private final Box yes=new Box(),no=new Box();\n    private final Box infoCard=new Box();",
"    private final Box yes=new Box(),no=new Box();\n    private final Box infoCard=new Box();\n    private final Box soundToggle=new Box(),vibrationToggle=new Box(),volumeDown=new Box(),volumeUp=new Box(),cheatG=new Box();")
rep(path,
"    private int pendingDelete=-1;",
"    private int pendingDelete=-1;\n    private int gTapCount;\n    private float cheatNotice;")
rep(path,
"        infoCard.set((width-cardW)/2f,cardTop,(width+cardW)/2f,cardBottom);\n    }",
"        infoCard.set((width-cardW)/2f,cardTop,(width+cardW)/2f,cardBottom);\n\n        float rowR=infoCard.r-14f*ui;\n        soundToggle.set(rowR-104f*ui,infoCard.t+16f*ui,rowR,infoCard.t+50f*ui);\n        vibrationToggle.set(rowR-104f*ui,infoCard.t+58f*ui,rowR,infoCard.t+92f*ui);\n        volumeDown.set(rowR-104f*ui,infoCard.t+100f*ui,rowR-58f*ui,infoCard.t+134f*ui);\n        volumeUp.set(rowR-46f*ui,infoCard.t+100f*ui,rowR,infoCard.t+134f*ui);\n        // GNOMES is centered at y=74. This box covers only the first G, not the whole title.\n        cheatG.set(width/2f-86f*ui,44f*ui,width/2f-45f*ui,96f*ui);\n    }")
rep(path,
"        elapsed+=Math.min(delta,.05f);",
"        elapsed+=Math.min(delta,.05f);\n        if(cheatNotice>0)cheatNotice-=Math.min(delta,.05f);")
old_main = '''    private void main(Draw d){
        heading(d,"DEEP MINE • ALPHA 0.3");
        button(d,main[0],"ИГРАТЬ",true,UiTheme.GOLD,false,1f);
        button(d,main[1],"ПРОДОЛЖИТЬ",game.saves.anySave(),UiTheme.GREEN,false,.94f);
        button(d,main[2],"СОХРАНЕНИЯ",true,UiTheme.STEEL,false,.92f);
        button(d,main[3],"НАСТРОЙКИ",true,UiTheme.STEEL,false,.92f);
        button(d,main[4],"ОБ ИГРЕ",true,UiTheme.COPPER,false,.92f);
        button(d,main[5],"ВЫХОД",true,UiTheme.RED,false,.92f);
    }'''
new_main = '''    private void main(Draw d){
        heading(d,"DEEP MINE • ALPHA 0.4");
        button(d,main[0],"ИГРАТЬ",true,UiTheme.GOLD,false,1f);
        button(d,main[1],"ПРОДОЛЖИТЬ",game.saves.anySave(),UiTheme.GREEN,false,.94f);
        button(d,main[2],"СОХРАНЕНИЯ",true,UiTheme.STEEL,false,.92f);
        button(d,main[3],"НАСТРОЙКИ",true,UiTheme.STEEL,false,.92f);
        button(d,main[4],"ОБ ИГРЕ",true,UiTheme.COPPER,false,.92f);
        button(d,main[5],"ВЫХОД",true,UiTheme.RED,false,.92f);
        if(game.settings.freeShop||cheatNotice>0){
            d.align=Draw.Align.CENTER;d.bold=true;d.textSize=7.5f*ui;
            d.setColor(game.settings.freeShop?0xFFFFC74A:0xFF8D979D);
            d.text(game.settings.freeShop?"TEST MODE • ВСЁ БЕСПЛАТНО":"TEST MODE ВЫКЛЮЧЕН",width/2,126f*ui);
            d.align=Draw.Align.LEFT;d.bold=false;
        }
    }'''
rep(path, old_main, new_main)
old_settings = '''    private void settings(Draw d){
        heading(d,"НАСТРОЙКИ");
        card(d,infoCard);
        d.align=Draw.Align.CENTER;
        d.bold=true;d.textSize=10.5f*ui;d.setColor(0xFFF0EEE7);d.text("ГРАФИКА",width/2,infoCard.t+36f*ui);
        d.bold=false;d.textSize=8.5f*ui;d.setColor(0xFFB9C2C7);d.text("Высокое качество эффектов",width/2,infoCard.t+67f*ui);
        d.text("Анимация шахты: включена",width/2,infoCard.t+92f*ui);
        d.setColor(0xFF879198);d.textSize=7.5f*ui;d.text("Звук и вибрация будут отдельными настройками.",width/2,infoCard.t+133f*ui);
        d.align=Draw.Align.LEFT;
        button(d,back,"НАЗАД",true,UiTheme.STEEL,false,.92f);
    }'''
new_settings = '''    private void settings(Draw d){
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
    }'''
rep(path, old_settings, new_settings)
old_tap = '''    private void tap(float x,float y){
        if(mode==Mode.MAIN){
            if(main[0].hit(x,y)||main[2].hit(x,y)){mode=Mode.SLOTS;return;}
            if(main[1].hit(x,y)&&game.saves.anySave()){game.playSlot(game.saves.lastSlot());return;}
            if(main[3].hit(x,y)){mode=Mode.SETTINGS;return;}
            if(main[4].hit(x,y)){mode=Mode.ABOUT;return;}
            if(main[5].hit(x,y)){Gdx.app.exit();return;}
        }else if(mode==Mode.SLOTS){
            for(int i=0;i<slots.length;i++){
                int slot=i+1;
                if(game.saves.exists(slot)&&slotDelete[i].hit(x,y)){pendingDelete=slot;mode=Mode.DELETE;return;}
                if(slots[i].hit(x,y)){
                    if(game.saves.exists(slot))game.playSlot(slot);else game.playNewSlot(slot);
                    return;
                }
            }
            if(back.hit(x,y))mode=Mode.MAIN;
        }else if(mode==Mode.SETTINGS||mode==Mode.ABOUT){
            if(back.hit(x,y))mode=Mode.MAIN;
        }else if(mode==Mode.DELETE){
            if(yes.hit(x,y)){game.saves.delete(pendingDelete);pendingDelete=-1;mode=Mode.SLOTS;}
            else if(no.hit(x,y)){pendingDelete=-1;mode=Mode.SLOTS;}
        }
    }'''
new_tap = '''    private void tap(float x,float y){
        if(mode==Mode.MAIN){
            if(cheatG.hit(x,y)){
                gTapCount++;game.audio.play(GameAudio.Sfx.UI,.25f);
                if(gTapCount>=10){gTapCount=0;game.settings.toggleFreeShop();game.syncCheats();cheatNotice=2.6f;game.audio.play(GameAudio.Sfx.COIN,.95f);game.audio.vibrate(70);}
                return;
            }
            if(main[0].hit(x,y)||main[2].hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.5f);mode=Mode.SLOTS;return;}
            if(main[1].hit(x,y)&&game.saves.anySave()){game.audio.play(GameAudio.Sfx.UI,.5f);game.playSlot(game.saves.lastSlot());return;}
            if(main[3].hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.5f);mode=Mode.SETTINGS;return;}
            if(main[4].hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.5f);mode=Mode.ABOUT;return;}
            if(main[5].hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.5f);Gdx.app.exit();return;}
        }else if(mode==Mode.SLOTS){
            for(int i=0;i<slots.length;i++){
                int slot=i+1;
                if(game.saves.exists(slot)&&slotDelete[i].hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.45f);pendingDelete=slot;mode=Mode.DELETE;return;}
                if(slots[i].hit(x,y)){
                    game.audio.play(GameAudio.Sfx.UI,.5f);
                    if(game.saves.exists(slot))game.playSlot(slot);else game.playNewSlot(slot);
                    return;
                }
            }
            if(back.hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.4f);mode=Mode.MAIN;}
        }else if(mode==Mode.SETTINGS){
            if(soundToggle.hit(x,y)){game.settings.toggleSound();if(game.settings.soundEnabled)game.audio.play(GameAudio.Sfx.UI,.75f);return;}
            if(vibrationToggle.hit(x,y)){game.settings.toggleVibration();if(game.settings.vibrationEnabled)game.audio.vibrate(45);game.audio.play(GameAudio.Sfx.UI,.5f);return;}
            if(volumeDown.hit(x,y)){game.settings.setSoundVolume(game.settings.soundVolume-.10f);game.audio.play(GameAudio.Sfx.UI,.6f);return;}
            if(volumeUp.hit(x,y)){game.settings.setSoundVolume(game.settings.soundVolume+.10f);game.audio.play(GameAudio.Sfx.UI,.6f);return;}
            if(back.hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.4f);mode=Mode.MAIN;}
        }else if(mode==Mode.ABOUT){
            if(back.hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.4f);mode=Mode.MAIN;}
        }else if(mode==Mode.DELETE){
            if(yes.hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.5f);game.saves.delete(pendingDelete);pendingDelete=-1;mode=Mode.SLOTS;}
            else if(no.hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.4f);pendingDelete=-1;mode=Mode.SLOTS;}
        }
    }'''
rep(path, old_tap, new_tap)

# Generate the same deterministic WAV assets from the tail of the original helper.
tail = source.split("# ---------------- Procedural SFX assets ----------------", 1)[1]
exec(compile(tail, "sfx_generation.py", "exec"), globals(), globals())
