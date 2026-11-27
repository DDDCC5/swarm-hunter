package com.example.swarmhunter.game

data class CombatHit(val distance:Float,val enemy:Int?=null,val surface:SurfaceHit?=null)
/** A wall wins ties so a body collider poking through a wall cannot be shot through it. */
fun nearestCombatHit(enemies:List<Pair<Int,Float>>,surface:SurfaceHit?,limit:Float):CombatHit? {
 val enemy=enemies.filter{it.second.isFinite()&&it.second>=0f&&it.second<=limit}.minByOrNull{it.second}
 return if(surface!=null&&surface.distance<=limit&&(enemy==null||surface.distance<=enemy.second+.001f))CombatHit(surface.distance,surface=surface)
 else enemy?.let{CombatHit(it.second,enemy=it.first)}
}
class RicochetState(var direction:V3,var speed:Float=7f) {
 var count=0;private set
 var travelled=0f;private set
 var age=0f;private set
 fun advance(dt:Float,distance:Float){age+=dt.coerceAtLeast(0f);travelled+=distance.coerceAtLeast(0f)}
 fun reflect(normal:V3):Boolean {
  if(count>=2||expired)return false
  val n=normal.normalized();if(direction.dot(n)>=-.01f)return false
  direction=(direction-n*(2f*direction.dot(n))).normalized();speed*=.8f;count++;return true
 }
 val expired get()=age>=1.4f||travelled>=10f
}

/** The clear muzzle segment is part of the travel budget measured from the wrist. */
fun launchedMotion(direction:V3,speed:Float,muzzleDistance:Float=.17f)=RicochetState(direction,speed).also{it.advance(0f,muzzleDistance.coerceAtLeast(0f))}
