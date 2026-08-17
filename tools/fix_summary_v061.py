from pathlib import Path
import re

p = Path('core/src/main/java/com/enhort/gnomes/game/CaveScreen.java')
s = p.read_text()

pat = r'    private void drawLevelSummary\(Draw d\)\{.*?\n    \}\n\n    private void drawGameOver'
repl = '''    private void drawLevelSummary(Draw d){
        d.setColor(0xE6090705);d.fillRect(0,0,width,height);
        float cw=Math.min(width-18f*ui,390f*ui),l=(width-cw)/2f,t=Math.max(18f*ui,height*.055f),b=Math.min(height-16f*ui,height*.91f),p=Math.min(1f,summaryAnim/1.2f);
        for(int i=0;i<30;i++){float a=i*2.399f+summaryAnim*.3f,rr=(34f+(i%8)*16f)*ui*p,x=width/2+(float)Math.cos(a)*rr,y=t+46f*ui+(float)Math.sin(a)*rr*.46f;d.setColor(i%3==0?0x88FFD35A:i%3==1?0x8877D89A:0x88E77A55);d.fillCircle(x,y,(1.4f+i%3)*ui);}
        d.setColor(0xFF3A2516);d.fillRoundRect(l-4f*ui,t-4f*ui,l+cw+4f*ui,b+4f*ui,18f*ui);d.setColor(0xFF18130F);d.fillRoundRect(l,t,l+cw,b,15f*ui);d.setColor(0xFFF0B85A);d.fillRoundRect(l+22f*ui,t+5f*ui,l+cw-22f*ui,t+9f*ui,2f*ui);
        d.align=Draw.Align.CENTER;d.bold=true;d.textSize=16f*ui;d.setColor(0xFFFFD86B);d.text("ПОБЕДА!",width/2,t+38f*ui);
        d.textSize=8.2f*ui;d.setColor(0xFFF4EFE3);d.text("ГЛУБИНА "+state.depth+" ПОКОРЕНА",width/2,t+66f*ui);
        d.bold=false;d.textSize=5.3f*ui;d.setColor(0xFFC5B9A8);d.text(state.difficultyTitle()+" • "+levelObjectiveShort(),width/2,t+87f*ui);

        float x1=l+22f*ui,x2=l+cw*.76f,y=t+119f*ui,dy=29f*ui;
        d.align=Draw.Align.LEFT;d.textSize=5.8f*ui;d.setColor(0xFFC5B9A8);
        d.text("ЗАРАБОТАНО",x1,y);d.text("ВЛОЖЕНО",x1,y+dy);d.text("КАПИТАЛ",x1,y+dy*2);d.text("ДЕНЕГ ДАЛЬШЕ",x1,y+dy*3);d.text("ГНОМОВ ДАЛЬШЕ",x1,y+dy*4);
        d.align=Draw.Align.CENTER;d.bold=true;d.textSize=6.2f*ui;d.setColor(0xFFFFD56A);
        d.text(format(rolling(summaryEarned)),x2,y);d.text(format(rolling(summaryInvested)),x2,y+dy);d.text(format(rolling(summaryCapital)),x2,y+dy*2);
        d.setColor(0xFF7FDEA0);d.text(format(rolling(summaryTransfer))+" ×"+one.format(state.carryRatio()),x2,y+dy*3);d.text("1 + "+summaryCarry,x2,y+dy*4);

        d.bold=false;d.textSize=5.1f*ui;d.setColor(0xFFA99E90);d.text("1 новый шахтёр + часть старого отряда",width/2,b-74f*ui);d.text("продолжат путь глубже.",width/2,b-58f*ui);
        summaryOk.set(l+18f*ui,b-48f*ui,l+cw-18f*ui,b-10f*ui);
        d.align=Draw.Align.LEFT;button(d,summaryOk,"В ГЛУБИНУ • УРОВЕНЬ "+(state.depth+1),summaryAnim>.75f,.66f);
    }

    private void drawGameOver'''

out, n = re.subn(pat, repl, s, count=1, flags=re.S)
if n != 1:
    raise SystemExit(f'drawLevelSummary matcher failed: {n}')

p.write_text(out)
