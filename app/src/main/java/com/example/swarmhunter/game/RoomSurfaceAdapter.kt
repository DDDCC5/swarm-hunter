package com.example.swarmhunter.game

import android.util.Log
import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.ecs.resource.*
import com.pico.spatial.core.ecs.simulation.*
import com.pico.spatial.core.math.*
import com.pico.spatial.sense.plane.*
import com.pico.spatial.sense.base.SemanticLabelType

/** Invisible, bounded native mesh colliders share the combat root's physics world. */
class RoomSurfaceAdapter(private val root:Entity,val registry:RoomSurfaceRegistry) {
 private data class Proxy(val snapshot:RoomSurface,val entity:Entity,val mesh:MeshResource,val shape:ShapeResource)
 private val proxies=mutableMapOf<String,Proxy>()
 fun setAvailable(available:Boolean){if(registry.available==available)return;registry.available=available;proxies.values.forEach{it.entity.enabled=available}}
 val hasProxies get()=proxies.isNotEmpty()
 fun surfaceFor(entity:Entity)=proxies.values.firstOrNull{it.entity==entity}?.snapshot
 fun update(anchors:Collection<PlaneAnchor>){
  val snapshots=anchors.mapNotNull{a->
   val kind=when(a.semantics){SemanticLabelType.WALL->RoomSurfaceKind.WALL;SemanticLabelType.FLOOR->RoomSurfaceKind.FLOOR;SemanticLabelType.TABLE->RoomSurfaceKind.TABLE;SemanticLabelType.CEILING->RoomSurfaceKind.CEILING;else->return@mapNotNull null}
   val p=root.convertPositionFrom(a.transform.position,null);val q=root.convertRotationFrom(a.transform.rotation.toQuat(),null)
   RoomSurface(a.anchorUUID.toString(),kind,V3(p.x,p.y,p.z),Q4(q.x,q.y,q.z,q.w),a.vertices.map{V3(it.x,it.y,it.z)},a.indices.toList())
  }
  registry.replace(snapshots)
  for(id in proxies.keys.toList())if(snapshots.none{it.id==id&&it==proxies[id]?.snapshot})remove(id)
  for(s in registry.surfaces.values)if(s.id !in proxies){
   var mesh:MeshResource?=null;var shape:ShapeResource?=null;var e:Entity?=null
   try{
    mesh=MeshResource.loadFromPlaneAnchor(java.util.UUID.fromString(s.id)).also{it.toGlobal()}
    shape=ShapeResource.createStaticMesh(mesh).also{it.toGlobal()}
    e=Entity();root.addChild(e);e.enabled=registry.available
    e.components[TransformComponent::class.java]!!.apply{position=Vector3(s.origin.x,s.origin.y,s.origin.z);quaternion=Quat(s.rotation.x,s.rotation.y,s.rotation.z,s.rotation.w)}
    e.components.set(CollisionComponent(listOf(shape),PhysicsMaterialResource()))
    // Absence of RigidBodyComponent is the SDK static-body contract.
    proxies[s.id]=Proxy(s,e,mesh,shape)
   }catch(ex:Exception){if(e?.valid==true)e.destroy();shape?.close();mesh?.close();Log.w("SwarmHunter","Surface proxy unavailable; bounded triangle fallback: ${s.id}",ex)}
  }
 }
 fun verifyNative(scene:Scene):String {
  val proxy=proxies.values.firstOrNull()?:return "no current surface"
  val s=proxy.snapshot;val ids=s.indices.take(3)
  check(ids.size==3&&ids.all{it in s.vertices.indices})
  val a=s.world(s.vertices[ids[0]]);val b=s.world(s.vertices[ids[1]]);val c=s.world(s.vertices[ids[2]])
  val center=(a+b+c)*(1f/3f);val normal=(b-a).cross(c-a).normalized()
  val hit=scene.rayCast(Vector3(center.x+normal.x*.2f,center.y+normal.y*.2f,center.z+normal.z*.2f),Vector3(-normal.x,-normal.y,-normal.z),.4f,CollisionCastHitMode.ALL,CollisionGroup(),root).results.firstOrNull{it.entity==proxy.entity}
  check(hit!=null){"native scanned mesh not returned by raycast"}
  check(kotlin.math.abs(hit.distance-.2f)<.035f){"native plane transform mismatch: ${hit.distance}"}
  return "${proxies.size} scanned mesh proxies, native hit=${hit.distance}m"
 }
 private fun remove(id:String){proxies.remove(id)?.let{if(it.entity.valid)it.entity.destroy();it.shape.close();it.mesh.close()}}
 fun close(){proxies.keys.toList().forEach(::remove);registry.replace(emptyList())}
}
