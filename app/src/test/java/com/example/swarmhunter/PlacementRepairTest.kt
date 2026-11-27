package com.example.swarmhunter
import com.example.swarmhunter.game.*
import org.junit.Assert.*
import org.junit.Test
class PlacementRepairTest {
 @Test fun skeletonPinchWorksWhenOfficialPinchIsNotReady(){val p=PlacementPinch();assertEquals(false,p.update(null,.7f));assertEquals(true,p.update(null,.20f));assertEquals(true,p.update(null,.36f));assertEquals(false,p.update(null,.7f))}
 @Test fun clearSkeletonOpenAndCloseOverrideAmbiguousOfficialStrength(){
  val p=PlacementPinch();assertEquals(false,p.update(.5f,.7f));assertEquals(true,p.update(.6f,.20f));assertEquals(false,p.update(.46f,.58f))
 }
 @Test fun trackingLossClearsPinchInsteadOfReusingIt(){val p=PlacementPinch();p.update(null,.7f);p.update(null,.2f);assertNull(p.update(null,null));assertNull(p.update(null,.36f))}
 @Test fun officialStrengthUsesHysteresis(){val p=PlacementPinch();assertEquals(false,p.update(.05f,null));assertEquals(true,p.update(.9f,null));assertEquals(true,p.update(.5f,null));assertEquals(false,p.update(.2f,null))}
 @Test fun invalidTrackingCoordinatesCannotProduceSurfaceHits(){
  val room=RoomSurfaceRegistry();room.replace(listOf(RoomSurface("wall",RoomSurfaceKind.WALL,V3(0f,0f,-1f),Q4(0f,0f,0f,1f),listOf(V3(-2f,-2f,0f),V3(2f,-2f,0f),V3(2f,2f,0f),V3(-2f,2f,0f)),listOf(0,1,2,0,2,3))))
  assertNull(room.cast(V3(Float.NaN,0f,0f),V3(0f,0f,-1f),3f))
  assertNull(room.cast(V3(0f,0f,0f),V3(Float.NaN,0f,-1f),3f))
 }
 @Test fun cornerPathJoinsWallAndFloorWithContinuousNormals(){val c=WallFloorTurn(V3(0f,0f,0f),V3(0f,0f,1f),.12f,"floor")
  assertEquals(V3(0f,.12f,0f),c.position(0f));assertTrue((c.position(1f)-V3(0f,0f,.12f)).length()<.0001f)
  assertTrue(c.normal(0f).dot(V3(0f,0f,1f))>.999f);assertTrue(c.normal(1f).dot(V3(0f,1f,0f))>.999f)
  for(i in 0..99){assertTrue((c.position((i+1)/100f)-c.position(i/100f)).length()<.003f);assertTrue(c.normal((i+1)/100f).dot(c.normal(i/100f))>.999f)}
 }
}
