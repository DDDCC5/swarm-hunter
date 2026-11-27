package com.example.swarmhunter

import com.example.swarmhunter.game.*
import org.junit.Assert.*
import org.junit.Test

class HoldFireTest {
 private val pose=List(15){.4f}
 private fun registered()=GestureMemory().also{g->repeat(26){g.register(pose,pose,.1f)}}
 @Test fun holdingSavedPoseFiresImmediatelyAndRepeatsWithoutRelease(){
  val g=registered();var shots=0
  repeat(101){if(g.fire(0,pose,.01f))shots++}
  assertTrue("Holding for one second must produce several bounded shots",shots in 5..6)
 }
 @Test fun eachHandCanFireWithoutWaitingForTheOther(){val g=registered();assertTrue(g.fire(0,pose,.02f));assertTrue(g.fire(1,pose,.02f));assertFalse(g.fire(0,pose,.02f));assertFalse(g.fire(1,pose,.02f))}
 @Test fun stopPoseAndTrackingLossStopFireButDoNotDeleteTemplate(){val g=registered();assertTrue(g.fire(0,pose,.02f));assertFalse(g.fire(0,List(15){1.4f},.3f));assertFalse(g.fire(0,null,.3f));assertTrue(g.registered);assertTrue(g.fire(0,pose,.02f))}
 @Test fun pauseResumeDoesNotRequireChangingPose(){val g=registered();assertTrue(g.fire(0,pose,.02f));g.suspendInput();assertTrue(g.fire(0,pose,.02f));assertFalse(g.fire(0,pose,.02f))}
 @Test fun invalidHandDataNeverProducesAColdOrHeldShot(){val g=registered();assertFalse(g.fire(0,List(15){Float.NaN},.2f));assertTrue(g.fire(0,pose,.02f));assertFalse(g.fire(0,List(15){Float.NaN},.3f));assertFalse(g.fire(0,List(3){.4f},.3f))}
 @Test fun weaponCadenceLimitsEachSustainedHand(){
  val fast=registered();val slow=registered();var fastShots=0;var slowShots=0
  repeat(100){if(fast.fire(0,pose,.01f,toolFireInterval(1)))fastShots++;if(slow.fire(0,pose,.01f,toolFireInterval(3)))slowShots++}
  assertEquals(7,fastShots);assertEquals(2,slowShots)
 }
 @Test fun leavingSavedPoseStopsShotsImmediatelyEvenInsideFormerExitHysteresis(){
  val g=registered();assertTrue(g.fire(0,pose,.01f));var unwanted=0
  repeat(60){if(g.fire(0,List(15){.62f},.01f))unwanted++}
  assertEquals("An unmatched hand must never sustain fire",0,unwanted);assertFalse(g.isMatching(0))
  assertTrue(g.fire(0,pose,.01f))
 }
 @Test fun everyWeaponRespectsItsOwnMinimumIntervalAndNeverFiresAnIdleHand(){
  for(tool in 0..3){val g=registered();val times=mutableListOf<Float>();var time=0f
   repeat(200){time+=.01f;if(g.fire(0,pose,.01f,toolFireInterval(tool)))times.add(time);assertFalse(g.fire(1,List(15){1.2f},.01f,toolFireInterval(tool)))}
   assertTrue(times.size>=3);times.zipWithNext().forEach{(a,b)->assertTrue("Tool $tool interval ${b-a}",b-a>=toolFireInterval(tool)-.001f)}
   repeat(80){assertFalse(g.fire(0,List(15){1.2f},.01f,toolFireInterval(tool)))}
  }
 }
 @Test fun rapidReposingCannotBypassFireCooldown(){val g=registered();assertTrue(g.fire(0,pose,.01f));repeat(4){assertFalse(g.fire(0,List(15){1.4f},.01f));assertFalse(g.fire(0,pose,.01f))}}
}
