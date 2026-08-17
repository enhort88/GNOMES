from pathlib import Path
import re

p = Path('core/src/main/java/com/enhort/gnomes/game/CaveScreen.java')
s = p.read_text()

def sub_once(pattern, repl, label):
    global s
    out, n = re.subn(pattern, repl, s, count=1, flags=re.S)
    if n != 1:
        raise SystemExit(f'{label} matcher failed: {n}')
    s = out

# Soft fog-of-war. Never cover a grid cell with a near-black block: keep the
# tunnel readable under translucent irregular charcoal mist.
sub_once(
    r'    private void drawDarkZones\(Draw d\)\{.*?\n    \}\n    private void drawPortal',
'''    private void drawDarkZones(Draw d){
        for(int r=0;r<map.rows;r++)for(int c=0;c<map.cols;c++){
            int cell=map.index(c,r);if(!isDarkCell(cell))continue;
            float base=Math.min(cellW,cellH),x=cx(c),y=cy(r),phase=hash01(map.seed^cell*0x6A09E667F3BCC909L)*6.28318f;
            // Broad, low-alpha fog body. The underlying stone/tunnel always remains visible.
            for(int i=0;i<11;i++){
                float a=phase+i*.571f+(float)Math.sin(elapsed*.18f+cell+i)*.08f;
                float ring=base*(i<3?.12f:.18f+.035f*(i%4));
                float ox=(float)Math.cos(a)*ring,oy=(float)Math.sin(a*1.17f)*ring*.78f;
                float rad=base*(.30f+.035f*(i%5));
                d.setColor(i<3?0x3C11161A:0x2811171B);d.fillCircle(x+ox,y+oy,rad);
            }
            // Wispy fringe destroys the square-cell silhouette.
            for(int i=0;i<7;i++){
                float a=phase+i*.897f-elapsed*.07f,rr=base*(.34f+.05f*(i%3));
                float wx=x+(float)Math.cos(a)*rr,wy=y+(float)Math.sin(a)*rr*.72f;
                d.setColor(0x18151B1E);d.fillOval(wx-base*.18f,wy-base*.10f,wx+base*.18f,wy+base*.10f);
            }
        }
        // A gnome carries a small local pool of visibility, not a hard-edged flashlight disc.
        for(Worker w:workers)if(isDarkCell(cellFor(w.x,w.y))){
            float rr=(19f+w.tier.ordinal()*2f)*ui;
            d.setColor(0x1852A5B5);d.fillCircle(w.x,w.y,rr*1.30f);
            d.setColor(0x24D5B56A);d.fillCircle(w.x,w.y,rr*.58f);
            d.setColor(0x30FFE09A);d.fillCircle(w.x,w.y,rr*.22f);
        }
    }
    private void drawPortal''',
    'dark zones')

# Mobile HUD: shorter objective string and smaller logical sizes only where the
# doubled display font otherwise collides with resource counters.
sub_once(
    r'    private void drawHud\(Draw d\)\{.*?\n    \}\n    private void drawActiveEffects',
'''    private void drawHud(Draw d){
        d.setColor(0xFF0D1012);d.fillRect(0,0,width,worldT);
        d.setColor(0xFF252B30);d.fillRect(0,worldT-2f*ui,width,worldT);
        button(d,back,"‹",true,1.20f);
        d.align=Draw.Align.LEFT;d.bold=true;d.textSize=10.8f*ui;d.setColor(0xFFF2EFE7);d.text("GNOMES",58f*ui,21f*ui);
        d.bold=false;d.textSize=6.2f*ui;d.setColor(UiTheme.GOLD);d.text("ГЛУБИНА "+state.depth,58f*ui,42f*ui);
        d.align=Draw.Align.CENTER;d.textSize=5.4f*ui;d.setColor(levelObjectiveMet()?0xFF79C98A:0xFFE2B544);d.text(levelObjectiveHud(),width*.66f,42f*ui);d.align=Draw.Align.LEFT;
        float y=65f*ui,section=width/4f;
        drawResource(d,7f*ui,y,0xFF888D92,"●",state.stone);
        drawResource(d,section+5f*ui,y,0xFFC6D0D8,"Ag",state.silver);
        drawResource(d,section*2+5f*ui,y,0xFFE2B544,"Au",state.gold);
        drawResource(d,section*3+5f*ui,y,0xFF67D7F2,"◆",state.diamond);
        drawActiveEffects(d);
    }
    private void drawActiveEffects''',
    'hud')

s = s.replace(
    '    private void drawResource(Draw d,float x,float y,int col,String icon,long n){d.setColor(col);d.fillCircle(x+5f*ui,y-4f*ui,4f*ui);d.bold=true;d.textSize=9f*ui;d.text(icon+" "+format(n),x+13f*ui,y);d.bold=false;}',
    '    private void drawResource(Draw d,float x,float y,int col,String icon,long n){d.setColor(col);d.fillCircle(x+5f*ui,y-4f*ui,3.6f*ui);d.bold=true;d.textSize=6.4f*ui;d.text(icon+" "+format(n),x+12f*ui,y);d.bold=false;}')

# Panel copy should be readable, but no sentence is allowed to escape its card.
sub_once(
    r'    private void drawGnomePanel\(Draw d\)\{.*?\n\n    private void drawUpgradePanel',
'''    private void drawGnomePanel(Draw d){GnomeTier gt=GnomeTier.values()[selectedTier];float ct=contentTop();button(d,left,"‹",selectedTier>0,1.15f);button(d,right,"›",selectedTier<GnomeTier.values().length-1,1.15f);d.align=Draw.Align.CENTER;d.bold=true;d.textSize=8.2f*ui;d.setColor(gt.color);d.text(gt.title,width/2,ct+16f*ui);d.textSize=6.7f*ui;d.setColor(0xFFF1D58A);d.text("ГНОМОВ: "+state.tierCounts[selectedTier],width*.31f,ct+37f*ui);d.setColor(0xFFB9C8D0);d.text("ДОБЫЧА: "+one.format(gt.miningPower*state.tierPowerMultiplier(selectedTier)*state.miningMultiplier(selectedTier))+"/УДАР",width*.69f,ct+37f*ui);d.align=Draw.Align.LEFT;d.bold=false;
        if(selectedTier==0)button(d,primary,"КУПИТЬ • "+format(state.minerBuyCost()),true,.72f);else statPill(d,primary,"УР. "+state.tierLevels[selectedTier]+" • БОЙ "+one.format(gt.combatPower*state.combatMultiplier(selectedTier)));button(d,secondary,"УЛУЧШИТЬ • "+format(state.tierUpgradeCost(selectedTier)),true,.66f);boolean merge=selectedTier<GnomeTier.values().length-1&&(GameState.FREE_SHOP||state.tierCounts[selectedTier]>=10);button(d,tertiary,GameState.FREE_SHOP?"TEST • СЛЕДУЮЩИЙ":"СЛИТЬ 10 → 1",merge,.60f);statPill(d,quaternary,"СУМКА • "+format((long)(gt.cargoCapacity*state.carryMultiplier(selectedTier))));}

    private void drawUpgradePanel''',
    'gnome panel')

sub_once(
    r'    private void drawArtifactPanel\(Draw d\)\{.*?\n\n    private void drawRunePanel',
'''    private void drawArtifactPanel(Draw d){ArtifactType a=ArtifactType.values()[selectedArtifact];float ct=contentTop();button(d,left,"‹",selectedArtifact>0,1.15f);button(d,right,"›",selectedArtifact<ArtifactType.values().length-1,1.15f);boolean owned=state.artifactOwned(selectedArtifact),active=owned&&state.artifactActive[selectedArtifact];d.align=Draw.Align.CENTER;d.bold=true;d.textSize=8.0f*ui;d.setColor(a.color);d.text(a.title,width/2,ct+16f*ui);d.bold=false;d.textSize=5.5f*ui;d.setColor(0xFFB5BFC7);d.text(ellipsize(a.description,30),width/2,ct+35f*ui);d.textSize=5.1f*ui;d.setColor(active?0xFF7FDEA0:0xFFAAB4BB);d.text(owned?(active?"АКТИВЕН":"СНЯТ"):"НЕ КУПЛЕН",width/2,ct+50f*ui);d.align=Draw.Align.LEFT;button(d,primary,owned?(active?"СНЯТЬ":"НАДЕТЬ"):"КУПИТЬ • ◆"+state.artifactCost(selectedArtifact),true,.66f);statPill(d,secondary,"ПОКУПАЕТСЯ 1 РАЗ");statPill(d,tertiary,owned?"КУПЛЕН":"НУЖНЫ ◆ АЛМАЗЫ");statPill(d,quaternary,active?"АКТИВЕН":"НЕ АКТИВЕН");}

    private void drawRunePanel''',
    'artifact panel')

sub_once(
    r'    private void drawRunePanel\(Draw d\)\{.*?\n\n\n    private void button',
'''    private void drawRunePanel(Draw d){RuneType r=RuneType.values()[selectedRune];float ct=contentTop();button(d,left,"‹",selectedRune>0,1.15f);button(d,right,"›",selectedRune<RuneType.values().length-1,1.15f);boolean active=state.runeIsActive(selectedRune);d.align=Draw.Align.CENTER;d.bold=true;d.textSize=8.0f*ui;d.setColor(r.color);d.text(r.title,width/2,ct+16f*ui);d.bold=false;d.textSize=5.5f*ui;d.setColor(0xFFB7C0C7);d.text(ellipsize(r.description,30),width/2,ct+35f*ui);d.textSize=5.1f*ui;d.setColor(active?0xFF7FDEA0:0xFFAAB4BB);d.text("УР. "+state.runeLevels[selectedRune]+" • "+(active?"АКТИВНА":"СНЯТА"),width/2,ct+50f*ui);d.align=Draw.Align.LEFT;button(d,primary,"УСИЛИТЬ • ◆"+state.runeUpgradeCost(selectedRune),state.runeLevels[selectedRune]<12,.62f);button(d,secondary,active?"СНЯТЬ РУНУ":"АКТИВИРОВАТЬ",state.runeLevels[selectedRune]>0,.58f);statPill(d,tertiary,"НА ВСЕХ ГНОМОВ");statPill(d,quaternary,"МЕТА • НАВСЕГДА");}


    private void button''',
    'rune panel')

s = s.replace(
    '    private void statPill(Draw d,Box b,String text){d.setColor(0x66191D20);d.fillRoundRect(b.l,b.t,b.r,b.b,7f*ui);d.setColor(0xFF252B30);d.fillRoundRect(b.l+1f*ui,b.t+1f*ui,b.r-1f*ui,b.b-1f*ui,6f*ui);d.align=Draw.Align.CENTER;d.bold=false;d.textSize=7.2f*ui;d.setColor(0xFF9FAAAF);d.text(text,b.cx(),b.cy()+2.5f*ui);d.align=Draw.Align.LEFT;}',
    '    private void statPill(Draw d,Box b,String text){d.setColor(0x66191D20);d.fillRoundRect(b.l,b.t,b.r,b.b,7f*ui);d.setColor(0xFF252B30);d.fillRoundRect(b.l+1f*ui,b.t+1f*ui,b.r-1f*ui,b.b-1f*ui,6f*ui);d.clipRect(b.l+4f*ui,b.t+2f*ui,b.r-4f*ui,b.b-2f*ui);d.align=Draw.Align.CENTER;d.bold=false;d.textSize=5.3f*ui;d.setColor(0xFF9FAAAF);d.text(text,b.cx(),b.cy()+2f*ui);d.align=Draw.Align.LEFT;d.unclip();}')

# Short objective text specifically for the narrow top HUD.
anchor = '    private String levelObjectiveToast(){return levelObjectiveShort();}'
if anchor not in s:
    raise SystemExit('objective anchor missing')
s = s.replace(anchor, '''    private String levelObjectiveToast(){return levelObjectiveShort();}
    private String levelObjectiveHud(){return switch(objectiveType){
        case ASCEND_GNOME -> "ЦЕЛЬ: ПРОДВИНУТЫЙ "+Math.min(1,state.tierCounts[GnomeTier.VETERAN.ordinal()])+"/1";
        case CLEAR_VEINS -> "ЦЕЛЬ: ОЧИСТИТЬ ЖИЛЫ";
        case GUARDIAN -> "ЦЕЛЬ: СТРАЖ УР."+objectiveTarget;
        case DEMON_PURGE -> "ЦЕЛЬ: НАШЕСТВИЕ";
        case BOSS_HUNT -> "ЦЕЛЬ: УБИТЬ БОССА";
        case TREASURE -> "ЦЕЛЬ: "+format(objectiveTreasureTarget);
    };}''', 1)

p.write_text(s)
