package com.example.swarmhunter
import com.example.swarmhunter.game.*
import org.junit.Assert.*
import org.junit.Test
class CreatureAnimationTest {
 @Test fun strideAndModelScaleDetermineFootSpeed(){
  assertEquals(7.8125f,creaturePlaybackSpeed(.6f,.048f,1.6f),.0001f)
  assertEquals(1.5625f,creaturePlaybackSpeed(.12f,.048f,1.6f),.0001f)
  assertEquals(0f,creaturePlaybackSpeed(0f,.048f),.0001f)
 }

 @Test fun attackCannotRestartDuringRecoveryAfterLeavingRange(){
  val c=AttackCycle();assertEquals(AttackEvent.PREPARE,c.tick(.01f,true))
  repeat(13){c.tick(.05f,true)}
  // A new preparation before 1.2s would truncate the recovery clip.
  c.tick(.01f,false)
  assertNull(c.tick(.01f,true))
 }
 @Test fun contactOccursAtAuthoredFrameNotOldSixTenths(){
  val c=AttackCycle();c.tick(0f,true)
  repeat(10){assertNull(c.tick(.05f,true))}
  assertEquals(AttackEvent.STRIKE,c.tick(.06f,true))
 }
}
