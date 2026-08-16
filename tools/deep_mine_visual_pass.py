from pathlib import Path
import re

CAVE = Path('core/src/main/java/com/enhort/gnomes/game/CaveScreen.java')
s = CAVE.read_text()


def sub(pattern, repl):
    global s
    ns, n = re.subn(pattern, repl, s, count=1, flags=re.S)
    if n != 1:
        raise SystemExit('pattern not found exactly once: ' + pattern[:180])
    s = ns


def rep(old, new):
    global s
    if old not in s:
        raise SystemExit('literal not found: ' + old[:180])
    s = s.replace(old, new, 1)

# ---------------------------------------------------------------------------
# Performance: keep effects bounded more aggressively with large populations.
# ---------------------------------------------------------------------------
rep(
'''    private void updateFx(float dt){for(Iterator<Fx>it=fx.iterator();it.hasNext();){Fx p=it.next();p.life-=dt;if(p.life<=0){it.remove();continue;}p.x+=p.vx*dt;p.y+=p.vy*dt;if(!p.spark)p.vy+=45f*ui*dt;else{p.vx*=Math.max(0,1-dt*3);p.vy*=Math.max(0,1-dt*3);}}int limit=workers.size()>120?170:workers.size()>70?230:420;if(fx.size()>limit)fx.subList(0,fx.size()-limit).clear();}
    private void spawnRockHit(Vein v,int power){int n=workers.size()>120?1:workers.size()>70?2:5+Math.min(5,power);for(int i=0;i<n;i++){float a=random.nextFloat()*6.28f,s=(25+random.nextFloat()*65+power*7)*ui;fx.add(new Fx(v.x,v.y,(float)Math.cos(a)*s,(float)Math.sin(a)*s-.2f*s,.35f+random.nextFloat()*.35f,(1.1f+random.nextFloat()*2.2f)*ui,adjust(v.type.color,.75f+random.nextFloat()*.35f),false));}}
    private void spawnBreak(Vein v){for(int i=0;i<22;i++){float a=random.nextFloat()*6.28f,s=(30+random.nextFloat()*110)*ui;fx.add(new Fx(v.x,v.y,(float)Math.cos(a)*s,(float)Math.sin(a)*s-35f*ui,.45f+random.nextFloat()*.65f,(1.5f+random.nextFloat()*3.4f)*ui,adjust(v.type.color,.65f+random.nextFloat()*.5f),false));}}
    private void spawnDeath(Mob m){for(int i=0;i<18+(m.type.ordinal()>=EnemyType.IMP_KING.ordinal()?16:0);i++){float a=random.nextFloat()*6.28f,s=(35+random.nextFloat()*95)*ui;fx.add(new Fx(m.x,m.y,(float)Math.cos(a)*s,(float)Math.sin(a)*s,.45f+random.nextFloat()*.5f,(1.5f+random.nextFloat()*3f)*ui,m.type.color,false));}}
    private void spawnSparks(float x,float y,int color,int n){for(int i=0;i<n;i++){float a=random.nextFloat()*6.28f,s=(25+random.nextFloat()*85)*ui;fx.add(new Fx(x,y,(float)Math.cos(a)*s,(float)Math.sin(a)*s,.18f+random.nextFloat()*.3f,(1+random.nextFloat()*1.7f)*ui,color,true));}}''',
'''    private void updateFx(float dt){for(Iterator<Fx>it=fx.iterator();it.hasNext();){Fx p=it.next();p.life-=dt;if(p.life<=0){it.remove();continue;}p.x+=p.vx*dt;p.y+=p.vy*dt;if(!p.spark)p.vy+=45f*ui*dt;else{p.vx*=Math.max(0,1-dt*3);p.vy*=Math.max(0,1-dt*3);}}int limit=workers.size()>120?120:workers.size()>90?165:workers.size()>70?215:400;if(fx.size()>limit)fx.subList(0,fx.size()-limit).clear();}
    private int fxBudget(int normal,int crowded){return workers.size()>90?crowded:normal;}
    private void spawnRockHit(Vein v,int power){int n=workers.size()>120?1:workers.size()>80?2:5+Math.min(5,power);for(int i=0;i<n;i++){float a=random.nextFloat()*6.28f,sp=(25+random.nextFloat()*65+power*7)*ui;fx.add(new Fx(v.x,v.y,(float)Math.cos(a)*sp,(float)Math.sin(a)*sp-.2f*sp,.35f+random.nextFloat()*.35f,(1.1f+random.nextFloat()*2.2f)*ui,adjust(v.type.color,.75f+random.nextFloat()*.35f),false));}}
    private void spawnBreak(Vein v){int n=fxBudget(18,7);for(int i=0;i<n;i++){float a=random.nextFloat()*6.28f,sp=(30+random.nextFloat()*110)*ui;fx.add(new Fx(v.x,v.y,(float)Math.cos(a)*sp,(float)Math.sin(a)*sp-35f*ui,.45f+random.nextFloat()*.65f,(1.5f+random.nextFloat()*3.4f)*ui,adjust(v.type.color,.65f+random.nextFloat()*.5f),false));}}
    private void spawnDeath(Mob m){int n=fxBudget(16+(m.type.ordinal()>=EnemyType.IMP_KING.ordinal()?12:0),6+(m.type.ordinal()>=EnemyType.IMP_KING.ordinal()?5:0));for(int i=0;i<n;i++){float a=random.nextFloat()*6.28f,sp=(35+random.nextFloat()*95)*ui;fx.add(new Fx(m.x,m.y,(float)Math.cos(a)*sp,(float)Math.sin(a)*sp,.45f+random.nextFloat()*.5f,(1.5f+random.nextFloat()*3f)*ui,m.type.color,false));}}
    private void spawnSparks(float x,float y,int color,int n){n=Math.min(n,fxBudget(n,Math.max(1,n/2)));for(int i=0;i<n;i++){float a=random.nextFloat()*6.28f,sp=(25+random.nextFloat()*85)*ui;fx.add(new Fx(x,y,(float)Math.cos(a)*sp,(float)Math.sin(a)*sp,.18f+random.nextFloat()*.3f,(1+random.nextFloat()*1.7f)*ui,color,true));}}''')

# ---------------------------------------------------------------------------
# Spawn/readability animation. Pure drawing, zero per-frame allocations.
# ---------------------------------------------------------------------------
sub(
    r'    private void drawVeins\(Draw d\)\{.*?\n    private void drawPriorityMarker',
'''    private void drawVeins(Draw d){for(Vein v:veins)if(!v.dead||v.death<.55f){drawVein(d,v);if(priorityKind==PriorityKind.VEIN&&v==priorityVein&&!v.dead)drawPriorityMarker(d,v);}}
    private float appearScale(float remaining,float duration){float p=1f-Math.min(1f,Math.max(0f,remaining/duration));p=ease(p);return .18f+.82f*p;}
    private void drawArrivalRing(Draw d,float x,float y,float radius,float remaining,float duration,int color){
        if(remaining<=0)return;float p=1f-Math.min(1f,remaining/duration),fade=1f-p;float r=radius*(.55f+p*.85f);
        d.setColor(alpha(color,.18f+.45f*fade));d.fillCircle(x,y,r*.50f);
        d.setColor(alpha(color,.35f+.55f*fade));d.strokeWidth=(1f+fade*1.6f)*ui;d.strokeCircle(x,y,r);
        for(int i=0;i<4;i++){float a=i*1.5708f+elapsed*4.5f;d.fillCircle(x+(float)Math.cos(a)*r,y+(float)Math.sin(a)*r,(1.2f+fade*1.4f)*ui);}
    }
    private void drawPriorityMarker''')

# Veins grow out of the wall instead of popping into existence.
rep(
'''    private void drawVein(Draw d,Vein v){
        float death=v.dead?Math.max(0,1-v.death/.55f):1;float damage=1-Math.max(0,v.hp)/v.maxHp;float shake=v.hitFlash>0?(float)Math.sin(v.hitFlash*210f)*2.1f*ui*(v.hitFlash/.16f):0;
        d.save();d.translate(shake,0);d.scale(death,death);float x=v.x,y=v.y,r=v.r;''',
'''    private void drawVein(Draw d,Vein v){
        float death=v.dead?Math.max(0,1-v.death/.55f):1;float damage=1-Math.max(0,v.hp)/v.maxHp;float shake=v.hitFlash>0?(float)Math.sin(v.hitFlash*210f)*2.1f*ui*(v.hitFlash/.16f):0;float born=appearScale(v.spawn,.58f);float x=v.x,y=v.y,r=v.r;
        if(v.spawn>0)drawArrivalRing(d,x,y,r*1.25f,v.spawn,.58f,adjust(v.type.color,1.18f));
        d.save();d.translate(x,y);d.scale(death*born,death*born);d.translate(-x,-y);d.translate(shake,0);''')

# ---------------------------------------------------------------------------
# Guardian: proper armored gnome, shield, spear thrust and spawn animation.
# ---------------------------------------------------------------------------
sub(
    r'    private void drawGuardian\(Draw d,float x,float y,float s\)\{.*?\n    \}\n\n    private void drawWorker',
'''    private void drawGuardian(Draw d,float x,float y,float s){
        float bob=(float)Math.sin(elapsed*3.1f)*1.1f*ui,attack=guardianAttackAnim>0?(float)Math.sin((.34f-guardianAttackAnim)/.34f*Math.PI):0;
        float appear=guardianSpawnAnim>0?appearScale(guardianSpawnAnim,.70f):1f;
        if(guardianSpawnAnim>0)drawArrivalRing(d,x,y,s*.80f,guardianSpawnAnim,.70f,0xFF70D7FF);
        d.save();d.translate(x,y+bob);d.scale(appear,appear);
        d.setColor(0x66000000);d.fillOval(-s*.38f,s*.48f,s*.40f,s*.62f);
        // boots and legs
        d.setColor(0xFF302A27);d.strokeWidth=s*.13f;d.line(-s*.13f,s*.25f,-s*.20f,s*.52f);d.line(s*.13f,s*.25f,s*.20f,s*.52f);d.fillOval(-s*.32f,s*.46f,-s*.08f,s*.58f);d.fillOval(s*.08f,s*.46f,s*.32f,s*.58f);
        // steel-blue coat and plate
        d.setColor(0xFF27495B);d.fillOval(-s*.31f,-s*.03f,s*.31f,s*.38f);d.setColor(0xFF718A98);d.fillRoundRect(-s*.24f,s*.02f,s*.24f,s*.30f,s*.05f);d.setColor(0xFFBCD0D8);d.strokeWidth=s*.045f;d.line(-s*.17f,s*.05f,s*.17f,s*.25f);d.line(s*.17f,s*.05f,-s*.17f,s*.25f);
        // head, nose, beard
        d.setColor(0xFFE4B483);d.fillCircle(0,-s*.22f,s*.22f);d.fillCircle(s*.20f,-s*.20f,s*.055f);d.setColor(0xFFE2E4E2);d.pathReset();d.moveTo(-s*.20f,-s*.11f);d.lineTo(0,s*.23f);d.lineTo(s*.21f,-s*.11f);d.lineTo(s*.09f,s*.10f);d.lineTo(-s*.08f,s*.10f);d.closePath();d.fillPath();
        d.setColor(0xFF101314);d.fillCircle(s*.11f,-s*.27f,s*.025f);
        // helmet with nose guard
        d.setColor(0xFF7696A7);d.fillOval(-s*.27f,-s*.50f,s*.27f,-s*.27f);d.setColor(0xFFC9D7DE);d.fillRect(-s*.29f,-s*.35f,s*.29f,-s*.29f);d.fillRect(-s*.035f,-s*.36f,s*.035f,-s*.12f);d.setColor(0xFF476273);d.pathReset();d.moveTo(-s*.11f,-s*.50f);d.lineTo(0,-s*.67f);d.lineTo(s*.11f,-s*.50f);d.closePath();d.fillPath();
        // shield on left
        d.setColor(0xFF233641);d.pathReset();d.moveTo(-s*.34f,-s*.02f);d.lineTo(-s*.60f,s*.05f);d.lineTo(-s*.55f,s*.38f);d.lineTo(-s*.34f,s*.52f);d.lineTo(-s*.15f,s*.34f);d.closePath();d.fillPath();d.setColor(0xFF9DB2BC);d.strokeWidth=s*.045f;d.line(-s*.52f,s*.10f,-s*.25f,s*.38f);d.line(-s*.25f,s*.10f,-s*.52f,s*.38f);d.setColor(UiTheme.GOLD);d.fillCircle(-s*.37f,s*.24f,s*.055f);
        // animated spear on right
        float thrust=attack*s*.32f;d.setColor(0xFFE4B483);d.fillCircle(s*.23f+thrust*.25f,s*.03f,s*.065f);d.setColor(0xFF75523A);d.strokeWidth=s*.065f;d.line(s*.20f,s*.02f,s*.82f+thrust,-s*.24f);d.setColor(0xFFDCE7EC);d.pathReset();d.moveTo(s*.76f+thrust,-s*.21f);d.lineTo(s*1.03f+thrust,-s*.37f);d.lineTo(s*.87f+thrust,-s*.10f);d.closePath();d.fillPath();d.setColor(0xFFFFFFFF);d.strokeWidth=s*.022f;d.line(s*.85f+thrust,-s*.28f,s*.98f+thrust,-s*.35f);
        if(state.guardianLevel>1){d.setColor(0xFF69D9F0);d.fillCircle(s*.02f,s*.11f,s*.045f);}
        d.restore();
    }

    private void drawWorker''')

# Worker spawn wrapper. Also keeps rune attached to the scale animation.
sub(
    r'    private void drawWorker\(Draw d,Worker w\)\{.*?\}\n\n    private float strikeProgress',
'''    private void drawWorker(Draw d,Worker w){
        float s=w.tier.size*ui,scale=appearScale(w.spawn,.52f);
        if(w.spawn>0)drawArrivalRing(d,w.x,w.y,s*.76f,w.spawn,.52f,w.tier.color);
        if(w.spawn>0){d.save();d.translate(w.x,w.y);d.scale(scale,scale);d.translate(-w.x,-w.y);}
        switch(w.tier){case MINER,VETERAN,TWIN_PICK->drawDwarf(d,w,s);case DRILL_RIG->drawDrill(d,w,s);case EXCAVATOR->drawExcavator(d,w,s);case IRON_GOLEM->drawIron(d,w,s);}
        int rune=state.tierRunes[w.tier.ordinal()];if(rune>=0&&rune<RuneType.values().length&&state.runeLevels[rune]>0)drawRune(d,w.x+s*.45f,w.y-s*.60f,4.4f*ui,RuneType.values()[rune]);
        if(w.spawn>0)d.restore();
    }

    private float strikeProgress''')

# Bigger, outlined pickaxe so it survives phone-scale rendering.
sub(
    r'    private void drawAnimatedPick\(Draw d,float x,float y,float s,float angle,float hand\)\{.*?\}\n    private void drawSack',
'''    private void drawAnimatedPick(Draw d,float x,float y,float s,float angle,float hand){
        d.save();d.translate(x,y);d.rotate(angle);
        d.setColor(0xFF3D291C);d.strokeWidth=s*.105f;d.line(0,0,s*.61f,s*.43f);
        d.setColor(0xFF8A5A32);d.strokeWidth=s*.068f;d.line(0,0,s*.61f,s*.43f);
        d.setColor(0xFF56351F);d.fillCircle(0,0,s*.085f);d.setColor(0xFFF0BE8C);d.fillCircle(0,0,s*.060f);
        d.setColor(0xFF59646A);d.strokeWidth=s*.135f;d.line(-s*.19f,-s*.035f,s*.23f,-s*.035f);
        d.setColor(0xFFD8E0E4);d.strokeWidth=s*.085f;d.line(-s*.19f,-s*.035f,s*.23f,-s*.035f);
        d.setColor(0xFFFFFFFF);d.strokeWidth=s*.025f;d.line(-s*.14f,-s*.068f,s*.16f,-s*.068f);
        d.restore();
    }
    private void drawSack''')

# Small art polish for ordinary gnomes: brighter tunic seam and shoulder patch.
rep(
'''        d.setColor(adjust(w.tier.color,.62f));d.fillOval(-s*.30f,-s*.01f,s*.30f,s*.38f);d.setColor(0xFF5D3E28);d.fillRect(-s*.31f,s*.20f,s*.31f,s*.27f);d.setColor(0xFFD5A73A);d.fillRect(-s*.04f,s*.19f,s*.05f,s*.28f);''',
'''        d.setColor(adjust(w.tier.color,.62f));d.fillOval(-s*.30f,-s*.01f,s*.30f,s*.38f);d.setColor(adjust(w.tier.color,1.28f));d.strokeWidth=s*.035f;d.line(-s*.19f,s*.03f,-s*.19f,s*.28f);d.setColor(0xFF5D3E28);d.fillRect(-s*.31f,s*.20f,s*.31f,s*.27f);d.setColor(0xFFD5A73A);d.fillRect(-s*.04f,s*.19f,s*.05f,s*.28f);d.setColor(0xFFB8833B);d.fillCircle(-s*.27f,s*.04f,s*.065f);''')

# ---------------------------------------------------------------------------
# Mobs: spawn portal, cleaner silhouettes, readable claws/tails/weapons.
# ---------------------------------------------------------------------------
sub(
    r'    private void drawMob\(Draw d,Mob m\)\{.*?\}\n    private void drawImp',
'''    private void drawMob(Draw d,Mob m){
        if(m.dead)return;float s=m.type.size*ui,scale=appearScale(m.spawn,.65f);
        if(m.spawn>0)drawArrivalRing(d,m.x,m.y,s*.82f,m.spawn,.65f,m.type.color);
        if(m.spawn>0){d.save();d.translate(m.x,m.y);d.scale(scale,scale);d.translate(-m.x,-m.y);}
        switch(m.type){case IMP,IMP_KING->drawImp(d,m,s);case DEMON,DEMON_KING->drawDemon(d,m,s);default->drawGolem(d,m,s);}
        float pct=Math.max(0,m.hp/m.maxHp);if(pct<.999f||m.type.ordinal()>=EnemyType.IMP_KING.ordinal()){float bw=s*1.5f;d.setColor(0xCC120E0D);d.fillRoundRect(m.x-bw/2,m.y-s*.95f,m.x+bw/2,m.y-s*.84f,2f*ui);d.setColor(0xFFE34F43);d.fillRoundRect(m.x-bw/2,m.y-s*.95f,m.x-bw/2+bw*pct,m.y-s*.84f,2f*ui);}
        if(m.spawn>0)d.restore();
    }
    private void drawImp''')

sub(
    r'    private void drawImp\(Draw d,Mob m,float s\)\{.*?\}\n    private void drawDemon',
'''    private void drawImp(Draw d,Mob m,float s){
        float hop=Math.abs((float)Math.sin(m.walkCycle+m.phase))*s*.11f,flap=(float)Math.sin(elapsed*13f+m.phase),steal=m.attack>0?(float)Math.sin((.42f-m.attack)/.42f*Math.PI):0;float dir=m.goalCell>=0&&cx(map.col(m.goalCell))<m.x?-1:1;
        d.save();d.translate(m.x,m.y-hop);d.scale(dir,1);if(m.type==EnemyType.IMP_KING){d.setColor(0x22FF4A32);d.fillCircle(0,0,s*.92f*(1+.07f*(float)Math.sin(elapsed*5)));}
        // leathery wings
        d.setColor(adjust(m.type.color,.48f));d.pathReset();d.moveTo(-s*.18f,-s*.08f);d.lineTo(-s*(.65f+.10f*flap),-s*.44f);d.lineTo(-s*.50f,s*.02f);d.lineTo(-s*.30f,s*.20f);d.closePath();d.fillPath();d.pathReset();d.moveTo(s*.18f,-s*.08f);d.lineTo(s*(.65f+.10f*flap),-s*.44f);d.lineTo(s*.50f,s*.02f);d.lineTo(s*.30f,s*.20f);d.closePath();d.fillPath();
        // body/head
        d.setColor(adjust(m.type.color,.78f));d.fillOval(-s*.29f,-s*.12f,s*.29f,s*.48f);d.setColor(m.type.color);d.fillCircle(0,-s*.32f,s*.30f);
        // long ivory horns
        d.setColor(0xFFF0D8A6);d.pathReset();d.moveTo(-s*.21f,-s*.51f);d.lineTo(-s*.55f,-s*.83f);d.lineTo(-s*.34f,-s*.48f);d.closePath();d.fillPath();d.pathReset();d.moveTo(s*.21f,-s*.51f);d.lineTo(s*.55f,-s*.83f);d.lineTo(s*.34f,-s*.48f);d.closePath();d.fillPath();
        d.setColor(0xFF17110F);d.fillOval(-s*.19f,-s*.40f,-s*.03f,-s*.29f);d.fillOval(s*.03f,-s*.40f,s*.19f,-s*.29f);d.setColor(0xFFFFE45C);d.fillCircle(-s*.10f,-s*.345f,s*.04f);d.fillCircle(s*.10f,-s*.345f,s*.04f);
        // legs and greedy reaching arms
        d.setColor(adjust(m.type.color,.57f));d.strokeWidth=s*.09f;float step=(float)Math.sin(m.walkCycle);d.line(-s*.12f,s*.40f,-s*.27f+step*s*.08f,s*.72f);d.line(s*.12f,s*.40f,s*.27f-step*s*.08f,s*.72f);d.line(s*.24f,s*.08f,s*(.60f+.20f*steal),s*(.19f-.08f*steal));d.line(-s*.24f,s*.08f,-s*.53f,s*.27f);
        // hooked tail makes the thief silhouette unmistakable
        d.strokeWidth=s*.055f;d.line(-s*.22f,s*.32f,-s*.52f,s*.48f);d.line(-s*.52f,s*.48f,-s*.68f,s*.31f);d.setColor(m.type.color);d.pathReset();d.moveTo(-s*.72f,s*.27f);d.lineTo(-s*.57f,s*.30f);d.lineTo(-s*.67f,s*.42f);d.closePath();d.fillPath();
        if(m.type==EnemyType.IMP_KING)drawCrown(d,0,-s*.78f,s*.70f);d.restore();
    }
    private void drawDemon''')

sub(
    r'    private void drawDemon\(Draw d,Mob m,float s\)\{.*?\}\n    private void drawGolem',
'''    private void drawDemon(Draw d,Mob m,float s){
        float stride=(float)Math.sin(m.walkCycle+m.phase),breath=(float)Math.sin(elapsed*3.2f+m.phase)*s*.025f,slash=m.attack>0?(float)Math.sin((.40f-m.attack)/.40f*Math.PI):0;
        d.save();d.translate(m.x,m.y+breath);if(m.type==EnemyType.DEMON_KING){d.setColor(0x288A1F28);d.fillCircle(0,0,s*.98f*(1+.08f*(float)Math.sin(elapsed*4)));}
        d.setColor(0x55000000);d.fillOval(-s*.53f,s*.61f,s*.53f,s*.78f);
        // folded black-red wings behind the torso
        d.setColor(adjust(m.type.color,.42f));d.pathReset();d.moveTo(-s*.25f,-s*.06f);d.lineTo(-s*.68f,-s*.42f);d.lineTo(-s*.55f,s*.28f);d.lineTo(-s*.28f,s*.42f);d.closePath();d.fillPath();d.pathReset();d.moveTo(s*.25f,-s*.06f);d.lineTo(s*.68f,-s*.42f);d.lineTo(s*.55f,s*.28f);d.lineTo(s*.28f,s*.42f);d.closePath();d.fillPath();
        // broad armored torso
        d.setColor(adjust(m.type.color,.66f));d.fillOval(-s*.39f,-s*.10f,s*.39f,s*.59f);d.setColor(adjust(m.type.color,.90f));d.fillRoundRect(-s*.30f,-s*.03f,s*.30f,s*.28f,s*.06f);d.setColor(0xFF311719);d.strokeWidth=s*.04f;d.line(-s*.23f,s*.02f,s*.23f,s*.21f);d.line(s*.23f,s*.02f,-s*.23f,s*.21f);
        d.setColor(m.type.color);d.fillCircle(0,-s*.40f,s*.34f);
        // swept horns
        d.setColor(0xFFEAD3A2);d.pathReset();d.moveTo(-s*.24f,-s*.59f);d.lineTo(-s*.65f,-s*.88f);d.lineTo(-s*.47f,-s*.51f);d.closePath();d.fillPath();d.pathReset();d.moveTo(s*.24f,-s*.59f);d.lineTo(s*.65f,-s*.88f);d.lineTo(s*.47f,-s*.51f);d.closePath();d.fillPath();
        // eyes and mouth
        d.setColor(0xFF140C0D);d.fillOval(-s*.20f,-s*.47f,-s*.04f,-s*.35f);d.fillOval(s*.04f,-s*.47f,s*.20f,-s*.35f);d.setColor(0xFFFFD447);d.fillCircle(-s*.12f,-s*.41f,s*.045f);d.fillCircle(s*.12f,-s*.41f,s*.045f);d.setColor(0xFF2A1112);d.fillRect(-s*.13f,-s*.26f,s*.13f,-s*.21f);
        // legs, claw arm and a visible cleaver on attack side
        d.setColor(adjust(m.type.color,.48f));d.strokeWidth=s*.14f;d.line(-s*.18f,s*.51f,-s*.31f+stride*s*.08f,s*.82f);d.line(s*.18f,s*.51f,s*.31f-stride*s*.08f,s*.82f);d.line(s*.32f,s*.04f,s*.61f,s*.25f);
        float ax=-s*(.55f+.22f*slash),ay=s*(.13f-.24f*slash);d.line(-s*.32f,s*.04f,ax,ay);d.setColor(0xFF6D4B31);d.strokeWidth=s*.065f;d.line(ax,ay,ax-s*.18f,ay-s*.24f);d.setColor(0xFFC8D0D5);d.pathReset();d.moveTo(ax-s*.20f,ay-s*.28f);d.lineTo(ax-s*.43f,ay-s*.37f);d.lineTo(ax-s*.28f,ay-s*.10f);d.closePath();d.fillPath();
        if(m.type==EnemyType.DEMON_KING)drawCrown(d,0,-s*.88f,s*.76f);d.restore();
    }
    private void drawGolem''')

# Give golems a bright elemental core at phone scale.
rep(
'''        d.setColor(0x66000000);d.fillOval(-s*.58f,s*.60f,s*.58f,s*.78f);d.setColor(adjust(col,.62f));d.fillRoundRect(-s*.37f,-s*.02f,s*.37f,s*.52f,s*.10f);d.setColor(col);d.fillCircle(0,-s*.39f,s*.32f);''',
'''        d.setColor(0x66000000);d.fillOval(-s*.58f,s*.60f,s*.58f,s*.78f);d.setColor(adjust(col,.62f));d.fillRoundRect(-s*.37f,-s*.02f,s*.37f,s*.52f,s*.10f);d.setColor(adjust(col,.42f));d.fillCircle(0,s*.18f,s*.17f);d.setColor(adjust(col,1.42f));d.fillCircle(0,s*.18f,s*.095f);d.setColor(col);d.fillCircle(0,-s*.39f,s*.32f);''')

# ---------------------------------------------------------------------------
# Hazards: distinguish a pit from a lava fissure; persistent rubble shows HP.
# ---------------------------------------------------------------------------
sub(
    r'    private void drawHazards\(Draw d\)\{.*?\n    \}\n\n    private void drawCollapseHazard',
'''    private void drawHazards(Draw d){
        for(CaveHazard h:hazards){
            float warning=Math.min(1f,h.age/1.25f);
            switch(h.type){
                case COLLAPSE -> drawCollapseHazard(d,h,warning);
                case FLOOD -> drawFloodHazard(d,h,warning);
                case PIT -> drawPitHazard(d,h,warning);
                case LAVA -> drawLavaHazard(d,h,warning);
            }
        }
    }

    private void drawPitHazard(Draw d,CaveHazard h,float warning){
        float pulse=.5f+.5f*(float)Math.sin(h.age*8f);
        d.setColor(0xFF514A42);for(int i=0;i<11;i++){float a=i*.571f+h.cell*.13f,rr=h.r*(.75f+.09f*(i%3));d.fillCircle(h.x+(float)Math.cos(a)*rr,h.y+(float)Math.sin(a)*rr*.55f,(3.5f+i%3*1.2f)*ui);}
        d.setColor(0xEE020202);d.fillOval(h.x-h.r*.80f,h.y-h.r*.42f,h.x+h.r*.80f,h.y+h.r*.43f);d.setColor(0xFF171411);d.fillOval(h.x-h.r*.55f,h.y-h.r*.27f,h.x+h.r*.55f,h.y+h.r*.31f);
        if(h.age<1.25f){d.setColor(alpha(0xFFE7D6B9,.35f+.35f*pulse));d.strokeWidth=(1.2f+warning)*ui;d.strokeCircle(h.x,h.y,h.r*(.70f+.06f*pulse));}
    }

    private void drawLavaHazard(Draw d,CaveHazard h,float warning){
        float t=Math.max(0,h.age-1.25f),pulse=.5f+.5f*(float)Math.sin(elapsed*5.2f+h.cell);
        // Before ignition it is cracked rock glowing from underneath, not a mysterious red stain.
        d.setColor(0xFF171311);d.fillOval(h.x-h.r*.93f,h.y-h.r*.52f,h.x+h.r*.93f,h.y+h.r*.53f);
        for(int i=0;i<7;i++){
            float a=i*.897f+h.cell*.19f,inner=h.r*.12f,outer=h.r*(.54f+.08f*(i%3));float x1=h.x+(float)Math.cos(a)*inner,y1=h.y+(float)Math.sin(a)*inner*.52f,x2=h.x+(float)Math.cos(a)*outer,y2=h.y+(float)Math.sin(a)*outer*.52f;
            d.setColor(alpha(0xFFFF5B24,h.age<1.25f?.28f+.48f*warning:.72f));d.strokeWidth=(2.7f+(i%2))*ui;d.line(x1,y1,x2,y2);d.setColor(alpha(0xFFFFD35A,h.age<1.25f?.18f+.48f*warning:.82f));d.strokeWidth=1.1f*ui;d.line(x1,y1,x2,y2);
        }
        if(h.age<1.25f)return;
        // Irregular molten pool with bright center and moving bubbles.
        d.setColor(0xCC7B1E13);d.pathReset();int n=12;for(int i=0;i<n;i++){float a=(float)(Math.PI*2*i/n),sc=.68f+hash01(h.cell*997L+i*83L)*.22f;float x=h.x+(float)Math.cos(a)*h.r*sc,y=h.y+(float)Math.sin(a)*h.r*sc*.48f;if(i==0)d.moveTo(x,y);else d.lineTo(x,y);}d.closePath();d.fillPath();
        d.setColor(0xFFFF5725);d.fillOval(h.x-h.r*.57f,h.y-h.r*.25f,h.x+h.r*.57f,h.y+h.r*.27f);d.setColor(alpha(0xFFFFB52F,.72f+.22f*pulse));d.fillOval(h.x-h.r*.36f,h.y-h.r*.15f,h.x+h.r*.36f,h.y+h.r*.17f);d.setColor(0xFFFFE07A);d.fillOval(h.x-h.r*.18f,h.y-h.r*.07f,h.x+h.r*.18f,h.y+h.r*.09f);
        for(int i=0;i<5;i++){float a=i*1.31f+h.cell*.07f,cycle=(t*(.7f+i*.09f)+i*.23f)%1f;float x=h.x+(float)Math.cos(a)*h.r*(.12f+.42f*cycle),y=h.y+(float)Math.sin(a*1.7f)*h.r*.16f;float rr=(1.5f+cycle*3f)*ui;d.setColor(cycle>.72f?0x55FFE89A:0xFFFF9D32);d.strokeWidth=1.2f*ui;d.strokeCircle(x,y,rr);}
    }

    private void drawCollapseHazard''')

# Add visible rubble health/progress to the persistent cave-in.
rep(
'''        d.setColor(0x668F8173);for(int i=0;i<7;i++){float drift=(h.age*13f+i*17f)%38f;d.fillCircle(h.x+(i-3)*h.r*.15f,h.y-h.r*.10f-drift*ui,(2f+i%3)*ui*(1-Math.min(.85f,(h.age-1.25f)/10f)));}
    }''',
'''        d.setColor(0x668F8173);for(int i=0;i<7;i++){float drift=(h.age*13f+i*17f)%38f;d.fillCircle(h.x+(i-3)*h.r*.15f,h.y-h.r*.10f-drift*ui,(2f+i%3)*ui*(1-Math.min(.85f,(h.age-1.25f)/10f)));}
        if(h.rubbleMaxHp>0&&!h.cleared){float pct=Math.max(0,h.rubbleHp/h.rubbleMaxHp),bw=h.r*1.28f,by=h.y-h.r*.72f;d.setColor(0xCC0F0D0C);d.fillRoundRect(h.x-bw/2,by,h.x+bw/2,by+5f*ui,2f*ui);d.setColor(0xFFBA8A54);d.fillRoundRect(h.x-bw/2,by,h.x-bw/2+bw*pct,by+5f*ui,2f*ui);}
    }''')

# ---------------------------------------------------------------------------
# Gnome panel: cargo is a stat, not a disabled mystery button.
# ---------------------------------------------------------------------------
sub(
    r'    private void drawGnomePanel\(Draw d\)\{.*?\}\n    private void drawUpgradePanel',
'''    private void drawGnomePanel(Draw d){GnomeTier gt=GnomeTier.values()[selectedTier];float ct=contentTop();button(d,left,"‹",selectedTier>0,1.15f);button(d,right,"›",selectedTier<GnomeTier.values().length-1,1.15f);d.align=Draw.Align.CENTER;d.bold=true;d.textSize=11f*ui;d.setColor(gt.color);d.text(gt.title,width/2,ct+18f*ui);d.bold=false;d.textSize=8.3f*ui;d.setColor(0xFFB6C0C8);d.text("×"+state.tierCounts[selectedTier]+"   ур. "+state.tierLevels[selectedTier]+"   добыча "+one.format(gt.miningPower*state.tierPowerMultiplier(selectedTier)),width/2,ct+37f*ui);d.align=Draw.Align.LEFT;
        if(selectedTier==0)button(d,primary,"КУПИТЬ • "+format(state.minerBuyCost()),true,.72f);else button(d,primary,"ЭТОТ ТИП",false,.72f);button(d,secondary,"УЛУЧШИТЬ • "+format(state.tierUpgradeCost(selectedTier)),true,.70f);button(d,tertiary,"СЛИТЬ 10 → 1",selectedTier<GnomeTier.values().length-1&&state.tierCounts[selectedTier]>=10,.68f);statPill(d,quaternary,"СУМКА • вместимость "+format((long)(gt.cargoCapacity*state.carryMultiplier(selectedTier))));}
    private void drawUpgradePanel''')

rep(
'''    private void button(Draw d,Box b,String text,boolean enabled,float scale){
        int accent=b==speed?UiTheme.GOLD:(b==back?UiTheme.STEEL:UiTheme.COPPER);
        UiTheme.button(d,b.l,b.t,b.r,b.b,ui,text,enabled,accent,b==speed&&speedHeld,scale);
    }''',
'''    private void button(Draw d,Box b,String text,boolean enabled,float scale){
        int accent=b==speed?UiTheme.GOLD:(b==back?UiTheme.STEEL:UiTheme.COPPER);
        UiTheme.button(d,b.l,b.t,b.r,b.b,ui,text,enabled,accent,b==speed&&speedHeld,scale);
    }
    private void statPill(Draw d,Box b,String text){d.setColor(0x66191D20);d.fillRoundRect(b.l,b.t,b.r,b.b,7f*ui);d.setColor(0xFF3B444A);d.strokeWidth=1f*ui;d.strokeRoundRect(b.l,b.t,b.r,b.b,7f*ui);d.align=Draw.Align.CENTER;d.bold=false;d.textSize=7.2f*ui;d.setColor(0xFF9FAAAF);d.text(text,b.cx(),b.cy()+2.5f*ui);d.align=Draw.Align.LEFT;}''')

# Draw API has no strokeRoundRect in older helper; normalize to safe fill-only if needed via workflow sed.

# Atmosphere is decorative. With 90+ workers it gets cheaper rather than competing with gameplay.
rep(
'''    private void drawAtmosphere(Draw d){
        for(int i=0;i<18;i++){float x=((i*73.3f+elapsed*(3+i%4))*ui)%(width+20f*ui)-10f*ui;float y=worldT+((i*119.7f+state.depth*31)%1000)/1000f*(worldB-worldT);d.setColor(0x18D7C7AB);d.fillCircle(x,y,(.7f+i%3*.45f)*ui);}''',
'''    private void drawAtmosphere(Draw d){
        int dust=workers.size()>90?7:18;for(int i=0;i<dust;i++){float x=((i*73.3f+elapsed*(3+i%4))*ui)%(width+20f*ui)-10f*ui;float y=worldT+((i*119.7f+state.depth*31)%1000)/1000f*(worldB-worldT);d.setColor(0x18D7C7AB);d.fillCircle(x,y,(.7f+i%3*.45f)*ui);}''')

CAVE.write_text(s)
print('deep mine visual pass applied')
