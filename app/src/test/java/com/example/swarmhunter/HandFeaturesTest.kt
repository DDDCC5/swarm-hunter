package com.example.swarmhunter
import com.example.swarmhunter.game.*
import org.junit.Assert.*
import org.junit.Test
class HandFeaturesTest {
 private fun joints():List<V3> {val j=MutableList(26){V3(0f,0f,0f)};j[0]=V3(0f,.04f,0f)
  for((i,start)in listOf(2,6,11,16,21).withIndex()){
   val count=if(start==2)4 else 5
   for(k in 0 until count)j[start+k]=V3((i-2)*.015f,.035f+k*.025f,-k*k*.002f)
  };return j
 }
 @Test fun featuresAreIndependentOfHandWorldPositionRotationAndScale(){val j=joints();val f=handBendFeatures(j)!!;val q=Q4.axisAngle(V3(1f,1f,0f),1.1f);val moved=j.map{q.rotate(it*1.3f)+V3(2f,-1f,3f)};val g=handBendFeatures(moved)!!;for(i in f.indices)assertEquals(f[i],g[i],.0001f)}
 @Test fun degenerateAndNonFiniteJointsCannotRegisterOrFire(){assertNull(handBendFeatures(List(26){V3(0f,0f,0f)}));val bad=joints().toMutableList();bad[10]=V3(Float.NaN,0f,0f);assertNull(handBendFeatures(bad))}
 @Test fun registrationAndFiringUseTheSameFeaturesWithSmallTipNoise(){val j=joints();val f=handBendFeatures(j)!!;val g=GestureMemory();repeat(160){g.register(f,f,1f/60)};assertTrue(g.registered);val noisy=j.toMutableList();noisy[10]=noisy[10]+V3(.004f,0f,0f);assertTrue(g.fire(0,handBendFeatures(noisy),1f/60));assertTrue(g.fire(1,f,1f/60))}
}
