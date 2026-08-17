from pathlib import Path


def repl(s, old, new, name):
    if old not in s:
        raise SystemExit(f'patch target not found: {name}')
    return s.replace(old, new, 1)

p = Path('core/src/main/java/com/enhort/gnomes/game/CaveScreen.java')
s = p.read_text()

# Track the actual finger position on the speed button so the feedback is local rather than a giant halo.
s = repl(s,
'''    private boolean speedHeld;
    private PriorityKind priorityKind=PriorityKind.NONE;''',
'''    private boolean speedHeld;
    private float speedTouchX,speedTouchY;
    private PriorityKind priorityKind=PriorityKind.NONE;''',
'speed touch fields')

s = repl(s,
'''                if(speed.hit(x,y)){speedHeld=true;game.audio.play(GameAudio.Sfx.UI,.55f);return true;}''',
'''                if(speed.hit(x,y)){speedHeld=true;speedTouchX=x;speedTouchY=y;game.audio.play(GameAudio.Sfx.UI,.55f);return true;}''',
'speed touch down')

s = repl(s,
'''            @Override public boolean touchDragged(int sx,int sy,int pointer){
                if(!speed.hit(sx,sy))speedHeld=false;''',
'''            @Override public boolean touchDragged(int sx,int sy,int pointer){
                if(speedHeld&&speed.hit(sx,sy)){speedTouchX=sx;speedTouchY=sy;}
                if(!speed.hit(sx,sy))speedHeld=false;''',
'speed drag tracking')

# When there are no mineable veins left, any remaining cargo becomes the highest worker priority.
s = repl(s,
'''    private void updateWorkers(float dt){
        int defendersLeft=state.guardianLevel>0?guardianDefenderQuota():workers.size();''',
'''    private void updateWorkers(float dt){
        boolean veinsExhausted=noLivingVeins();
        int defendersLeft=state.guardianLevel>0?guardianDefenderQuota():workers.size();''',
'exhausted vein state')

s = repl(s,
'''            if(w.charm>0){fightAlly(w,dt);continue;}

            // Direct player orders always win. No ambient rubble job is allowed to hijack them.''',
'''            if(w.charm>0){fightAlly(w,dt);continue;}

            // Once the mine is exhausted, unfinished cargo must reach the chest before anyone picks a fight
            // or obeys an old map order. Otherwise a few units can sit forever in half-filled bags.
            if(veinsExhausted&&w.hasCargo()){w.mob=null;w.vein=null;carryHome(w,dt);continue;}

            // Direct player orders always win. No ambient rubble job is allowed to hijack them.''',
'force final cargo home')

# Objective gets a restrained breathing highlight so the player keeps seeing what actually ends the floor.
s = repl(s,
'''        d.align=Draw.Align.CENTER;d.textSize=5.4f*ui;d.setColor(levelObjectiveMet()?0xFF79C98A:0xFFE2B544);d.text(levelObjectiveHud(),width*.66f,42f*ui);''',
'''        drawObjectiveHud(d);''',
'objective draw call')

marker = '''    private void drawActiveEffects(Draw d){'''
if marker not in s:
    raise SystemExit('patch target not found: objective method insertion')
objective_method = '''    private void drawObjectiveHud(Draw d){
        float pulse=.5f+.5f*(float)Math.sin(elapsed*3.0f);
        float cx=width*.66f,cy=42f*ui,w=Math.min(width*.52f,210f*ui);
        int col=levelObjectiveMet()?0xFF79C98A:0xFFE2B544;
        d.setColor(alpha(col,.035f+.055f*pulse));
        d.fillRoundRect(cx-w*.5f,cy-10f*ui,cx+w*.5f,cy+9f*ui,7f*ui);
        d.setColor(alpha(col,.22f+.36f*pulse));
        d.fillRoundRect(cx-w*.34f,cy+8.5f*ui,cx+w*.34f,cy+9.7f*ui,.7f*ui);
        d.setColor(alpha(col,.40f+.45f*pulse));
        float dot=1.4f*ui+.7f*ui*pulse;
        d.fillCircle(cx-w*.43f,cy-1f*ui,dot);d.fillCircle(cx+w*.43f,cy-1f*ui,dot);
        d.align=Draw.Align.CENTER;d.bold=pulse>.62f;d.textSize=(5.25f+.22f*pulse)*ui;d.setColor(col);d.text(levelObjectiveHud(),cx,cy);d.bold=false;
    }

'''
s = s.replace(marker, objective_method + marker, 1)

# Replace the broad rectangular glow with a local finger ripple and sparks.
s = repl(s,
'''        if(speedHeld)drawSpeedGlow(d);
        button(d,speed,speedHeld?"УСКОРЕНИЕ":"УСКОРИТЬ ГНОМОВ",true,.86f);
    }
    private void drawSpeedGlow(Draw d){
        float pulse=.5f+.5f*(float)Math.sin(elapsed*11f);
        for(int i=3;i>=1;i--){float pad=(3f+i*3.5f+pulse*2f)*ui;d.setColor(alpha(0xFFFFD45C,.045f+i*.025f));d.fillRoundRect(speed.l-pad,speed.t-pad,speed.r+pad,speed.b+pad,(12f+i*3f)*ui);}
        d.setColor(alpha(0xFFFFE88A,.65f+.25f*pulse));
        for(int i=0;i<10;i++){float q=(elapsed*(.28f+i*.013f)+i*.103f)%1f,x=speed.l+(speed.r-speed.l)*q,y=(i&1)==0?speed.t-3f*ui:speed.b+3f*ui;d.fillCircle(x,y,(1.4f+(i%3)*.45f)*ui);}
    }''',
'''        button(d,speed,speedHeld?"УСКОРЕНИЕ":"УСКОРИТЬ ГНОМОВ",true,.86f);
        if(speedHeld)drawSpeedGlow(d);
    }
    private void drawSpeedGlow(Draw d){
        float tx=Math.max(speed.l+18f*ui,Math.min(speed.r-18f*ui,speedTouchX));
        float ty=Math.max(speed.t+10f*ui,Math.min(speed.b-10f*ui,speedTouchY));
        float pulse=.5f+.5f*(float)Math.sin(elapsed*10f),ring=(15f+4f*pulse)*ui;
        d.setColor(alpha(0xFFFFE58A,.22f+.20f*pulse));d.strokeWidth=(1.4f+1.0f*pulse)*ui;d.strokeCircle(tx,ty,ring);
        d.setColor(alpha(0xFFFFD35A,.55f+.30f*pulse));
        for(int i=0;i<7;i++){float a=elapsed*(2.3f+i*.07f)+i*.897f,r=ring*(.72f+.22f*((i%3)/2f));d.fillCircle(tx+(float)Math.cos(a)*r,ty+(float)Math.sin(a)*r,(1.0f+(i%3)*.45f)*ui);}
    }''',
'speed visual feedback')

p.write_text(s)

# The shared button renderer gets a richer pressed face. At present only the hold-to-speed control uses
# pressed=true, so this does not repaint the rest of the UI.
u = Path('core/src/main/java/com/enhort/gnomes/ui/UiTheme.java')
t = u.read_text()
t = repl(t,
'''        d.setColor(enabled ? (pressed ? 0xFF20262A : 0xFF252C31) : 0xFF171B1E);''',
'''        d.setColor(enabled ? (pressed ? 0xFF493A1E : 0xFF252C31) : 0xFF171B1E);''',
'pressed outer face')
t = repl(t,
'''        d.setColor(enabled ? 0xFF3E474D : 0xFF242A2E);''',
'''        d.setColor(enabled ? (pressed ? 0xFF6A5225 : 0xFF3E474D) : 0xFF242A2E);''',
'pressed middle face')
t = repl(t,
'''        d.setColor(enabled ? 0xFF22282C : 0xFF191D20);
        d.fillRoundRect(l + 2f * ui, t + 3f * ui + down, r - 2f * ui, b - 2f * ui + down,
                Math.max(3f * ui, radius - 2f * ui));

        if (enabled) {''',
'''        d.setColor(enabled ? (pressed ? 0xFF302B20 : 0xFF22282C) : 0xFF191D20);
        d.fillRoundRect(l + 2f * ui, t + 3f * ui + down, r - 2f * ui, b - 2f * ui + down,
                Math.max(3f * ui, radius - 2f * ui));

        if (pressed && enabled) {
            d.setColor(0x335A4315);
            d.fillRoundRect(l + 4f * ui, t + 5f * ui + down, r - 4f * ui, b - 4f * ui + down,
                    Math.max(3f * ui, radius - 3f * ui));
            d.setColor(0x99FFD35A);
            d.fillRect(l + 12f * ui, t + 4f * ui + down, r - 10f * ui, t + 5.4f * ui + down);
        }

        if (enabled) {''',
'pressed inner lighting')
t = repl(t,
'''            d.setColor(enabled ? 0xFFF4F1E9 : 0xFF626A70);''',
'''            d.setColor(enabled ? (pressed ? 0xFFFFE8A5 : 0xFFF4F1E9) : 0xFF626A70);''',
'text pressed color')
u.write_text(t)
