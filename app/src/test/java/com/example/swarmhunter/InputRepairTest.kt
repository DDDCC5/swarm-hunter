package com.example.swarmhunter
import com.example.swarmhunter.game.*
import org.junit.Assert.*
import org.junit.Test
class InputRepairTest {
 @Test fun registrationNeverAcceptsPoseThatCannotFireAtCompletion(){
  val g=GestureMemory();var left=List(15){.1f};var right=List(15){.36f}
  repeat(22){g.register(left,right,.1f)}
  left=List(15){.34f};right=List(15){.60f}
  repeat(10){g.register(left,right,.1f)}
  assertTrue(g.registered)
  assertTrue("Left current pose must match its own registered hand",g.fire(0,left,.01f))
  assertTrue("Right current pose must match its own registered hand",g.fire(1,right,.01f))
 }
 @Test fun portalExitHasNoNormalLiftOrDetachmentSnap(){
  val p=emergencePosition(1f,.3f)
  assertEquals("Exit stays at the authored foot plane",0f,p.z,.00001f)
  val before=emergencePosition(.999f,.3f)
  assertTrue((p-before).length()<.002f)
 }
}
