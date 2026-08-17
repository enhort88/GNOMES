from pathlib import Path
import re

p = Path('core/src/main/java/com/enhort/gnomes/game/CaveScreen.java')
s = p.read_text()

def sub_once(pattern, repl, label):
    global s
    out, n = re.subn(pattern, repl, s, count=1, flags=re.S)
    if n != 1:
        raise SystemExit(f'{label}: expected 1 replacement, got {n}')
    s = out

# Leave a real header area above the two action rows. With the global x2 font scale
# the old layout put the artifact/rune status line under the first row of buttons.
s = s.replace(
    'float actionsTop=contentT+navH+7f*ui;',
    'float actionsTop=Math.max(contentT+58f*ui,contentT+navH+7f*ui);'
)

# A cave-in is a blocked cell, therefore a worker must stop on a reachable neighbour,
# walk to that neighbour\'s edge and mine the rubble from there. The old implementation
# routed INTO the blocked cell and followWorker correctly refused to enter it.
sub_once(
    r'    private void clearCollapse\(Worker w,CaveHazard h,float dt\)\{.*?\n    \}\n\n    private int guardianDefenderQuota',
    '''    private void clearCollapse(Worker w,CaveHazard h,float dt){
        float base=Math.min(cellW,cellH),reach=base*.66f;
        if(distance(w.x,w.y,h.x,h.y)>reach){
            int approach=collapseApproachCell(w,h);
            if(approach<0){w.action=WorkerAction.IDLE;w.vx=w.vy=0;w.routeRetry=.22f;return;}
            if(!atCell(w,approach)){
                w.action=WorkerAction.WALK;
                if(w.goalCell!=approach||w.path.length==0){w.goalCell=approach;w.path=map.path(cellFor(w.x,w.y),approach);w.pathIndex=Math.min(1,w.path.length);}
                followWorker(w,moveSpeed(w)*.82f,dt);return;
            }
            float dx=h.x-w.x,dy=h.y-w.y,di=len(dx,dy);
            if(di>.001f){
                float tx=h.x-dx/di*base*.58f,ty=h.y-dy/di*base*.58f;
                w.action=WorkerAction.WALK;moveDirect(w,tx,ty,moveSpeed(w)*.62f,dt);return;
            }
        }
        w.action=WorkerAction.MINE;w.vx=w.vy=0;
        if(w.attackCooldown<=0&&w.swing<=0){w.swing=.58f;w.hitApplied=false;w.attackCooldown=.62f;}
        float p=w.swing<=0?1f:1f-w.swing/.58f;
        if(w.swing>0&&!w.hitApplied&&p>=.57f){
            w.hitApplied=true;float damage=Math.max(1f,w.tier.miningPower*state.tierPowerMultiplier(w.tier.ordinal())*state.miningMultiplier(w.tier.ordinal())*.55f);
            h.rubbleHp-=damage;spawnSparks(h.x,h.y,0xFF9B8A78,workers.size()>80?1:3);
            if(h.rubbleHp<=0){h.rubbleHp=0;h.cleared=true;h.obstacleActive=false;map.unblockCell(h.cell);invalidateRoutes();if(priorityHazard==h)clearPriority(false);toast="ОБВАЛ РАЗОБРАН";toastTime=1.5f;game.audio.play(GameAudio.Sfx.ROCK_BREAK,.75f);}
        }
    }

    private int collapseApproachCell(Worker w,CaveHazard h){
        int hc=map.col(h.cell),hr=map.row(h.cell),start=cellFor(w.x,w.y),best=-1,bestLen=Integer.MAX_VALUE;
        int[] dirs={CaveMap.N,CaveMap.E,CaveMap.S,CaveMap.W};
        for(int dir:dirs){
            if(!map.connected(hc,hr,dir))continue;
            int nc=hc+CaveMap.dx(dir),nr=hr+CaveMap.dy(dir);if(!map.inside(nc,nr))continue;
            int cell=map.index(nc,nr);if(map.isBlocked(cell))continue;
            int[] route=map.path(start,cell);if(route.length>0&&route.length<bestLen){best=cell;bestLen=route.length;}
        }
        return best;
    }

    private int guardianDefenderQuota''',
    'collapse approach'
)

# The solid rock mass used to be almost black, so the grid gaps read as rectangular
# black panels. Give the whole cave a visible stone body and break it up with large,
# irregular deterministic slabs. Tunnels are painted over this afterwards.
sub_once(
    r'    private void drawRockMass\(Draw d\)\{.*?\n    \}\n\n    private void drawTunnels',
    '''    private void drawRockMass(Draw d){
        d.setColor(0xFF272720);d.fillRect(worldL,worldT,worldR,worldB);long seed=map.seed;
        int count=workers.size()>100?44:76;
        for(int i=0;i<count;i++){
            long q=seed+i*0x9E3779B97F4A7C15L;
            float x=worldL+hash01(q)*(worldR-worldL),y=worldT+hash01(q^0xA5A5A5A5L)*(worldB-worldT);
            float rr=(8f+hash01(q^0x55AA55AAL)*18f)*ui;
            int c=i%6==0?0xFF33372E:i%4==0?0xFF302E27:i%3==0?0xFF242923:0xFF2B2A25;
            d.setColor(c);d.fillOval(x-rr*1.45f,y-rr*.62f,x+rr*1.45f,y+rr*.62f);
            d.setColor(i%5==0?0x443F5E3B:0x223F3A31);d.strokeWidth=Math.max(1f,1.1f*ui);
            d.line(x-rr*.95f,y-rr*.18f,x-rr*.12f,y+rr*.08f);d.line(x-rr*.12f,y+rr*.08f,x+rr*.76f,y-rr*.11f);
            if(i%9==0){d.setColor(0x553F6940);d.fillCircle(x-rr*.34f,y-rr*.16f,rr*.28f);d.fillCircle(x+rr*.02f,y-rr*.22f,rr*.20f);}
        }
    }

    private void drawTunnels''',
    'stone background'
)

# Darkness should be fog, not another dark geometric tile. Keep the tunnel readable and
# only lay a sparse smoky veil over dark cells.
sub_once(
    r'    private void drawDarkZones\(Draw d\)\{.*?\n    \}\n    private void drawPortal',
    '''    private void drawDarkZones(Draw d){
        for(int r=0;r<map.rows;r++)for(int c=0;c<map.cols;c++){
            int cell=map.index(c,r);if(!isDarkCell(cell))continue;
            float base=Math.min(cellW,cellH),x=cx(c),y=cy(r),phase=hash01(map.seed^cell*0x6A09E667F3BCC909L)*6.28318f;
            for(int i=0;i<13;i++){
                float a=phase+i*.483f+(float)Math.sin(elapsed*.13f+i+cell)*.13f;
                float ring=base*(.08f+.31f*hash01(cell*131L+i*17L));
                float ox=(float)Math.cos(a)*ring,oy=(float)Math.sin(a)*ring*.68f;
                float rx=base*(.17f+.11f*hash01(cell*79L+i*23L));
                float ry=rx*(.42f+.24f*hash01(cell*47L+i*29L));
                d.setColor(i<4?0x28101618:0x1B12191B);d.fillOval(x+ox-rx,y+oy-ry,x+ox+rx,y+oy+ry);
            }
        }
        for(Worker w:workers)if(isDarkCell(cellFor(w.x,w.y))){
            float rr=(19f+w.tier.ordinal()*2f)*ui;
            d.setColor(0x1452A5B5);d.fillCircle(w.x,w.y,rr*1.32f);
            d.setColor(0x22D5B56A);d.fillCircle(w.x,w.y,rr*.62f);
            d.setColor(0x2CFFE09A);d.fillCircle(w.x,w.y,rr*.24f);
        }
    }
    private void drawPortal''',
    'fog darkness'
)

# Re-layout artifact/rune headers so their description/status never sit underneath the
# first button row on a tall phone with the 2x global font multiplier.
sub_once(
    r'    private void drawUpgradePanel\(Draw d\)\{.*?\n    private void drawArtifactPanel',
    '''    private void drawUpgradePanel(Draw d){float ct=contentTop();d.align=Draw.Align.CENTER;d.bold=true;d.textSize=7.0f*ui;d.setColor(0xFFF0F3F5);d.text("ШАХТА И ИНФРАСТРУКТУРА",width/2,ct+17f*ui);d.bold=false;d.align=Draw.Align.LEFT;button(d,primary,"КИРКИ ур."+state.miningUpgrade,true,.70f);button(d,secondary,"ЛОГИСТИКА ур."+state.speedUpgrade,true,.66f);button(d,tertiary,"БОЙ ур."+state.combatUpgrade,true,.70f);button(d,quaternary,state.guardianLevel==0?"НАНЯТЬ СТРАЖА":"СТРАЖ ур."+state.guardianLevel,true,.66f);}
    private void drawArtifactPanel''',
    'upgrade header'
)

sub_once(
    r'    private void drawArtifactPanel\(Draw d\)\{.*?\n\n    private void drawRunePanel',
    '''    private void drawArtifactPanel(Draw d){ArtifactType a=ArtifactType.values()[selectedArtifact];float ct=contentTop();button(d,left,"‹",selectedArtifact>0,1.15f);button(d,right,"›",selectedArtifact<ArtifactType.values().length-1,1.15f);boolean owned=state.artifactOwned(selectedArtifact),active=owned&&state.artifactActive[selectedArtifact];d.align=Draw.Align.CENTER;d.bold=true;d.textSize=7.2f*ui;d.setColor(a.color);d.text(a.title,width/2,ct+14f*ui);d.bold=false;d.textSize=4.8f*ui;d.setColor(0xFFB5BFC7);d.text(ellipsize(a.description,27),width/2,ct+30f*ui);d.textSize=4.5f*ui;d.setColor(active?0xFF7FDEA0:0xFFAAB4BB);d.text(owned?(active?"АКТИВЕН":"СНЯТ"):"НЕ КУПЛЕН",width/2,ct+43f*ui);d.align=Draw.Align.LEFT;button(d,primary,owned?(active?"СНЯТЬ":"НАДЕТЬ"):"КУПИТЬ • ◆"+state.artifactCost(selectedArtifact),true,.62f);statPill(d,secondary,"ПОКУПАЕТСЯ 1 РАЗ");statPill(d,tertiary,owned?"КУПЛЕН":"НУЖНЫ ◆ АЛМАЗЫ");statPill(d,quaternary,active?"АКТИВЕН":"НЕ АКТИВЕН");}

    private void drawRunePanel''',
    'artifact panel'
)

sub_once(
    r'    private void drawRunePanel\(Draw d\)\{.*?\n\n\n    private void button',
    '''    private void drawRunePanel(Draw d){RuneType r=RuneType.values()[selectedRune];float ct=contentTop();button(d,left,"‹",selectedRune>0,1.15f);button(d,right,"›",selectedRune<RuneType.values().length-1,1.15f);boolean active=state.runeIsActive(selectedRune);d.align=Draw.Align.CENTER;d.bold=true;d.textSize=7.2f*ui;d.setColor(r.color);d.text(r.title,width/2,ct+14f*ui);d.bold=false;d.textSize=4.8f*ui;d.setColor(0xFFB7C0C7);d.text(ellipsize(r.description,27),width/2,ct+30f*ui);d.textSize=4.5f*ui;d.setColor(active?0xFF7FDEA0:0xFFAAB4BB);d.text("УР. "+state.runeLevels[selectedRune]+" • "+(active?"АКТИВНА":"СНЯТА"),width/2,ct+43f*ui);d.align=Draw.Align.LEFT;button(d,primary,"УСИЛИТЬ • ◆"+state.runeUpgradeCost(selectedRune),state.runeLevels[selectedRune]<12,.58f);button(d,secondary,active?"СНЯТЬ РУНУ":"АКТИВИРОВАТЬ",state.runeLevels[selectedRune]>0,.54f);statPill(d,tertiary,"НА ВСЕХ ГНОМОВ");statPill(d,quaternary,"МЕТА • НАВСЕГДА");}


    private void button''',
    'rune panel'
)

p.write_text(s)
print('patched CaveScreen.java')
