from pathlib import Path
import re

screen_path = Path('core/src/main/java/com/enhort/gnomes/game/CaveScreen.java')
map_path = Path('core/src/main/java/com/enhort/gnomes/game/CaveMap.java')
s = screen_path.read_text()
m = map_path.read_text()

def sub_screen(pattern, repl, label, flags=re.S):
    global s
    out, n = re.subn(pattern, repl, s, count=1, flags=flags)
    if n != 1:
        raise SystemExit(f'{label}: expected 1 replacement, got {n}')
    s = out

def sub_map(pattern, repl, label, flags=re.S):
    global m
    out, n = re.subn(pattern, repl, m, count=1, flags=flags)
    if n != 1:
        raise SystemExit(f'{label}: expected 1 replacement, got {n}')
    m = out

# User commands must outrank everything else. Rubble is no longer an automatic obsession:
# gnomes clear it only when explicitly ordered, while rubble still settles by itself over time.
# Regular mining assignments are balanced across veins instead of piling onto the nearest rock.
sub_screen(
    r'''    private void updateWorkers\(float dt\)\{.*?\n    \}\n\n    private CaveHazard firstActiveCollapse''',
    '''    private void updateWorkers(float dt){
        int defendersLeft=state.guardianLevel>0?guardianDefenderQuota():workers.size();
        int[] veinLoad=new int[veins.size()];
        for(Worker worker:workers){int vi=veinIndex(worker.vein);if(vi>=0&&!worker.vein.dead&&!map.isBlocked(worker.vein.cell))veinLoad[vi]++;}

        for(int i=workers.size()-1;i>=0;i--){
            Worker w=workers.get(i);
            w.spawn=Math.max(0,w.spawn-dt);w.attackCooldown-=dt;w.allyCooldown-=dt;w.charm=Math.max(0,w.charm-dt);if(w.swing>0)w.swing=Math.max(0,w.swing-dt);if(w.stun>0)w.stun-=dt;if(w.routeRetry>0)w.routeRetry-=dt;
            if(w.spawn>0){w.action=WorkerAction.IDLE;w.vx=w.vy=0;continue;}
            if(w.stun>0){w.action=WorkerAction.STUNNED;w.vx=w.vy=0;continue;}
            if(w.charm>0){fightAlly(w,dt);continue;}

            // Direct player orders always win. No ambient rubble job is allowed to hijack them.
            if(priorityKind==PriorityKind.VEIN&&priorityVein!=null&&!priorityVein.dead){w.mob=null;w.vein=priorityVein;mine(w,priorityVein,dt);continue;}
            if(priorityKind==PriorityKind.MOB&&priorityMob!=null&&!priorityMob.dead){w.vein=null;w.mob=priorityMob;fight(w,priorityMob,dt);continue;}
            if(priorityKind==PriorityKind.HAZARD&&priorityHazard!=null&&!priorityHazard.cleared&&priorityHazard.obstacleActive){w.vein=null;w.mob=null;clearCollapse(w,priorityHazard,dt);continue;}
            if(priorityKind==PriorityKind.POINT&&priorityCell>=0){w.vein=null;w.mob=null;moveToPriorityPoint(w,dt);continue;}

            Mob enemy=null;if(defendersLeft>0)enemy=nearestMob(w.x,w.y);
            if(enemy!=null){defendersLeft--;w.mob=enemy;w.vein=null;fight(w,enemy,dt);continue;}

            w.mob=null;
            float cap=w.tier.cargoCapacity*state.carryMultiplier(w.tier.ordinal());
            if(w.hasCargo()&&w.cargo()>=cap*.92){carryHome(w,dt);continue;}

            int old=veinIndex(w.vein);
            boolean invalid=old<0||w.vein.dead||map.isBlocked(w.vein.cell);
            if(!invalid&&veinLoad[old]>1&&hasLowerLoadReachableVein(w,veinLoad,veinLoad[old]))invalid=true;
            if(invalid){
                if(old>=0&&old<veinLoad.length&&veinLoad[old]>0)veinLoad[old]--;
                w.vein=null;w.path=new int[0];w.pathIndex=0;w.goalCell=-1;
            }
            if(w.vein==null){
                w.vein=chooseVein(w,veinLoad);
                int ni=veinIndex(w.vein);if(ni>=0)veinLoad[ni]++;
            }
            if(w.vein!=null)mine(w,w.vein,dt); else if(w.hasCargo())carryHome(w,dt); else {w.action=WorkerAction.IDLE;w.vx=w.vy=0;}
        }
    }

    private CaveHazard firstActiveCollapse''',
    'worker priorities and vein distribution')

# If a temporary danger makes the chosen vein unreachable, release the assignment instead of standing forever.
sub_screen(
    r'''    private void mine\(Worker w,Vein v,float dt\)\{\n        if\(!atCell\(w,v\.cell\)\)\{w\.action=WorkerAction\.WALK;routeWorker\(w,v\.cell\);followWorker\(w,moveSpeed\(w\),dt\);return;\}''',
    '''    private void mine(Worker w,Vein v,float dt){
        if(!atCell(w,v.cell)){
            w.action=WorkerAction.WALK;routeWorker(w,v.cell);
            if(w.path.length==0){w.vein=null;w.goalCell=-1;w.vx=w.vy=0;w.action=WorkerAction.IDLE;return;}
            followWorker(w,moveSpeed(w),dt);return;
        }''',
    'release unreachable vein')

# Replace distance-dominated mining choice with load-first assignment. Cached normal paths are used when
# there is no temporary hazard mask, so 100+ gnomes do not turn balancing into a BFS benchmark.
sub_screen(
    r'''    private Vein chooseVein\(Worker w\)\{.*?\n    \}\n    private void updatePortal''',
    '''    private int veinIndex(Vein v){return v==null?-1:veins.indexOf(v);}
    private boolean hasLowerLoadReachableVein(Worker w,int[] loads,int currentLoad){
        int start=cellFor(w.x,w.y);
        for(int i=0;i<veins.size();i++){
            Vein v=veins.get(i);if(v.dead||map.isBlocked(v.cell)||loads[i]+1>=currentLoad)continue;
            if(map.path(start,v.cell).length>0)return true;
        }
        return false;
    }
    private Vein chooseVein(Worker w,int[] loads){
        Vein best=null;float bestScore=Float.MAX_VALUE;int start=cellFor(w.x,w.y);boolean[] avoid=dangerMask(w);
        for(int i=0;i<veins.size();i++){
            Vein v=veins.get(i);if(v.dead||map.isBlocked(v.cell))continue;
            int[] route=avoid==null?map.path(start,v.cell):map.pathAvoiding(start,v.cell,avoid);if(route.length==0)continue;
            // One extra worker on a vein costs far more than a few cells of walking. This produces squads,
            // not a hundred beards all queueing for the same pebble.
            float personal=Math.floorMod(w.visualId*31+v.seed,29)*3f;
            float score=loads[i]*100000f+route.length*420f+personal;
            if(score<bestScore){bestScore=score;best=v;}
        }
        return best;
    }
    private void updatePortal''',
    'balanced chooseVein')

# Replace rectilinear tunnel/wet-corridor rendering with organic curved edges, rounded junctions and
# irregular puddles. Some corridors are deliberately wet from wall to wall.
sub_screen(
    r'''    private void drawTunnels\(Draw d\)\{.*?\n    \}\n    private void drawCaveDecor''',
    '''    private void drawTunnels(Draw d){
        float base=Math.min(cellW,cellH),outer=base*.50f,mid=base*.39f,inner=base*.30f;
        drawOrganicTunnelLayer(d,outer,0xFF4A4035,0x13A5B357L);
        drawOrganicTunnelLayer(d,mid,0xFF292722,0x4C957F2DL);
        drawOrganicTunnelLayer(d,inner,0xFF36312A,0x7F4A7C15L);
        for(int r=0;r<map.rows;r++)for(int c=0;c<map.cols;c++){
            int idx=map.index(c,r),degree=map.degree(c,r);if(degree<=0)continue;
            boolean light=(idx+state.depth*3)%14==0&&!isDarkCell(idx);
            if(light){
                if(isGlowMossLight(idx))drawGlowMoss(d,cx(c),cy(r),idx,inner);
                else drawTorch(d,cx(c)-inner*.26f,cy(r)-inner*.38f,idx);
            }
            if((idx*7+state.depth)%19==0){d.setColor(0xFF6A4A31);d.strokeWidth=2f*ui;d.line(cx(c)-inner*.45f,cy(r)-inner*.48f,cx(c)-inner*.45f,cy(r)+inner*.48f);d.line(cx(c)+inner*.45f,cy(r)-inner*.48f,cx(c)+inner*.45f,cy(r)+inner*.48f);d.line(cx(c)-inner*.52f,cy(r)-inner*.34f,cx(c)+inner*.52f,cy(r)-inner*.34f);}
        }
    }
    private void drawOrganicTunnelLayer(Draw d,float width,int color,long salt){
        d.setColor(color);d.strokeWidth=width;
        for(int r=0;r<map.rows;r++)for(int c=0;c<map.cols;c++){
            int cell=map.index(c,r);
            if(map.connected(c,r,CaveMap.E))drawOrganicTunnelEdge(d,cx(c),cy(r),cx(c+1),cy(r),cell,salt^0x51ED270BL);
            if(map.connected(c,r,CaveMap.S))drawOrganicTunnelEdge(d,cx(c),cy(r),cx(c),cy(r+1),cell,salt^0xB5297A4DL);
        }
        // Round every join, not only crossroads. Dead ends become small alcoves instead of chopped-off pipes.
        for(int r=0;r<map.rows;r++)for(int c=0;c<map.cols;c++){
            int degree=map.degree(c,r);if(degree<=0)continue;float x=cx(c),y=cy(r);d.setColor(color);
            if(degree==1){
                int open=firstOpening(c,r);float ex=-CaveMap.dx(open)*width*.12f,ey=-CaveMap.dy(open)*width*.12f;
                d.fillOval(x-width*.66f+ex,y-width*.56f+ey,x+width*.66f+ex,y+width*.56f+ey);
            }else d.fillCircle(x,y,width*.52f);
        }
    }
    private void drawOrganicTunnelEdge(Draw d,float x1,float y1,float x2,float y2,int cell,long salt){
        float dx=x2-x1,dy=y2-y1,di=Math.max(1f,len(dx,dy)),nx=-dy/di,ny=dx/di;
        float bend=(hash01(map.seed^salt^(long)cell*0x9E3779B97F4A7C15L)-.5f)*Math.min(cellW,cellH)*.12f;
        float mx=(x1+x2)*.5f+nx*bend,my=(y1+y2)*.5f+ny*bend;
        d.pathReset();d.moveTo(x1,y1);d.quadTo(mx,my,x2,y2);d.strokePath();
    }
    private int firstOpening(int c,int r){int bits=map.openings[r][c];if((bits&CaveMap.N)!=0)return CaveMap.N;if((bits&CaveMap.E)!=0)return CaveMap.E;if((bits&CaveMap.S)!=0)return CaveMap.S;return CaveMap.W;}
    private boolean isGlowMossLight(int cell){return hash01(map.seed^0xC6BC279692B5CC83L^(long)cell*0xD1B54A32D192ED03L)<.42f;}
    private void drawGlowMoss(Draw d,float x,float y,int cell,float inner){
        float side=((cell&1)==0?-1f:1f),px=x+side*inner*.36f,py=y-inner*.30f;
        float pulse=.82f+.18f*(float)Math.sin(elapsed*2.4f+cell*.71f);
        d.setColor(0x1432F1C2);d.fillCircle(px,py,inner*.74f*pulse);d.setColor(0x2256F7D1);d.fillCircle(px,py,inner*.42f*pulse);
        d.setColor(0xFF285C45);d.strokeWidth=1.3f*ui;for(int i=0;i<4;i++){float ox=(i-1.5f)*3.5f*ui;d.line(px+ox,py-7f*ui,px+ox+side*(i%2==0?2:-2)*ui,py+8f*ui);}
        int[] cols={0xFF53D58E,0xFF6AF4BB,0xFF3CBDA0,0xFF9AEDD0};
        for(int i=0;i<9;i++){long h=cell*131L+i*47L;float ox=(hash01(h)-.5f)*18f*ui,oy=(hash01(h^0x55AA55AAL)-.5f)*18f*ui;d.setColor(cols[i%cols.length]);d.fillCircle(px+ox,py+oy,(1.4f+(i%3)*.55f)*ui*pulse);}
    }
    private void drawWetCorridors(Draw d){
        float base=Math.min(cellW,cellH);int wet=0;
        for(int r=0;r<map.rows;r++)for(int c=0;c<map.cols;c++){
            int cell=map.index(c,r);long h=map.seed^cell*0x94D049BB133111EBL;if(hash01(h)>.13f)continue;
            int dir=map.connected(c,r,CaveMap.E)?CaveMap.E:(map.connected(c,r,CaveMap.S)?CaveMap.S:0);if(dir==0)continue;
            float x1=cx(c),y1=cy(r),x2=cx(c+CaveMap.dx(dir)),y2=cy(r+CaveMap.dy(dir));
            float dx=x2-x1,dy=y2-y1,di=Math.max(1f,len(dx,dy)),nx=-dy/di,ny=dx/di;
            boolean soaked=hash01(h^0xA24BAED4963EE407L)<.28f;
            if(soaked){
                drawWetRibbonLayer(d,x1,y1,x2,y2,base*.125f,h,0x77304A43);
                drawWetRibbonLayer(d,x1,y1,x2,y2,base*.090f,h^0x9E3779B9L,0xAA34756E);
                drawWetRibbonLayer(d,x1,y1,x2,y2,base*.045f,h^0x85EBCA6BL,0x554AB4AD);
                for(int i=0;i<6;i++){float q=(i+.5f)/6f,px=x1+dx*q+nx*(float)Math.sin(i*1.7f+cell)*base*.035f,py=y1+dy*q+ny*(float)Math.sin(i*1.7f+cell)*base*.035f;d.setColor(0x665FD0CB);d.fillCircle(px,py,(1.1f+i%3*.45f)*ui);}
            }else{
                int puddles=2+(int)(hash01(h^0x632BE59BD9B4E019L)*3f);
                for(int i=0;i<puddles;i++){
                    float q=.16f+(i+.5f)/puddles*.68f,jitter=(hash01(h+i*137L)-.5f)*base*.18f;
                    float px=x1+dx*q+nx*jitter,py=y1+dy*q+ny*jitter;
                    float longR=base*(.075f+.075f*hash01(h+i*271L)),shortR=base*(.035f+.045f*hash01(h+i*353L));
                    float rx=Math.abs(dx)>Math.abs(dy)?longR:shortR,ry=Math.abs(dx)>Math.abs(dy)?shortR:longR;
                    drawIrregularPuddle(d,px,py,rx,ry,h+i*911L);
                }
            }
            wet++;if(wet>12)return;
        }
    }
    private void drawWetRibbonLayer(Draw d,float x1,float y1,float x2,float y2,float half,long seed,int color){
        float dx=x2-x1,dy=y2-y1,di=Math.max(1f,len(dx,dy)),nx=-dy/di,ny=dx/di;int n=7;d.pathReset();
        for(int i=0;i<n;i++){float q=i/(float)(n-1),w=half*(.72f+.36f*hash01(seed+i*71L)),wave=(hash01(seed+i*131L)-.5f)*half*.40f;float x=x1+dx*q+nx*(w+wave),y=y1+dy*q+ny*(w+wave);if(i==0)d.moveTo(x,y);else d.lineTo(x,y);}
        for(int i=n-1;i>=0;i--){float q=i/(float)(n-1),w=half*(.72f+.36f*hash01(seed^0x55AA55AAL+i*83L)),wave=(hash01(seed^0xA5A5A5A5L+i*149L)-.5f)*half*.40f;d.lineTo(x1+dx*q-nx*(w+wave),y1+dy*q-ny*(w+wave));}
        d.closePath();d.setColor(color);d.fillPath();
    }
    private void drawIrregularPuddle(Draw d,float x,float y,float rx,float ry,long seed){
        drawPuddleLayer(d,x,y,rx*1.14f,ry*1.18f,seed,0x66304B43);
        drawPuddleLayer(d,x,y,rx,ry,seed^0x9E3779B97F4A7C15L,0xAA37766E);
        drawPuddleLayer(d,x,y,rx*.67f,ry*.54f,seed^0xBF58476D1CE4E5B9L,0x554BB4AF);
        float ripple=.70f+.10f*(float)Math.sin(elapsed*1.8f+(seed&31));d.setColor(0x665ED1C9);d.strokeWidth=1f*ui;d.strokeCircle(x-rx*.10f,y,Math.max(1.8f*ui,Math.min(rx,ry)*ripple));
    }
    private void drawPuddleLayer(Draw d,float x,float y,float rx,float ry,long seed,int color){
        int n=10;d.pathReset();for(int i=0;i<n;i++){float a=(float)(Math.PI*2*i/n),j=.72f+.38f*hash01(seed+i*97L);float px=x+(float)Math.cos(a)*rx*j,py=y+(float)Math.sin(a)*ry*(.78f+.34f*hash01(seed+i*163L));if(i==0)d.moveTo(px,py);else d.lineTo(px,py);}d.closePath();d.setColor(color);d.fillPath();
    }
    private void drawCaveDecor''',
    'organic tunnels puddles and glowing moss')

# Reduce graph dead ends so the mine feels like a cave network rather than a collection of chopped pipes.
sub_map(
    r'''        ensureStartJunction\(\);\n        if \(style == Style\.RING\) carveRing\(\);\n    \}''',
    '''        ensureStartJunction();
        if (style == Style.RING) carveRing();
        softenDeadEnds();
    }''',
    'call softenDeadEnds')

sub_map(
    r'''    private void ensureStartJunction\(\) \{''',
    '''    private void softenDeadEnds() {
        List<Integer> ends = new ArrayList<>();
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) {
            if (c == startCol && r == startRow) continue;
            if (degree(c, r) == 1) ends.add(index(c, r));
        }
        Collections.shuffle(ends, new Random(seed ^ 0xD6E8FEB86659FD93L));
        int connectCount = Math.max(0, Math.round(ends.size() * .62f));
        for (int i = 0; i < connectCount; i++) {
            int cell = ends.get(i), c = col(cell), r = row(cell);
            if (degree(c, r) != 1) continue;
            int[] order = {N, E, S, W};
            shuffle(order);
            int bestDir = 0, bestScore = Integer.MIN_VALUE;
            for (int dir : order) {
                if ((openings[r][c] & dir) != 0) continue;
                int nc = c + dx(dir), nr = r + dy(dir);
                if (!inside(nc, nr)) continue;
                int score = degree(nc, nr) * 5;
                if (nc > 0 && nc < cols - 1 && nr > 0 && nr < rows - 1) score += 3;
                if (score > bestScore) { bestScore = score; bestDir = dir; }
            }
            if (bestDir != 0) connect(c, r, c + dx(bestDir), r + dy(bestDir), bestDir);
        }
    }

    private void ensureStartJunction() {''',
    'softenDeadEnds method', flags=0)

screen_path.write_text(s)
map_path.write_text(m)
print('v0.6.5 tunnel/worker patch applied')
