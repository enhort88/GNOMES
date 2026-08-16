from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# Apply the complete interaction pass first, then normalize the hazard renderer into readable Java.
# Keeping this repair separate also makes the generated result much less fragile than another one-line switch patch.
exec(compile((ROOT / "tools" / "apply_interaction_pass_v2.py").read_text(), "apply_interaction_pass_v2.py", "exec"), globals(), globals())

p = ROOT / "core/src/main/java/com/enhort/gnomes/game/CaveScreen.java"
s = p.read_text()
start = s.index("    private void drawHazards(Draw d){")
end = s.index("\n    private void drawFx(", start)

method = r'''    private void drawHazards(Draw d){
        for(CaveHazard h:hazards){
            float warn=h.age<1.25f?.35f+.20f*(float)Math.sin(h.age*16f):.70f;
            switch(h.type){
                case COLLAPSE -> {
                    d.setColor(alpha(0xFFD57B4F,warn));
                    d.strokeWidth=2f*ui;
                    d.strokeCircle(h.x,h.y,h.r);
                    if(h.age>=1.25f){
                        d.setColor(0xFF50483F);
                        for(int i=0;i<11;i++){
                            float a=i*2.17f+h.cell*.31f;
                            float rr=h.r*(.18f+.56f*hash01(h.cell*971L+i*71L));
                            float sz=(4+i%4*2.1f)*ui;
                            d.fillCircle(h.x+(float)Math.cos(a)*rr,h.y+(float)Math.sin(a)*rr*.55f,sz);
                        }
                        d.setColor(0xFF75695D);
                        d.strokeWidth=2f*ui;
                        d.line(h.x-h.r*.65f,h.y+h.r*.16f,h.x+h.r*.64f,h.y-h.r*.12f);
                    }
                }
                case PIT -> {
                    d.setColor(0xDD020202);
                    d.fillOval(h.x-h.r,h.y-h.r*.55f,h.x+h.r,h.y+h.r*.55f);
                    d.setColor(0xFF514A42);
                    d.strokeWidth=2f*ui;
                    d.strokeCircle(h.x,h.y,h.r*.75f);
                }
                case LAVA -> {
                    d.setColor(alpha(0xFFFF5625,warn));
                    d.fillOval(h.x-h.r,h.y-h.r*.45f,h.x+h.r,h.y+h.r*.45f);
                    d.setColor(0xFFFFC13B);
                    for(int i=0;i<4;i++){
                        float a=elapsed*(1+i*.12f)+i;
                        d.fillCircle(h.x+(float)Math.cos(a)*h.r*.55f,h.y+(float)Math.sin(a*1.4f)*h.r*.24f,(2.5f+i%2*1.5f)*ui);
                    }
                }
                case FLOOD -> {
                    float q=Math.max(0,h.age-1.25f);
                    d.setColor(alpha(0xFF4BAEE0,warn));
                    float yy=h.y+(float)Math.sin(q*4f)*h.r*.14f;
                    d.fillRoundRect(h.x-h.r,yy-h.r*.24f,h.x+h.r,yy+h.r*.24f,h.r*.18f);
                    d.setColor(0x99DDF7FF);
                    for(int i=0;i<5;i++){
                        float xx=h.x-h.r+(i*.41f+(q*.9f)%1f)*h.r*2;
                        d.fillCircle(xx,yy-h.r*.10f+(i%2)*h.r*.12f,2f*ui);
                    }
                }
            }
        }
    }
'''

p.write_text(s[:start] + method + s[end:])
print("hazard renderer repaired")
