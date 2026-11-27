"""Audit distinct creature recordings and real non-static one-second death clips."""
from pathlib import Path
import hashlib,json,wave
from verify_tripo_assets import load,accessor
root=Path(__file__).resolve().parents[1];assets=root/'app/src/main/assets';rows=[];signals=set()
for id in ['sp01_jumping','sp02_wolf','sp03_orb','sp04_tarantula','sp05_longlegs','in01_roach','in02_beetle','in03_weevil']:
 clips=[]
 for variant in [1,2]:
  p=assets/'audio/sfx'/f'{id}_emerge_{variant}.wav'
  with wave.open(str(p))as f:
   assert f.getnchannels()==1 and f.getframerate()==48000;pcm=f.readframes(f.getnframes());sha=hashlib.sha256(pcm).hexdigest();assert sha not in signals;signals.add(sha);clips.append({'file':p.name,'pcmSha256':sha,'seconds':f.getnframes()/f.getframerate()})
 g,b=load(assets/'models'/f'{id}.glb');a=next(x for x in g['animations']if x['name']=='fall');duration=max(accessor(g,b,s['input'])[-1][0]for s in a['samplers']);assert abs(duration-1)<.002
 moving=0
 for s in a['samplers']:
  v=accessor(g,b,s['output']);moving+=int(any(max(abs(x-y)for x,y in zip(q,v[0]))>.001 for q in v))
 assert moving>3,(id,moving)
 rows.append({'species':id,'arrivals':clips,'deathClip':'fall','deathSeconds':duration,'movingDeathChannels':moving})
out=root/'docs/verification/1.3.2/creature-presentation.json';out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps({'passed':True,'distinctArrivalRecordings':len(signals),'species':rows},indent=2));print('CREATURE_PRESENTATION_ASSETS_PASS 16 unique mono arrival PCM signals; 8 animated 1s death clips')
