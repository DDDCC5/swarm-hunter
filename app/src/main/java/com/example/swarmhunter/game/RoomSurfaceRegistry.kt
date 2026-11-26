package com.example.swarmhunter.game

import kotlin.math.*

enum class RoomSurfaceKind { FLOOR, WALL, TABLE }
data class RoomSurface(val id:String,val kind:RoomSurfaceKind,val origin:V3,val rotation:Q4,val vertices:List<V3>,val indices:List<Int>) {
    fun world(p:V3)=origin+rotation.rotate(p)
    fun local(p:V3)=Q4(-rotation.x,-rotation.y,-rotation.z,rotation.w).rotate(p-origin)
}
data class SurfaceHit(val surface:RoomSurface,val distance:Float,val point:V3,val normal:V3)
/** Finite triangle bounds, not infinite walls. All values use the game root's metre space. */
class RoomSurfaceRegistry {
    val surfaces=linkedMapOf<String,RoomSurface>()
    private val worldVertices=mutableMapOf<String,List<V3>>()
    var available=true
    var revision=0;private set
    fun replace(all:List<RoomSurface>){
        val next=all.filter{it.vertices.size>=3&&it.indices.size>=3}.associateBy{it.id}
        if(next==surfaces)return
        surfaces.clear();surfaces.putAll(next);worldVertices.clear();next.values.forEach{s->worldVertices[s.id]=s.vertices.map(s::world)};revision++
    }
    fun cast(origin:V3,direction:V3,maxDistance:Float,wallsOnly:Boolean=false):SurfaceHit? = castInternal(origin,direction,maxDistance,wallsOnly,null)
    private fun castInternal(origin:V3,direction:V3,maxDistance:Float,wallsOnly:Boolean,surfaceId:String?):SurfaceHit? {
        if(!available||maxDistance<=0f||!maxDistance.isFinite()||direction.length()<.00001f)return null
        val d=direction.normalized();var closest:SurfaceHit?=null
        for(s in surfaces.values){
            if((surfaceId!=null&&s.id!=surfaceId)||(wallsOnly&&s.kind!=RoomSurfaceKind.WALL))continue
            val vertices=worldVertices[s.id]?:continue
            for(i in 0 until s.indices.size-2 step 3){
                val ids=s.indices.subList(i,i+3);if(ids.any{it !in s.vertices.indices})continue
                val a=vertices[ids[0]];val e1=vertices[ids[1]]-a;val e2=vertices[ids[2]]-a
                val p=d.cross(e2);val det=e1.dot(p);if(abs(det)<.0000001f)continue
                val inv=1f/det;val offset=origin-a;val u=offset.dot(p)*inv;if(u<-.00001f||u>1.00001f)continue
                val q=offset.cross(e1);val v=d.dot(q)*inv;if(v<-.00001f||u+v>1.00001f)continue
                val distance=e2.dot(q)*inv
                if(distance<.0005f||distance>maxDistance||distance>=(closest?.distance?:Float.MAX_VALUE))continue
                var normal=e1.cross(e2).normalized();if(normal.dot(d)>0)normal=normal*-1f
                closest=SurfaceHit(s,distance,origin+d*distance,normal)
            }
        };return closest
    }
    fun canPlace(hit:SurfaceHit,radius:Float=.10f):Boolean {
        if(surfaces[hit.surface.id]!=hit.surface)return false
        val n=hit.normal;val tangent=n.cross(if(abs(n.y)<.9f)V3(0f,1f,0f)else V3(1f,0f,0f)).normalized();val bitangent=n.cross(tangent)
        // The device footprint must fit the triangle union, including concave boundaries.
        return (0..8).all{i->val a=i*PI.toFloat()/4;val p=hit.point+if(i==8)V3(0f,0f,0f)else(tangent*cos(a)+bitangent*sin(a))*radius
            val h=castInternal(p+n*.04f,n*-1f,.08f,false,hit.surface.id);h?.surface?.id==hit.surface.id}
    }
}
