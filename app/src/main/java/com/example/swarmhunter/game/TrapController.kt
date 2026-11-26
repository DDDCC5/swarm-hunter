package com.example.swarmhunter.game

data class PlacedTrap(val id:Int,val anchorId:String,val localPosition:V3,val localNormal:V3,var age:Float=0f)
/** Release-gated official pinch, independent of the user's registered firing pose. */
class TrapController {
 val traps=mutableListOf<PlacedTrap>()
 private var serial=0
 private var validatedRevision=-1
 private val released=booleanArrayOf(false,false)
 fun locationFree(point:V3,room:RoomSurfaceRegistry)=traps.none{t->room.surfaces[t.anchorId]?.world(t.localPosition)?.let{(it-point).length()<.24f}==true}
 fun pinch(hand:Int,pinched:Boolean?,candidate:SurfaceHit?,room:RoomSurfaceRegistry):PlacedTrap? {
  if(hand !in 0..1)return null
  if(pinched==null){released[hand]=false;return null}
  if(!pinched){released[hand]=true;return null}
  if(!released[hand])return null
  released[hand]=false
  if(candidate==null||traps.size>=2||!room.canPlace(candidate))return null
  if(!locationFree(candidate.point,room))return null
  val s=candidate.surface;val q=s.rotation
  return PlacedTrap(++serial,s.id,s.local(candidate.point),Q4(-q.x,-q.y,-q.z,q.w).rotate(candidate.normal)).also{traps.add(it)}
 }
 fun reconcile(room:RoomSurfaceRegistry){
  if(!room.available||validatedRevision==room.revision)return
  validatedRevision=room.revision
  traps.removeAll{t->
  val s=room.surfaces[t.anchorId]
  s==null||!room.canPlace(SurfaceHit(s,0f,s.world(t.localPosition),s.rotation.rotate(t.localNormal)))
 }}
 fun undo():PlacedTrap?=if(traps.isEmpty())null else traps.removeAt(traps.lastIndex)
 fun reset(){traps.clear();validatedRevision=-1;suspendInput()}
 fun suspendInput(){released.fill(false)}
 fun tick(dt:Float,room:RoomSurfaceRegistry,enemies:List<Pair<Int,V3>>):List<Pair<PlacedTrap,List<Int>>> {
  if(!room.available)return emptyList()
  reconcile(room);val activated=mutableListOf<Pair<PlacedTrap,List<Int>>>()
  for(t in traps.toList()){
   t.age+=dt.coerceAtLeast(0f);if(t.age<1f)continue
   val s=room.surfaces[t.anchorId]?:continue;val position=s.world(t.localPosition)+s.rotation.rotate(t.localNormal)*.03f
   val hits=enemies.filter{(_,p)->val delta=p-position;val length=delta.length();length<=1.25f&&(length<.001f||room.cast(position,delta,length,wallsOnly=true)==null)}.map{it.first}
   if(hits.isNotEmpty()){traps.remove(t);activated.add(t to hits)}
  };return activated
 }
}
