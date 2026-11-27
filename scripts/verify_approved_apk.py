"""Verify the APK contains byte-identical approved meshes, metadata and thumbnails."""
import argparse,hashlib,json,zipfile
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('apk',type=Path);p.add_argument('--out',type=Path);a=p.parse_args();root=Path(__file__).resolve().parents[1];assets=root/'app/src/main/assets';rows=[]
with zipfile.ZipFile(a.apk) as z:
 files=list((assets/'models').glob('*.glb'))+list((assets/'models').glob('*.json'))+list((assets/'ui').glob('thumb_wp*.png'))
 for f in files:
  data=z.read('assets/'+str(f.relative_to(assets)));sha=hashlib.sha256(data).hexdigest();assert sha==hashlib.sha256(f.read_bytes()).hexdigest(),f
  if f.suffix=='.glb':rows.append({'file':f.name,'bytes':len(data),'sha256':sha})
report={'passed':True,'checkedFiles':len(files),'apkBytes':a.apk.stat().st_size,'apkSha256':hashlib.sha256(a.apk.read_bytes()).hexdigest(),'models':rows}
if a.out:a.out.parent.mkdir(parents=True,exist_ok=True);a.out.write_text(json.dumps(report,indent=2))
print('PACKAGED_APPROVED_ASSETS_PASS',len(files),'files',len(rows),'GLBs; APK',report['apkBytes'],'bytes SHA256',report['apkSha256'])
