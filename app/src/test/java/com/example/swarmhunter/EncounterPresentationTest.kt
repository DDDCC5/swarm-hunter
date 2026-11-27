package com.example.swarmhunter
import com.example.swarmhunter.game.*
import org.junit.Assert.*
import org.junit.Test
class EncounterPresentationTest {
 @Test fun arrivalAudioCannotStartBeforeVisibleModelAndRunsOnlyOnce(){val cue=ArrivalCue();assertFalse(cue.start(false));assertTrue(cue.start(true));assertFalse(cue.start(true))}
 @Test fun visibleEmergenceNeverHidesTheWholeCreatureBehindPlane(){for(i in 0..100){assertEquals(0f,visibleEmergencePosition(i/100f).z,0f)};assertTrue(visibleEmergencePosition(1f).y>0f)}
 @Test fun smallerCreaturesHaveSmallerProportionalNests(){assertTrue(CREATURE_MODEL_SCALE<=1f);for(i in 0..7){assertTrue(creatureSpan(i)<listOf(.12f,.22f,.18f,.36f,.28f,.15f,.14f,.13f)[i]*1.6f);assertTrue(nestScale(i)<1.8f);assertTrue(nestScale(i)*.36f>creatureSpan(i))};assertTrue(nestScale(0)<nestScale(3))}
 @Test fun ceilingHasAnInwardDownwardNormal(){assertTrue(spawnNormalMatches(V3(0f,-1f,0f),RoomSurfaceKind.CEILING));assertFalse(spawnNormalMatches(V3(0f,1f,0f),RoomSurfaceKind.CEILING));assertTrue(spawnNormalMatches(V3(1f,0f,0f),RoomSurfaceKind.WALL))}
 @Test fun deathAllowsFullFallBeforeDissolvingAndCleanup(){assertEquals(1f,deathOpacity(.9f),0f);assertFalse(deathDissolveDue(.9f));assertTrue(deathDissolveDue(1.05f));assertTrue(deathOpacity(1.3f)<1f);assertEquals(0f,deathOpacity(1.7f),0f)}
 @Test fun speciesSoundsHaveDistinctRhythmsAndArrivalDurationsMatchTheirAudio(){assertEquals(8,(0..7).map{creatureSoundRate(it)}.toSet().size);for(i in 0..7){assertTrue(creatureSoundRate(i)in .25f..4f);assertEquals(1.25f,arrivalDuration(i)*creatureSoundRate(i),.0001f)}}
 @Test fun ceilingTurnJoinsWallWithoutTeleportingOrFlipping(){val turn=WallFloorTurn(V3(0f,2.5f,0f),V3(0f,-1f,0f),.12f,"wall",endNormal=V3(0f,0f,1f));assertTrue((turn.position(0f)-V3(0f,2.5f,.12f)).length()<.0001f);assertTrue((turn.position(1f)-V3(0f,2.38f,0f)).length()<.0001f);assertTrue(turn.normal(1f).dot(V3(0f,0f,1f))>.999f)}
 @Test fun obliqueCeilingApproachStartsTurnAtTheActualCreaturePosition(){
  val position=V3(.06f,2.5f,.06f);val wall=RoomSurface("diagonal",RoomSurfaceKind.WALL,V3(0f,0f,0f),Q4(0f,0f,0f,1f),emptyList(),emptyList());val hit=SurfaceHit(wall,.12f,V3(-.06f,2.48f,.06f),V3(1f,0f,1f).normalized())
  val turn=ceilingWallTurn(position,V3(0f,-1f,0f),hit)!!;assertTrue("Corner must not teleport",(turn.position(0f)-position).length()<.00001f)
 }
 @Test fun cornerMustNotClampRadiusAndJumpTowardADistantWall(){val wall=RoomSurface("wall",RoomSurfaceKind.WALL,V3(0f,0f,0f),Q4(0f,0f,0f,1f),emptyList(),emptyList());assertNull(ceilingWallTurn(V3(0f,2.5f,.13f),V3(0f,-1f,0f),SurfaceHit(wall,.13f,V3(0f,2.48f,0f),V3(0f,0f,1f))))}
 @Test fun densityGraduallyIncreasesWithoutFrontLoadingCrowds(){for(level in HuntDifficulty.entries){val first=encounterSettings(level,0f);val late=encounterSettings(level,150f);assertTrue(first.capacity<=4);assertTrue(late.capacity>first.capacity);assertTrue(first.spawnSeconds>late.spawnSeconds);var prior=first;for(i in 1..150){val next=encounterSettings(level,i.toFloat());assertTrue(next.capacity>=prior.capacity);assertTrue(next.spawnSeconds<=prior.spawnSeconds);prior=next}}}
}
