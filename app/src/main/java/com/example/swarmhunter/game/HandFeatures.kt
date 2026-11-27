package com.example.swarmhunter.game
import kotlin.math.*
/** OpenXR joint order: palm0/wrist1, metacarpals6/11/16/21, proximal7/12/17/22. */
fun handBendFeatures(j:List<V3>):List<Float>? {
 if(j.size<26||j.any{!it.x.isFinite()||!it.y.isFinite()||!it.z.isFinite()})return null
 // Full palm length, never the short wrist-to-metacarpal-base segment.
 val palm=(j[12]-j[1]).length();if(palm !in .025f.. .18f)return null
 val result=ArrayList<Float>(15)
 for(indices in listOf(listOf(2,3,4,5),listOf(6,7,8,9,10),listOf(11,12,13,14,15),listOf(16,17,18,19,20),listOf(21,22,23,24,25))){
  val points=indices.map{j[it]}
  for(k in 0..1){val a=points[k+1]-points[k];val b=points[k+2]-points[k+1];if(a.length()<.001f||b.length()<.001f)return null
   result.add(acos(a.normalized().dot(b.normalized()).coerceIn(-1f,1f))/PI.toFloat())}
  result.add(((points.last()-j[0]).length()/palm*.5f).coerceIn(0f,2f))
 }
 return result
}
