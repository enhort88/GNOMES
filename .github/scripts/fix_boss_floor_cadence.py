from pathlib import Path

p = Path('core/src/main/java/com/enhort/gnomes/game/CaveScreen.java')
s = p.read_text()

old_setup = '''    private void setupObjective(){
        if(state.depth==1){objectiveType=ObjectiveType.ASCEND_GNOME;}else if(state.depth%10==0){objectiveType=ObjectiveType.BOSS_HUNT;}else{ObjectiveType[] pool={ObjectiveType.CLEAR_VEINS,ObjectiveType.GUARDIAN,ObjectiveType.DEMON_PURGE,ObjectiveType.BOSS_HUNT,ObjectiveType.TREASURE};objectiveType=pool[Math.floorMod(state.depth+slot*3,pool.length)];}objectiveStartKills=state.enemiesDefeated;objectiveTarget=0;objectiveTreasureTarget=0;objectiveStarted=false;
        switch(objectiveType){case GUARDIAN->objectiveTarget=2+Math.min(2,state.depth/15);case DEMON_PURGE->objectiveTarget=3+Math.min(8,state.depth/3);case TREASURE->objectiveTreasureTarget=state.walletValue()+600L+state.depth*220L;default->{}}
    }
'''
new_setup = '''    private void setupObjective(){
        // Bosses are milestone encounters, not random objectives. Keeping kings on 10/20/30... makes the
        // difficulty curve readable and prevents an unlucky early floor from opening with a boss in your face.
        if(state.depth==1){
            objectiveType=ObjectiveType.ASCEND_GNOME;
        }else if(state.depth%10==0){
            objectiveType=ObjectiveType.BOSS_HUNT;
        }else{
            ObjectiveType[] pool={ObjectiveType.CLEAR_VEINS,ObjectiveType.GUARDIAN,ObjectiveType.DEMON_PURGE,ObjectiveType.TREASURE};
            objectiveType=pool[Math.floorMod(state.depth+slot*3,pool.length)];
        }
        objectiveStartKills=state.enemiesDefeated;objectiveTarget=0;objectiveTreasureTarget=0;objectiveStarted=false;
        switch(objectiveType){case GUARDIAN->objectiveTarget=2+Math.min(2,state.depth/15);case DEMON_PURGE->objectiveTarget=3+Math.min(8,state.depth/3);case TREASURE->objectiveTreasureTarget=state.walletValue()+600L+state.depth*220L;default->{}}
    }
'''
if old_setup not in s:
    raise SystemExit('setupObjective target not found')
s = s.replace(old_setup, new_setup, 1)

old_update = '''    private void updateObjective(){
        if(state.totalGnomes()<5)return;if(objectiveStarted)return;if(objectiveType==ObjectiveType.BOSS_HUNT){objectiveStarted=true;spawnBoss();}else if(objectiveType==ObjectiveType.DEMON_PURGE){objectiveStarted=true;EnemyType[] q=new EnemyType[objectiveTarget+1];for(int i=0;i<objectiveTarget;i++)q[i]=EnemyType.DEMON;q[q.length-1]=state.depth>=20?EnemyType.DEMON_KING:EnemyType.IMP_KING;openPortal(q);toast="ЗАДАНИЕ • ПЕРЕЖИТЬ НАШЕСТВИЕ";toastTime=2f;}}
'''
new_update = '''    private void updateObjective(){
        if(state.totalGnomes()<5)return;
        if(objectiveStarted)return;
        if(objectiveType==ObjectiveType.BOSS_HUNT){
            objectiveStarted=true;
            spawnBoss();
        }else if(objectiveType==ObjectiveType.DEMON_PURGE){
            // A regular invasion is a pack fight. Kings belong only to milestone boss floors.
            objectiveStarted=true;
            EnemyType[] q=new EnemyType[objectiveTarget];
            java.util.Arrays.fill(q,EnemyType.DEMON);
            openPortal(q);
            toast="ЗАДАНИЕ • ПЕРЕЖИТЬ НАШЕСТВИЕ";toastTime=2f;
        }
    }
'''
if old_update not in s:
    raise SystemExit('updateObjective target not found')
s = s.replace(old_update, new_update, 1)

p.write_text(s)
