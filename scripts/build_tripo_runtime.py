"""Local derivatives of approved Tripo GLBs. Run in Blender --background --python ... -- <asset-workspace> <out>.
Originals are never modified. No service/API calls. Output canonical Blender +Z up/+Y forward -> glTF +Y up/-Z forward.
"""
import bpy, math, json, sys, hashlib
from pathlib import Path
from mathutils import Vector, Matrix
import numpy as np
ARGS=sys.argv[sys.argv.index('--')+1:]; SOURCE=Path(ARGS[0]); OUT=Path(ARGS[1]); OUT.mkdir(parents=True,exist_ok=True)
ROOT=SOURCE/'assets/tripo/revision-2026-11-27'; manifest=json.loads((ROOT/'manifest.json').read_text())
FPS=30

def clear():
 bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
 for collection in [bpy.data.actions,bpy.data.materials,bpy.data.images,bpy.data.meshes,bpy.data.armatures]:
  for item in list(collection):
   if not item.users:collection.remove(item)

def smooth(t):
 t=max(0,min(1,t));return t*t*(3-2*t)

def points(o):return np.array([v.co[:] for v in o.data.vertices])

def import_mesh(a):
 clear();p=ROOT/a['file'];assert hashlib.sha256(p.read_bytes()).hexdigest()==a['sha256']
 bpy.ops.import_scene.gltf(filepath=str(p));meshes=[o for o in bpy.context.scene.objects if o.type=='MESH'];assert len(meshes)==1
 o=meshes[0];o.data.transform(o.matrix_world);o.parent=None;o.matrix_world=Matrix.Identity(4)
 if a['id']=='jumping_spider':rot=Matrix.Rotation(math.pi/2,4,'Z')
 elif a['id']=='armored_beetle':rot=Matrix.Rotation(-math.pi/2,4,'Y')@Matrix.Rotation(-math.pi/2,4,'X')
 else:
  vs=points(o);_,axes=np.linalg.eigh(np.cov(vs.T));f=Vector(axes[:,-1]);f*=1 if f.x>0 else -1;up=Vector((0,0,1));up=(up-f*up.dot(f)).normalized();rot=Matrix((f.cross(up).normalized(),f,up)).to_4x4()
 o.data.transform(rot);vs=points(o);lo=vs.min(axis=0);hi=vs.max(axis=0);mid=(lo+hi)/2;mid[2]=lo[2]
 o.data.transform(Matrix.Translation(-Vector(mid)));return o

def resize_maps(o,limit):
 for m in o.data.materials:
  for n in m.node_tree.nodes:
   if n.type=='TEX_IMAGE' and n.image and max(n.image.size)>limit:
    n.image.scale(limit,limit);n.image.file_format="PNG";n.image.pack()

def armature(legs,span,tool=False):
 data=bpy.data.armatures.new('TripoArticulatedSkeleton');arm=bpy.data.objects.new('TripoRig',data);bpy.context.collection.objects.link(arm);bpy.context.view_layer.objects.active=arm;arm.select_set(True);bpy.ops.object.mode_set(mode='EDIT')
 body=data.edit_bones.new('body');body.head=(0,0,.02 if tool else span*.13);body.tail=body.head+Vector((0,0,span*.1))
 if tool:
  b=data.edit_bones.new('mechanism');b.head=(0,.075,.04);b.tail=(0,.12,.04);b.parent=body
 else:
  for side,row,rest in legs:
   for k in range(3):
    b=data.edit_bones.new(f'{side}_leg{row+1}_{k+1}');b.head=rest[k];b.tail=rest[k+1];b.parent=body
 bpy.ops.object.mode_set(mode='OBJECT')
 for p in arm.pose.bones:p.rotation_mode='QUATERNION'
 return arm

def distance_segment(v,a,b):
 d=b-a;t=max(0,min(1,(v-a).dot(d)/d.length_squared));return (v-a-d*t).length,t

def bind_creature(o,arm,legs,span):
 groups={b.name:o.vertex_groups.new(name=b.name) for b in arm.data.bones}
 for v in o.data.vertices:
  p=v.co; candidates=[]
  for side,row,rest in legs:
   for k in range(3):
    d,t=distance_segment(p,rest[k],rest[k+1]);candidates.append((d,side,row,k,t))
  d,side,row,k,t=min(candidates);rest=next(r for s,j,r in legs if s==side and j==row)
  # Body is a variable-radius capsule; this protects abdomen/head from nearby limb chains.
  y=p.y/span;body_radius=(.11 if y>.1 else .135)*span
  body_d,_=distance_segment(p,Vector((0,-.30*span,.14*span)),Vector((0,.17*span,.14*span)))
  body_score=body_d-body_radius;leg_score=d-.026*span
  bw=1-smooth((body_score-leg_score+.012*span)/(.045*span))
  weights={'body':bw};remaining=1-bw;name=f'{side}_leg{row+1}_{k+1}'
  if t<.20 and k>0:
   share=.5*(1-smooth(t/.20));weights[f'{side}_leg{row+1}_{k}']=share*remaining;remaining*=1-share
  elif t>.80 and k<2:
   share=.5*smooth((t-.80)/.20);weights[f'{side}_leg{row+1}_{k+2}']=share*remaining;remaining*=1-share
  weights[name]=remaining
  for key,w in weights.items():
   if w>1e-6:groups[key].add([v.index],w,'REPLACE')
 mod=o.modifiers.new('Articulated limbs','ARMATURE');mod.object=arm;o.parent=arm

def fit_rest(o,xy,span):
 vs=points(o)/span;legs=[]
 for side,sign in [('L',-1),('R',1)]:
  for row,chain in enumerate(xy):
   rest=[]
   for x,y in chain:
    target=np.array([sign*x,y]);ds=((vs[:,:2]-target)**2).sum(axis=1);idx=np.argsort(ds)[:18];z=float(np.median(vs[idx,2]));rest.append(Vector((sign*x*span,y*span,z*span)))
   legs.append((side,row,rest))
 return legs

def solve_leg(rest,foot,torso):
 a=torso@rest[0];hip=torso@rest[1];knee_ref=torso@rest[2];l1=(rest[2]-rest[1]).length;l2=(rest[3]-rest[2]).length
 delta=foot-hip;dist=delta.length;d=max(abs(l1-l2)+1e-7,min(l1+l2-1e-7,dist));axis=delta.normalized();foot=hip+axis*d
 bend=knee_ref-hip;perp=bend-axis*bend.dot(axis)
 if perp.length<1e-7:perp=Vector((0,0,1))-axis*axis.z
 perp.normalize();along=(l1*l1-l2*l2+d*d)/(2*d);height=math.sqrt(max(0,l1*l1-along*along));knee=hip+axis*along+perp*height
 return [a,hip,knee,foot]

def bone_matrix(a,b,length):
 delta=b-a;return Matrix.Translation(a)@delta.to_track_quat('Y','Z').to_matrix().to_4x4()@Matrix.Diagonal((1,delta.length/length,1,1))

def key(pb,frame):
 for path in ['location','rotation_quaternion','scale']:pb.keyframe_insert(path,frame=frame)

def finish_actions(arm,actions):
 arm.animation_data.action=None
 for a in actions:
  tr=arm.animation_data.nla_tracks.new();tr.name=a.name;tr.strips.new(a.name,0,a);tr.mute=True
 arm.animation_data.action=actions[0]

def animate_creature(arm,legs,span,stride):
 actions=[];ground=.006*span
 for name,end in [('idle',60),('crawl',30),('turn_left',30),('turn_right',30),('emerge',60),('emerge_floor',60),('attack',36),('hit',12),('fall',30)]:
  action=bpy.data.actions.new(name);arm.animation_data_create();arm.animation_data.action=action
  for frame in range(end+1):
   t=frame/end;attack=anticipation=0;bob=.008*span*math.sin(math.tau*t);translation=Vector((0,0,bob));pitch=yaw=0
   if name=='attack':
    sec=t*1.2
    if sec<.42:anticipation=smooth(sec/.42)
    elif sec<.60:attack=smooth((sec-.42)/.18);anticipation=1-attack
    else:attack=1-smooth((sec-.60)/.60)
    translation.z=span*(-.025*anticipation+.065*attack);pitch=.10*anticipation-.20*attack
   elif name=='hit':translation.z=span*.018*math.sin(math.pi*t);yaw=.13*math.sin(math.pi*t)
   elif name=='fall':pitch=math.pi*.88*smooth(t);translation.z=-.12*span*smooth(t)
   elif name.startswith('turn'):yaw=(.065 if name=='turn_left' else -.065)*math.sin(math.tau*t)
   pivot=arm.data.bones['body'].head_local
   torso=Matrix.Translation(translation)@Matrix.Translation(pivot)@Matrix.Rotation(yaw,4,'Z')@Matrix.Rotation(pitch,4,'X')@Matrix.Translation(-pivot);pb=arm.pose.bones['body'];pb.matrix=torso@arm.data.bones['body'].matrix_local;key(pb,frame);bpy.context.view_layer.update()
   for side,row,rest in legs:
    foot=rest[3].copy();foot.z=ground
    if name in ['crawl','turn_left','turn_right','emerge','emerge_floor']:
     phase=(t+(.5 if (row+(side=='R'))%2 else 0))%1
     if phase<.64:dy=stride*(.32-phase);dz=0
     else:u=(phase-.64)/.36;dy=stride*(-.32+.64*smooth(u));dz=span*.07*math.sin(math.pi*u)**1.25
     if name.startswith('turn') and ((side=='L')==(name=='turn_left')):dy*=-.25
     foot.y+=dy;foot.z+=dz
    if name=='attack':
     front=row==0;foot.y+=span*(.10 if front else -.035)*attack;foot.z+=span*(.20 if front else .015)*attack
    if name=='fall':
     foot=torso@rest[3];foot.x*=1-.30*smooth(t)
    ps=solve_leg(rest,foot,torso)
    for k in range(3):
     n=f'{side}_leg{row+1}_{k+1}';pb=arm.pose.bones[n];pb.matrix=bone_matrix(ps[k],ps[k+1],arm.data.bones[n].length);key(pb,frame)
   bpy.context.view_layer.update()
  actions.append(action)
 finish_actions(arm,actions);return actions

def export(o,arm,id,meta):
 bpy.context.scene.render.fps=FPS;bpy.context.scene.frame_set(0);bpy.ops.object.select_all(action='DESELECT');o.select_set(True);arm.select_set(True);bpy.context.view_layer.objects.active=arm
 bpy.ops.export_scene.gltf(filepath=str(OUT/(id+'.glb')),export_format='GLB',use_selection=True,export_image_format='AUTO',export_animations=True,export_animation_mode='ACTIONS',export_force_sampling=True,export_skins=True,export_yup=True,export_cameras=False,export_lights=False)
 o.data.calc_loop_triangles();meta.update({'triangles':len(o.data.loop_triangles),'bones':len(arm.data.bones),'revision':'tripo-runtime-v1','source':'Tripo P2-20260801; locally rigged derivative'})
 (OUT/(id+'.json')).write_text(json.dumps(meta,ensure_ascii=False,indent=2));bpy.ops.wm.save_as_mainfile(filepath=str(OUT/(id+'.blend')))
 print('RUNTIME_ASSET',id,meta['triangles'],flush=True)

def creatures(a):
 o=import_mesh(a);beetle=a['id']=='armored_beetle';span=.14 if beetle else .12
 # Long axis for beetle, full leg span for spider, maintaining the existing gameplay silhouettes.
 vs=points(o);scale=span/np.max(vs.max(axis=0)-vs.min(axis=0));o.data.transform(Matrix.Scale(scale,4));resize_maps(o,1024)
 xy=([[(.12,.12),(.21,.18),(.235,.265),(.255,.38)],[(.13,-.03),(.21,-.02),(.27,-.14),(.355,-.23)],[(.12,-.20),(.195,-.23),(.22,-.40),(.28,-.48)]] if beetle else [[(.10,.15),(.15,.30),(.157,.41),(.133,.49)],[(.105,.08),(.20,.13),(.28,.24),(.35,.32)],[(.09,-.035),(.21,.015),(.36,.015),(.44,.015)],[(.10,-.12),(.17,-.22),(.28,-.35),(.37,-.47)]])
 legs=fit_rest(o,xy,span);arm=armature(legs,span);bind_creature(o,arm,legs,span);stride=span*(.32 if beetle else .40);actions=animate_creature(arm,legs,span,stride)
 meta={'id':'in02_beetle' if beetle else 'sp01_jumping','name':'重甲甲虫' if beetle else '写实跳蛛','kind':'beetle' if beetle else 'spider','spanMeters':span,'legCount':len(legs),'originalTaskId':a['taskId'],'originalSha256':a['sha256'],'textureSize':1024,'motion':{'fps':30,'strideMeters':stride,'cycleSeconds':1,'stanceRatio':.64,'attackDurationSeconds':1.2,'attackContactSeconds':.56,'attackContactWindow':[.52,.64],'rootTravelBaked':False,'worldUp':'+Y','worldForward':'-Z'},'clips':[{'name':x.name,'frames':list(x.frame_range),'fps':30} for x in actions]}
 arm.location.z=.0036  # Keep the visible tarsal surface above the tracked real plane.
 export(o,arm,meta['id'],meta)

def launcher(a):
 o=import_mesh(a);vs=points(o);scale=.17/(vs[:,1].max()-vs[:,1].min());o.data.transform(Matrix.Scale(scale,4));vs=points(o)
 # Mount the barrel parallel to the wrist, with the emitter in front of the existing 17cm shot origin.
 front=vs[vs[:,1] > vs[:,1].max()-.012];muzzle=front.mean(axis=0)
 offset=Vector((-muzzle[0],.17-vs[:,1].max(),-muzzle[2]));o.data.transform(Matrix.Translation(offset));resize_maps(o,1024)
 arm=armature([],.17,True);body=o.vertex_groups.new(name='body');mech=o.vertex_groups.new(name='mechanism')
 for v in o.data.vertices:
  w=smooth((v.co.y-.065)/.035);body.add([v.index],1-w,'REPLACE');mech.add([v.index],w,'REPLACE')
 mod=o.modifiers.new('Emitter recoil','ARMATURE');mod.object=arm;o.parent=arm;actions=[]
 for name,end in [('equip',24),('fire',6)]:
  aclip=bpy.data.actions.new(name);arm.animation_data_create();arm.animation_data.action=aclip
  for frame in range(end+1):
   t=frame/end;pb=arm.pose.bones['body'];pb.location=(0,0,.014*(1-smooth(t)) if name=='equip' else 0);key(pb,frame)
   pb=arm.pose.bones['mechanism'];pb.location=(0,-.003*math.sin(math.pi*t)**2 if name=='fire' else 0,0);key(pb,frame)
  actions.append(aclip)
 finish_actions(arm,actions)
 meta={'id':'wp01_web','name':'赤铜蛛丝腕式发射器','kind':'tool','dimensionsUnit':'meter','textureSize':1024,'originalTaskId':a['taskId'],'originalSha256':a['sha256'],'sideFiles':['wp01_web_right.glb','wp01_web_left.glb'],'clips':['equip','fire'],'muzzleForwardMeters':.17,'fireDurationSeconds':.2,'leftMethod':'Baked X mirror with reversed winding; tangents regenerated by importer'}
 export(o,arm,'wp01_web_right',dict(meta))
 # Bake reflection in geometry/UV tangents; bone axes are unchanged because all joint centres are on X=0.
 o.data.transform(Matrix.Diagonal((-1,1,1,1)));o.data.flip_normals();o.data.update();export(o,arm,'wp01_web_left',dict(meta))
 (OUT/'wp01_web.json').write_text(json.dumps(meta,ensure_ascii=False,indent=2))

for a in manifest['assets']:
 if a['id']=='copper_wrist_launcher':launcher(a)
 else:creatures(a)
