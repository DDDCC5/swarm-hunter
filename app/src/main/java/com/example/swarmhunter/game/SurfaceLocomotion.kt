package com.example.swarmhunter.game
import kotlin.math.*
/** Quarter-circle foot path; tangent and body normal join both measured planes. */
data class WallFloorTurn(val corner:V3,val wallNormal:V3,val radius:Float,val floorId:String,var progress:Float=0f,val endNormal:V3=V3(0f,1f,0f)) {
 fun position(t:Float):V3 {val a=t.coerceIn(0f,1f)*PI.toFloat()/2;return corner+wallNormal*(radius*(1-cos(a)))+endNormal*(radius*(1-sin(a)))}
 fun normal(t:Float):V3 {val a=t.coerceIn(0f,1f)*PI.toFloat()/2;return (wallNormal*cos(a)+endNormal*sin(a)).normalized()}
 fun tangent(t:Float):V3 {val a=t.coerceIn(0f,1f)*PI.toFloat()/2;return (wallNormal*sin(a)-endNormal*cos(a)).normalized()}
}

/** Construct from perpendicular wall distance: ray length is wrong for an oblique approach. */
fun ceilingWallTurn(position:V3,normal:V3,hit:SurfaceHit):WallFloorTurn? {
 val wallNormal=hit.normal.normalized()
 if(kotlin.math.abs(normal.dot(wallNormal))>.03f)return null
 val radius=(position-hit.point).dot(wallNormal)
 if(radius !in .025f.. .12f)return null
 val corner=position-wallNormal*radius
 return WallFloorTurn(corner,normal,radius,hit.surface.id,endNormal=wallNormal)
}
