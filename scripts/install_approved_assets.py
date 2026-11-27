"""Install user-approved local Tripo derivatives and v4 wrist devices. No API calls."""
import argparse,json,hashlib,shutil
from pathlib import Path
from verify_tripo_assets import verify,load,accessor
p=argparse.ArgumentParser();p.add_argument('--workspace',type=Path,required=True);a=p.parse_args();repo=Path(__file__).resolve().parents[1];dst=repo/'app/src/main/assets';base=a.workspace/'assets/tripo/all-species-v2/runtime';v4=a.workspace/'assets/revisions/asset-polish-v4';rows=[]
ids=['sp01_jumping','sp02_wolf','sp03_orb','sp04_tarantula','sp05_longlegs','in01_roach','in02_beetle','in03_weevil']
for id in ids:
 root=v4/'creature' if id=='in01_roach'else base
 file=root/(id+'.glb');report=verify(file)
 for ext in ['.glb','.json']:shutil.copy2(root/(id+ext),dst/'models'/(id+ext))
 meta=json.loads((root/(id+'.json')).read_text());rows.append({'id':id,'sha256':hashlib.sha256(file.read_bytes()).hexdigest(),'sourceTaskId':meta['originalTaskId'],'sourceSha256':meta['originalSha256'],'triangles':meta['triangles']})
for meta in sorted((v4/'tools/models').glob('*.json')):
 m=json.loads(meta.read_text());id=m['id'];assert len(m['muzzleLocalGame'])==3
 for side in ['left','right']:
  f=meta.with_name(id+'_'+side+'.glb');g,b=load(f);assert {a['name']for a in g['animations']}=={'equip','fire'};shutil.copy2(f,dst/'models'/f.name)
 shutil.copy2(meta,dst/'models'/meta.name);shutil.copy2(v4/'tools/ui'/('thumb_'+id+'.png'),dst/'ui'/('thumb_'+id+'.png'))
rows.append({'toolsRevision':'asset-polish-v4','cloudCreditsConsumedThisIntegration':0});(dst/'models/approved-assets.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2));print('APPROVED_ASSETS_INSTALLED',len(ids),'creatures, 8 hand tool GLBs')
