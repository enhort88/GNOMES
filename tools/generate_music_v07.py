from pathlib import Path
import math, random, struct, wave

RATE = 22050
DURATION = 16.0
N = int(RATE * DURATION)
out = Path('core/src/main/resources/music')
out.mkdir(parents=True, exist_ok=True)

NOTES = {
    'D2':73.42,'F2':87.31,'G2':98.00,'A2':110.00,'C3':130.81,'D3':146.83,'F3':174.61,'G3':196.00,
    'A3':220.00,'C4':261.63,'D4':293.66,'F4':349.23,'G4':392.00,'A4':440.00
}

def sine(f,t): return math.sin(2*math.pi*f*t)
def tri(f,t): return 2/math.pi*math.asin(math.sin(2*math.pi*f*t))
def saw(f,t):
    x=(t*f)%1.0
    return 2*x-1

def env(local, length, attack=.04, release=.18):
    if local < 0 or local >= length: return 0.0
    a=min(1.0, local/max(.001,attack))
    r=min(1.0, (length-local)/max(.001,release))
    return max(0.0,min(a,r))

def kick(local):
    if local<0 or local>.22: return 0.0
    f=74-42*(local/.22)
    return sine(max(28,f),local)*math.exp(-local*18)

def bell(f,local,length=.65):
    e=env(local,length,.008,.34)
    return e*(sine(f,local)*.68+sine(f*2.01,local)*.19+sine(f*3.98,local)*.08)

def write_track(name, mode):
    rng=random.Random(0x474E4F4D4553 + mode*997)
    buf=[]
    progression=[('D2','A2','D3'),('F2','C3','F3'),('C3','G2','C4'),('G2','D3','G3')]
    melody_mine=['D4','F4','A3','C4','D4','A3','G3','F3']
    melody_danger=['D3','D4','C4','D4','F3','D4','C4','A3']
    melody_boss=['D3','A3','D4','F4','D4','C4','A3','G3']
    melody=[melody_mine,melody_danger,melody_boss][mode]
    beat=0.5 if mode==0 else 0.4 if mode==1 else 0.3333333333
    for i in range(N):
        t=i/RATE
        bar=int(t/4.0)%4
        root,fifth,upper=progression[bar]
        sample=0.0
        # Cave drone, intentionally restrained so SFX remain readable.
        sample += sine(NOTES[root],t)*(.12 if mode==0 else .16 if mode==1 else .20)
        sample += tri(NOTES[fifth],t)*(.055 if mode==0 else .075 if mode==1 else .09)
        sample += sine(NOTES[upper]*.5,t)*.035

        step=int(t/beat)
        local=t-step*beat
        note=melody[step%len(melody)]
        if mode==0:
            sample += bell(NOTES[note],local,beat*.88)*.16
            if step%4==0: sample += bell(NOTES[note]*.5,local,beat*.95)*.09
        elif mode==1:
            sample += tri(NOTES[note],local)*env(local,beat*.72,.015,.16)*.12
            sample += kick(local)*(.17 if step%2==0 else .08)
            sample += sine(NOTES['D2']*2,t)*(.025*(1+math.sin(2*math.pi*2.5*t)))
        else:
            sample += saw(NOTES[note],local)*env(local,beat*.68,.01,.13)*.08
            sample += tri(NOTES[note]*.5,local)*env(local,beat*.72,.01,.16)*.09
            sample += kick(local)*(.26 if step%2==0 else .13)
            # Slow war-drum second transient.
            local2=(t+beat*.5)%beat
            sample += kick(local2)*.08

        # Very low deterministic cave air/noise.
        noise=(rng.random()*2-1)
        sample += noise*(.006 if mode==0 else .009)
        # Soft limiter.
        sample=math.tanh(sample*1.35)*.72
        buf.append(int(max(-1,min(1,sample))*32767))

    with wave.open(str(out/name),'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(RATE)
        w.writeframes(b''.join(struct.pack('<h',x) for x in buf))
    print(name, (out/name).stat().st_size)

write_track('mine_loop_v3.wav',0)
write_track('danger_loop.wav',1)
write_track('boss_loop.wav',2)
