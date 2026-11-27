"""Check exported game GLBs, including real skin weights, PBR maps and clip timing."""
import argparse, json, struct, hashlib
from pathlib import Path
COMP={5121:('B',1),5123:('H',2),5125:('I',4),5126:('f',4)}
WIDTH={'SCALAR':1,'VEC2':2,'VEC3':3,'VEC4':4,'MAT4':16}
def load(path):
 data=path.read_bytes(); assert data[:4]==b'glTF',path
 size,typ=struct.unpack_from('<II',data,12); assert typ==0x4e4f534a
 g=json.loads(data[20:20+size]); pos=20+size; n,typ=struct.unpack_from('<II',data,pos); assert typ==0x004e4942
 return g,data[pos+8:pos+8+n]
def accessor(g,b,idx):
 a=g['accessors'][idx]; v=g['bufferViews'][a['bufferView']]; fmt,n=COMP[a['componentType']]; w=WIDTH[a['type']]; offset=v.get('byteOffset',0)+a.get('byteOffset',0); stride=v.get('byteStride',n*w)
 return [struct.unpack_from('<'+fmt*w,b,offset+i*stride) for i in range(a['count'])]
def verify(path,tool=False):
 g,b=load(path); assert g.get('skins'),f'{path.name}: no skin'
 clips={a['name']:a for a in g.get('animations',[])}
 required={'equip','fire'} if tool else {'idle','crawl','attack','hit','fall','turn_left','turn_right'}
 assert required<=clips.keys(),f'{path.name}: missing clips {required-clips.keys()}'
 for name,a in clips.items():
  duration=max(accessor(g,b,s['input'])[-1][0] for s in a['samplers'])
  if name=='attack':assert abs(duration-1.2)<.01,(path.name,name,duration)
  if name=='fire':assert duration<=.201,(path.name,name,duration)
  if name in {'crawl','idle','fire','attack'}:
   for s in a['samplers']:
    vs=accessor(g,b,s['output']); assert max(abs(x-y) for x,y in zip(vs[0],vs[-1]))<.0001,(path.name,name,'loop seam')
  for ch in a['channels']:
   values=accessor(g,b,a['samplers'][ch['sampler']]['output']);target=ch['target'];node=g['nodes'][target['node']]
   if not tool and name in {'idle','crawl','attack','turn_left','turn_right','hit'}:
    if node.get('name')=='body' and target['path']=='translation':
     assert max(abs(v[k]-values[0][k]) for v in values for k in [0,2])<.0001,(path.name,name,'horizontal root travel')
     if name=='attack':
      times=accessor(g,b,a['samplers'][ch['sampler']]['input']);strike=min(range(len(times)),key=lambda i:abs(times[i][0]-.56));peak=max(v[1] for v in values)
      assert values[strike][1]>values[0][1]+.001,(path.name,'strike has no lift')
      assert values[strike][1]-values[0][1]>.8*(peak-values[0][1]),(path.name,'strike outside authored peak')
    if target['path']=='scale':assert max(abs(x-1) for v in values for x in v)<.0001,(path.name,name,'changing limb length')
 for mesh in g['meshes']:

  for p in mesh['primitives']:
   att=p['attributes'];assert {'JOINTS_0','WEIGHTS_0'}<=att.keys()
   for weights in accessor(g,b,att['WEIGHTS_0']):assert abs(sum(weights)-1)<.002
   if not tool:
    used={j for js,ws in zip(accessor(g,b,att['JOINTS_0']),accessor(g,b,att['WEIGHTS_0'])) for j,w in zip(js,ws) if w>.01}
    assert len(used)>12,(path.name,'insufficient articulated joints')
 for mat in g['materials']:
  assert 'baseColorTexture' in mat['pbrMetallicRoughness'];assert 'metallicRoughnessTexture' in mat['pbrMetallicRoughness'];assert 'normalTexture' in mat
 for im in g['images']:
  v=g['bufferViews'][im['bufferView']]; off=v.get('byteOffset',0); img=b[off:off+v['byteLength']]
  # Runtime export uses lossless PNG for deterministic normals/ORM.
  assert img[:8]==b'\x89PNG\r\n\x1a\n',im
  w,h=struct.unpack_from('>II',img,16);assert max(w,h)<=1024,(path.name,w,h)
 return {'file':path.name,'skins':len(g['skins']),'joints':sum(len(s['joints']) for s in g['skins']),'clips':list(clips),'sha256':hashlib.sha256(path.read_bytes()).hexdigest()}
if __name__=='__main__':
 ap=argparse.ArgumentParser();ap.add_argument('directory',type=Path);ap.add_argument('--static-source',type=Path);args=ap.parse_args()
 if args.static_source:verify(args.static_source)
 else:print(json.dumps([verify(args.directory/f'{id}.glb','wp01' in id) for id in ['sp01_jumping','in02_beetle','wp01_web_left','wp01_web_right']],indent=2))
