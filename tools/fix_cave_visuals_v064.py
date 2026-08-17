from pathlib import Path
import re

p = Path('core/src/main/java/com/enhort/gnomes/game/CaveScreen.java')
s = p.read_text()

def sub_once(pattern, repl, label, flags=re.S):
    global s
    out, n = re.subn(pattern, repl, s, count=1, flags=flags)
    if n != 1:
        raise SystemExit(f'{label}: expected 1 replacement, got {n}')
    s = out

# 1) Ore deposits should have visibly varied silhouettes/sizes, with only a small type bias.
sub_once(
    r'float radius=Math\.min\(cellW,cellH\)\*\(\.19f\+random\.nextFloat\(\)\*\.075f\);',
    '''float sizeJitter=.155f+random.nextFloat()*.145f;
            float typeScale=switch(type){case OBSIDIAN->1.10f;case ANCIENT_CRYSTAL->1.16f;case DIAMOND->.92f;default->1f;};
            float radius=Math.min(cellW,cellH)*sizeJitter*typeScale;''',
    'ore size variation', flags=0)

# 2) Persistent hazards: pits/lava kill continuously; rubble slowly settles away if ignored.
sub_once(
    r'''    private void updateHazards\(float dt\)\{.*?    private float hazardDuration\(HazardType t\)\{return switch\(t\)\{case COLLAPSE->Float\.MAX_VALUE;case PIT->8f;case LAVA->9f;case FLOOD->4\.8f;\};\}\n''',
    '''    private void updateHazards(float dt){
        for(Iterator<CaveHazard>it=hazards.iterator();it.hasNext();){
            CaveHazard h=it.next();h.age+=dt;
            if(!h.fired&&h.age>=1.25f){h.fired=true;fireHazard(h);}
            if(h.fired){
                if(h.type==HazardType.LAVA)applyLava(h,dt);
                else if(h.type==HazardType.PIT)applyPit(h);
                else if(h.type==HazardType.COLLAPSE)settleCollapse(h,dt);
            }
            if(h.type==HazardType.COLLAPSE){if(h.cleared)it.remove();continue;}
            if(h.age>hazardDuration(h.type))it.remove();
        }
    }
    private float hazardDuration(HazardType t){return switch(t){case COLLAPSE->Float.MAX_VALUE;case PIT->8f;case LAVA->9f;case FLOOD->5.4f;};}
    private void settleCollapse(CaveHazard h,float dt){
        if(h.cleared||!h.obstacleActive||h.age<6f)return;
        // Loose rubble gradually settles and rolls away. A player can still clear it much faster with gnomes.
        h.rubbleHp-=h.rubbleMaxHp*.012f*dt;
        if(h.rubbleHp<=0)finishCollapse(h,false);
    }
    private void finishCollapse(CaveHazard h,boolean byGnomes){
        if(h.cleared)return;
        h.rubbleHp=0;h.cleared=true;h.obstacleActive=false;map.unblockCell(h.cell);
        if(priorityHazard==h)clearPriority(false);
        invalidateRoutes();
        if(byGnomes){toast="ОБВАЛ РАЗОБРАН";toastTime=1.5f;game.audio.play(GameAudio.Sfx.ROCK_BREAK,.75f);}
    }
''',
    'hazard update')

# 3) Pit and collapse impact lethality. Pit/lava are genuinely deadly, not a dice roll.
sub_once(
    r'''                case FLOOD -> w\.stun=Math\.max\(w\.stun,1\.8f\);\n                case LAVA -> loseWorker\(w,"сгорел в лаве"\);\n                case PIT -> \{if\(random\.nextFloat\(\)<\.34f\*\(1-survive\)\)loseWorker\(w,"провалился в яму"\);else w\.stun=Math\.max\(w\.stun,1\.2f\);\}\n                case COLLAPSE -> \{if\(random\.nextFloat\(\)<\.28f\*\(1-survive\)\)loseWorker\(w,"погиб под обвалом"\);else w\.stun=Math\.max\(w\.stun,1\.1f\);\}\n''',
    '''                case FLOOD -> w.stun=Math.max(w.stun,1.8f);
                case LAVA -> loseWorker(w,"сгорел в лаве");
                case PIT -> loseWorker(w,"провалился в яму");
                case COLLAPSE -> {if(distance(w.x,w.y,h.x,h.y)<h.r*.70f)loseWorker(w,"погиб под обвалом");else if(random.nextFloat()<.24f*(1-survive))loseWorker(w,"погиб под обвалом");else w.stun=Math.max(w.stun,1.1f);}
''',
    'hazard lethality')

# Continuous pit check catches gnomes who walk into an already-open hole.
sub_once(
    r'''    private void applyLava\(CaveHazard h,float dt\)\{.*?    \}\n\n    private void hitMobsWithHazard''',
    lambda m: m.group(0).replace('\n\n    private void hitMobsWithHazard', '''\n\n    private void applyPit(CaveHazard h){
        float rr=h.r*.64f;
        for(int i=workers.size()-1;i>=0;i--){Worker w=workers.get(i);if(distance(w.x,w.y,h.x,h.y)<rr)loseWorker(w,"провалился в яму");}
    }

    private void hitMobsWithHazard'''),
    'continuous pit')

# 4) Rubble clearing: never route into the blocked cell. Mine from a connected neighbouring cell and
# release worker state immediately after the rubble is gone.
sub_once(
    r'''    private void clearCollapse\(Worker w,CaveHazard h,float dt\)\{.*?\n    \}\n\n    private int collapseApproachCell''',
    '''    private void clearCollapse(Worker w,CaveHazard h,float dt){
        int approach=collapseApproachCell(w,h);
        if(approach<0){w.action=WorkerAction.IDLE;w.vx=w.vy=0;w.path=new int[0];w.pathIndex=0;w.goalCell=-1;w.routeRetry=.22f;return;}
        if(!atCell(w,approach)){
            w.action=WorkerAction.WALK;
            if(w.goalCell!=approach||w.path.length==0||w.pathIndex>=w.path.length){
                w.goalCell=approach;w.path=map.path(cellFor(w.x,w.y),approach);w.pathIndex=Math.min(1,w.path.length);
            }
            followWorker(w,moveSpeed(w)*.82f,dt);return;
        }
        // The adjacent cell is the work position. Do not make the worker creep into the blocked cell.
        w.path=new int[0];w.pathIndex=0;w.goalCell=-1;
        w.action=WorkerAction.MINE;w.vx=h.x<w.x?-2f:2f;w.vy=0;
        if(w.attackCooldown<=0&&w.swing<=0){w.swing=.58f;w.hitApplied=false;w.attackCooldown=.62f;}
        float p=w.swing<=0?1f:1f-w.swing/.58f;
        if(w.swing>0&&!w.hitApplied&&p>=.57f){
            w.hitApplied=true;float damage=Math.max(1f,w.tier.miningPower*state.tierPowerMultiplier(w.tier.ordinal())*state.miningMultiplier(w.tier.ordinal())*.55f);
            h.rubbleHp-=damage;spawnSparks(h.x,h.y,0xFF9B8A78,workers.size()>80?1:3);
            if(h.rubbleHp<=0){w.swing=0;w.attackCooldown=0;w.vx=w.vy=0;finishCollapse(h,true);}
        }
    }

    private int collapseApproachCell''',
    'rubble clearing')

# 5) Wet corridors become little puddle groups instead of cyan/green stripes.
sub_once(
    r'''    private void drawWetCorridors\(Draw d\)\{.*?\n    \}\n    private void drawCaveDecor''',
    '''    private void drawWetCorridors(Draw d){
        float base=Math.min(cellW,cellH);int wet=0;
        for(int r=0;r<map.rows;r++)for(int c=0;c<map.cols;c++){
            int cell=map.index(c,r);long h=map.seed^cell*0x94D049BB133111EBL;if(hash01(h)>.115f)continue;
            int dir=map.connected(c,r,CaveMap.E)?CaveMap.E:(map.connected(c,r,CaveMap.S)?CaveMap.S:0);if(dir==0)continue;
            float x1=cx(c),y1=cy(r),x2=cx(c+CaveMap.dx(dir)),y2=cy(r+CaveMap.dy(dir));
            float dx=x2-x1,dy=y2-y1,len=Math.max(1f,len(dx,dy)),nx=-dy/len,ny=dx/len;
            int puddles=2+(cell&1);
            for(int i=0;i<puddles;i++){
                float q=.22f+i*(.56f/Math.max(1,puddles-1));
                float jitter=(hash01(h+i*137L)-.5f)*base*.16f;
                float px=x1+dx*q+nx*jitter,py=y1+dy*q+ny*jitter;
                float longR=base*(.095f+.045f*hash01(h+i*271L));
                float shortR=base*(.045f+.025f*hash01(h+i*353L));
                float rx=Math.abs(dx)>Math.abs(dy)?longR:shortR,ry=Math.abs(dx)>Math.abs(dy)?shortR:longR;
                d.setColor(0x7723433C);d.fillOval(px-rx*1.18f,py-ry*1.22f,px+rx*1.18f,py+ry*1.22f);
                d.setColor(0xAA326D68);d.fillOval(px-rx,py-ry,px+rx,py+ry);
                d.setColor(0x663FA8A6);d.fillOval(px-rx*.72f,py-ry*.58f,px+rx*.72f,py+ry*.58f);
                if(i==0){float ripple=.72f+.12f*(float)Math.sin(elapsed*2.1f+cell);d.setColor(0x7759C7C4);d.strokeWidth=1f*ui;d.strokeCircle(px-rx*.15f,py,Math.max(2f*ui,shortR*ripple));}
                d.setColor(0x554D7A45);for(int g=0;g<3;g++){float gx=px+nx*(shortR*1.35f)+dx/len*(g-1)*3f*ui,gy=py+ny*(shortR*1.35f)+dy/len*(g-1)*3f*ui;d.fillCircle(gx,gy,(1.3f+g*.35f)*ui);}
            }
            wet++;if(wet>11)return;
        }
    }
    private void drawCaveDecor''',
    'puddle corridors')

# 6) More than one repeated grass motif: tufts, moss, ivy and mushrooms, all deterministic per cell.
sub_once(
    r'''    private void drawCaveDecor\(Draw d\)\{.*?\n    \}\n    private boolean isDarkCell''',
    '''    private void drawCaveDecor(Draw d){
        if(workers.size()>105)return;
        for(int r=0;r<map.rows;r++)for(int c=0;c<map.cols;c++){
            int cell=map.index(c,r);long q=map.seed^cell*0x9E3779B97F4A7C15L;float x=cx(c),y=cy(r),k=hash01(q);
            if(k<.085f)drawPlantPatch(d,x,y,cell,(int)(hash01(q^0xA24BAED4963EE407L)*4f));
            else if(k>.945f){float drip=((elapsed*(8+cell%5)+cell*17)%32)*ui;d.setColor(0x665CBCE3);d.strokeWidth=1f*ui;d.line(x,y-18f*ui,x,y-8f*ui+drip*.25f);d.fillOval(x-1.2f*ui,y-8f*ui+drip*.45f,x+1.2f*ui,y-4f*ui+drip*.45f);}
            if(cell%37==state.depth%37&&r<map.rows/2){d.setColor(0x18DDF6D7);d.pathReset();d.moveTo(x-24f*ui,worldT);d.lineTo(x+7f*ui,worldT);d.lineTo(x+26f*ui,y+34f*ui);d.lineTo(x-12f*ui,y+34f*ui);d.closePath();d.fillPath();}
        }
    }
    private void drawPlantPatch(Draw d,float x,float y,int cell,int variant){
        float sway=(float)Math.sin(elapsed*.9f+cell)*1.2f*ui;
        switch(variant){
            case 0 -> { // thin grass tuft
                d.setColor(0xFF365F3C);d.strokeWidth=1.25f*ui;for(int i=0;i<5;i++){float ox=(i-2)*3f*ui;d.line(x+ox,y+8f*ui,x+ox*.72f+sway*(i%2==0?1:-1),y-7f*ui-(i%3)*3f*ui);}d.setColor(0xFF598053);for(int i=0;i<4;i++)d.fillCircle(x-5f*ui+i*3.5f*ui,y+1f*ui-(i%2)*4f*ui,1.7f*ui);}
            case 1 -> { // low moss carpet
                d.setColor(0xAA294C31);d.fillOval(x-12f*ui,y+2f*ui,x+12f*ui,y+9f*ui);int[] cols={0xFF3E6D40,0xFF527A47,0xFF315B39};for(int i=0;i<7;i++){d.setColor(cols[i%cols.length]);float ox=(hash01(cell*91L+i*17L)-.5f)*20f*ui,oy=(hash01(cell*71L+i*29L)-.5f)*7f*ui;d.fillCircle(x+ox,y+4f*ui+oy,(1.6f+i%3*.55f)*ui);}}
            case 2 -> { // hanging ivy
                d.setColor(0xFF315A38);d.strokeWidth=1.4f*ui;float px=x-7f*ui;for(int i=0;i<4;i++){float yy=y-13f*ui+i*7f*ui,nx=x-7f*ui+(i%2==0?4f:-2f)*ui+sway*.35f;d.line(px,yy,nx,yy+7f*ui);px=nx;d.setColor(i%2==0?0xFF4D7A49:0xFF3D6842);d.fillOval(nx-3f*ui,yy+2f*ui,nx+3f*ui,yy+6f*ui);d.setColor(0xFF315A38);}}
            default -> { // tiny cave mushrooms among short grass
                d.setColor(0xFF3B643D);d.strokeWidth=1.2f*ui;for(int i=0;i<3;i++)d.line(x-7f*ui+i*5f*ui,y+8f*ui,x-5f*ui+i*5f*ui,y-2f*ui-i*2f*ui);for(int i=0;i<3;i++){float mx=x-6f*ui+i*6f*ui,my=y+5f*ui-(i%2)*3f*ui;d.setColor(0xFFC6B38B);d.fillRect(mx-1f*ui,my-1f*ui,mx+1f*ui,my+4f*ui);d.setColor(i==1?0xFF8B5D52:0xFF6E7650);d.fillOval(mx-4f*ui,my-4f*ui,mx+4f*ui,my+1f*ui);}}
        }
    }
    private boolean isDarkCell''',
    'plant variety')

# 7) Collapse drawing shrinks with remaining rubble HP so passive settling is visible.
sub_once(
    r'''        float settle=Math\.min\(1f,\(h\.age-1\.25f\)/\.55f\);\n        d\.setColor\(0xAA171411\);d\.fillOval\(h\.x-h\.r\*\.88f,h\.y-h\.r\*\.10f,h\.x\+h\.r\*\.88f,h\.y\+h\.r\*\.48f\);\n        for\(int i=0;i<17;i\+\+\)\{\n            float q=hash01\(h\.cell\*971L\+i\*71L\),a=i\*2\.17f\+h\.cell\*\.31f;float rr=h\.r\*\(\.10f\+\.72f\*q\),sz=\(4\.5f\+i%5\*1\.8f\)\*ui\*settle;''',
    '''        float settle=Math.min(1f,(h.age-1.25f)/.55f),pct=h.rubbleMaxHp<=0?1f:Math.max(0,Math.min(1,h.rubbleHp/h.rubbleMaxHp));
        float shrink=.32f+.68f*(float)Math.sqrt(pct);int stones=Math.max(4,Math.round(17*pct));
        d.setColor(0xAA171411);d.fillOval(h.x-h.r*.88f*shrink,h.y-h.r*.10f,h.x+h.r*.88f*shrink,h.y+h.r*.48f*shrink);
        for(int i=0;i<stones;i++){
            float q=hash01(h.cell*971L+i*71L),a=i*2.17f+h.cell*.31f;float rr=h.r*(.10f+.72f*q)*shrink,sz=(4.5f+i%5*1.8f)*ui*settle*shrink;''',
    'collapse visual decay')

# 8) Flood hazard is now a moving stream made of irregular water masses/foam, not a thick straight stripe.
sub_once(
    r'''    private void drawFloodHazard\(Draw d,CaveHazard h,float warning\)\{.*?\n    \}\n\n    private void drawFx''',
    '''    private void drawFloodHazard(Draw d,CaveHazard h,float warning){
        int dir=floodDirection(h.cell);float ux=CaveMap.dx(dir),uy=CaveMap.dy(dir),px=-uy,py=ux;
        float t=Math.max(0,h.age-1.25f),len=h.r*.78f;
        if(h.age<1.25f){
            for(int i=0;i<7;i++){float q=i/6f,along=(q-.5f)*len*1.7f,wob=(float)Math.sin(i*1.7f+h.age*9f)*h.r*.10f;float x=h.x+ux*along+px*wob,y=h.y+uy*along+py*wob;d.setColor(alpha(0xFF75D5DD,.18f+.40f*warning));d.fillCircle(x,y,(1.5f+warning*2.3f+i%2)*ui);}
            return;
        }
        // Dark wet bed first. The visible water itself is a broken chain of translucent moving pools.
        for(int i=0;i<11;i++){
            float q=i/10f,along=(q-.5f)*len*2f,wob=(float)Math.sin(i*1.19f+t*4.3f)*h.r*.09f;
            float x=h.x+ux*along+px*wob,y=h.y+uy*along+py*wob;
            float longR=h.r*(.13f+.025f*(i%3)),shortR=h.r*(.075f+.012f*((i+1)%3));
            float rx=Math.abs(ux)*longR+Math.abs(px)*shortR,ry=Math.abs(uy)*longR+Math.abs(py)*shortR;
            d.setColor(0x55325457);d.fillOval(x-rx*1.15f,y-ry*1.15f,x+rx*1.15f,y+ry*1.15f);
            d.setColor(i%2==0?0xA345909C:0x994A9FAC);d.fillOval(x-rx,y-ry,x+rx,y+ry);
            d.setColor(0x555FC5CC);d.fillOval(x-rx*.72f,y-ry*.60f,x+rx*.72f,y+ry*.60f);
        }
        for(int i=0;i<13;i++){
            float q=(i*.091f+t*(.40f+.025f*(i%4)))%1f,along=(q-.5f)*len*2f,wob=(float)Math.sin(i*2.2f+t*6f)*h.r*.10f;
            float x=h.x+ux*along+px*wob,y=h.y+uy*along+py*wob;
            d.setColor(i%3==0?0xCCDEFFFF:0x8875D9E0);d.fillCircle(x,y,(1.1f+i%3*.55f)*ui);
        }
        // Small eddy at the downstream edge sells direction without drawing a giant blue bar.
        float ex=h.x+ux*len*.72f,ey=h.y+uy*len*.72f;d.setColor(0x775FD0D6);d.strokeWidth=1.1f*ui;d.strokeCircle(ex,ey,h.r*(.12f+.02f*(float)Math.sin(t*4f)));
    }
    private int floodDirection(int cell){
        int c=map.col(cell),r=map.row(cell);int[] order={(cell&1)==0?CaveMap.E:CaveMap.S,CaveMap.N,CaveMap.W,CaveMap.S,CaveMap.E};
        for(int dir:order)if(map.connected(c,r,dir))return dir;return CaveMap.E;
    }

    private void drawFx''',
    'flood visual')

p.write_text(s)
print('v0.6.4 cave visuals/gameplay patch applied')
