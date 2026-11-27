package com.example.swarmhunter

import com.example.swarmhunter.game.*
import org.junit.Assert.*
import org.junit.Test

class HuntEngineTest {
    private val pose=List(15){0.4f}
    @Test fun registrationNeedsMatchingStableHands() {
        val r=GestureMemory()
        repeat(30){r.register(pose,List(15){1.4f},.1f)}
        assertFalse(r.registered)
        repeat(26){r.register(pose,pose,.1f)}
        assertTrue(r.registered)
    }
    @Test fun customPoseRejectsUnknownShapeAndRateLimitsHeldShots() {
        val r=GestureMemory();repeat(26){r.register(pose,pose,.1f)}
        assertTrue(r.fire(0,pose,.02f))
        repeat(10){r.fire(0,List(15){1.4f},.02f)}
        assertTrue(r.fire(0,pose,.02f))
        assertFalse(r.fire(0,pose,.02f))
        repeat(10){r.fire(0,List(15){1.4f},.02f)}
        assertTrue(r.fire(0,pose,.02f))
        assertTrue(r.registered)
    }
    @Test fun oneSecondTrackingGapPreservesRegisteredPoseButDoesNotFire() {
        val r=GestureMemory();repeat(26){r.register(pose,pose,.1f)}
        repeat(40){assertFalse(r.fire(0,null,.02f))}
        assertTrue(r.registered)
    }
    @Test fun damageHasTwoSecondInvulnerabilityAndThreeLives() {
        val g=HuntEngine();g.start()
        assertTrue(g.hurt());assertEquals(2,g.lives)
        assertFalse(g.hurt());g.tick(1.9f);assertFalse(g.hurt())
        g.tick(.11f);assertTrue(g.hurt());g.tick(2.01f);assertTrue(g.hurt())
        assertEquals(HuntPhase.RESULTS,g.phase);assertEquals(0,g.lives)
    }
    @Test fun survivalCountsUpAndPauseDoesNotAdvance() {
        val g=HuntEngine();g.start();g.tick(12f);g.paused=true;g.tick(7f)
        assertEquals(12f,g.survival,0.001f)
    }
    @Test fun unseenThreatCannotDamageAndKillsCountOnce() {
        val g=HuntEngine();g.start();val e=g.spawn(0,V3(0f,1.3f,2f))
        assertFalse(g.attack(e.id));assertEquals(3,g.lives)
        g.reveal(e.id);assertTrue(g.kill(e.id));assertFalse(g.kill(e.id));assertEquals(1,g.kills)
    }
    @Test fun retryResetsStatsAndInvulnerability() {
        val g=HuntEngine();g.start();g.tick(22f);g.hurt();g.start()
        assertEquals(3,g.lives);assertEquals(0,g.kills);assertEquals(0f,g.survival,0f);assertEquals(0f,g.invulnerable,0f)
    }
    @Test fun nearestRayIntersectionRejectsTargetsBehindTheHand() {
        assertNull(raySphere(V3(0f,0f,0f),V3(0f,0f,-1f),V3(0f,0f,1f),.2f))
        assertEquals(1.8f,raySphere(V3(0f,0f,0f),V3(0f,0f,-1f),V3(0f,0f,-2f),.2f)!!,.001f)
    }
    @Test fun attackCueOccursOncePerCycle() {
        val cycle=AttackCycle();var cues=0;var strikes=0
        repeat(300){when(cycle.tick(.01f,true)){AttackEvent.PREPARE->cues++;AttackEvent.STRIKE->strikes++;else->Unit}}
        assertEquals(2,cues);assertEquals(2,strikes)
    }
    @Test fun pauseDisarmsGestureWithoutDeletingTemplate() {
        val r=GestureMemory();repeat(26){r.register(pose,pose,.1f)}
        repeat(10){r.fire(0,List(15){1.4f},.02f)};r.suspendInput()
        assertFalse(r.isMatching(0));assertTrue(r.fire(0,pose,.02f));assertTrue(r.registered)
    }
    @Test fun nearestEligibleHitFiltersDeadOrHiddenTargets() {
        val g=HuntEngine();g.start();val a=g.spawn(0,V3(0f,0f,-1f));val b=g.spawn(1,V3(0f,0f,-2f));g.reveal(b.id)
        assertEquals(b.id,g.nearestEligibleHit(listOf(a.id to 1f,b.id to 2f)))
        g.reveal(a.id);assertEquals(a.id,g.nearestEligibleHit(listOf(b.id to 2f,a.id to 1f)))
        g.kill(a.id);assertEquals(b.id,g.nearestEligibleHit(listOf(a.id to 1f,b.id to 2f)))
    }
    @Test fun registrationToleratesSmallContinuousTrackingJitter() {
        val r=GestureMemory()
        repeat(60){i->val v=if(i%2==0).30f else .50f;r.register(List(15){v},List(15){v+.025f},.05f)}
        assertTrue("Small matching-hand jitter must not repeatedly reset capture",r.registered)
    }
    @Test fun briefPoseMismatchPausesInsteadOfErasingRegistration() {
        val r=GestureMemory();repeat(15){r.register(pose,pose,.1f)}
        val before=r.progress;repeat(5){r.register(pose,List(15){1.4f},.1f)}
        assertEquals(before,r.progress,.001f)
        repeat(15){r.register(pose,pose,.1f)};assertTrue(r.registered)
    }
    @Test fun shortOcclusionDoesNotCountTowardHoldOrResetIt() {
        val r=GestureMemory();repeat(12){r.register(pose,pose,.1f)}
        repeat(6){r.register(null,null,.1f)};repeat(10){r.register(pose,pose,.1f)}
        assertFalse(r.registered)
        repeat(5){r.register(pose,pose,.1f)};assertTrue(r.registered)
    }
    @Test fun sustainedMismatchStillRequiresNewStableCapture() {
        val r=GestureMemory();repeat(15){r.register(pose,pose,.1f)}
        repeat(12){r.register(pose,List(15){1.4f},.1f)}
        assertFalse(r.registered);assertEquals(0f,r.progress,.001f)
    }
    @Test fun combatCanContinueWithEitherHandAndToleratesBriefDropout() {
        val tracking=CombatTrackingGate()
        assertFalse(tracking.update(.02f,false,false))
        assertTrue(tracking.update(.02f,true,false))
        repeat(4){assertTrue(tracking.update(.1f,false,false))}
        assertTrue(tracking.update(.1f,false,true))
        repeat(11){tracking.update(.1f,false,false)}
        assertFalse(tracking.ready)
        assertTrue(tracking.update(.02f,true,false))
    }
    private fun near(a:V3,b:V3){assertEquals(a.x,b.x,.0001f);assertEquals(a.y,b.y,.0001f);assertEquals(a.z,b.z,.0001f)}
    @Test fun surfaceOrientationAlignsAuthoredUpAndForwardOnFloorAndWall() {
        val floor=surfaceRotation(V3(0f,1f,0f),V3(0f,0f,-1f))
        near(V3(0f,1f,0f),floor.rotate(V3(0f,1f,0f)));near(V3(0f,0f,-1f),floor.rotate(V3(0f,0f,-1f)))
        val wall=surfaceRotation(V3(0f,0f,1f),V3(0f,-1f,0f))
        near(V3(0f,0f,1f),wall.rotate(V3(0f,1f,0f)));near(V3(0f,-1f,0f),wall.rotate(V3(0f,0f,-1f)))
        val portal=wall*Q4.axisAngle(V3(1f,0f,0f),-Math.PI.toFloat()/2f)
        near(V3(0f,0f,1f),portal.rotate(V3(0f,0f,1f)))
    }
    @Test fun emergenceMovesWholeRootFromBehindToOutsidePortal() {
        val first=emergencePosition(0f,.30f);val last=emergencePosition(1f,.30f)
        assertTrue(first.z<-.25f);assertEquals(0f,last.z,.00001f)
        var old=first.z
        repeat(100){i->val p=emergencePosition((i+1)/100f,.30f);assertTrue(p.z>=old);old=p.z}
    }
    @Test fun beamVisualEndsAtTheMeasuredHitDistance() {
        assertEquals(4f,.17f+.9f*beamLengthScale(4f),.0001f)
        assertEquals(2f,.17f+.9f*beamLengthScale(2f),.0001f)
    }
    @Test fun handRayUsesOfficialAimAndFallsBackToHandPositionFacingForward() {
        val head=V3(0f,1.6f,0f);val forward=V3(0f,0f,-1f);val right=V3(1f,0f,0f)
        near(V3(1f,0f,0f),handRayDirection(head,V3(.2f,1.3f,-.6f),forward,right,1,V3(2f,0f,0f)))
        val fallback=handRayDirection(head,V3(.18f,1.3f,-.65f),forward,right,1,null)
        assertTrue(fallback.dot(forward)>.9f)
        near(forward,handRayDirection(head,V3(.15f,1.42f,0f),forward,right,1,null))
    }
    @Test fun handRayVisualAndProjectileShareDirectionIncludingVerticalAim() {
        for(d in listOf(V3(.3f,-.4f,-1f),V3(0f,1f,0f),V3(0f,-1f,0f),V3(1f,0f,0f))){
            val rotation=handRayRotation(d,V3(0f,1f,0f))
            near(d.normalized(),rotation.rotate(V3(0f,0f,-1f)))
        }
    }

    @Test fun pickupToolsStayVisibleUntilEquippedEvenWhenHandsAreAbsent() {
        assertTrue(toolVisible(HuntPhase.PICKUP,false,false))
        assertTrue(toolVisible(HuntPhase.PICKUP,false,true))
        assertTrue(toolVisible(HuntPhase.PICKUP,true,true))
        assertFalse(toolVisible(HuntPhase.PICKUP,true,false))
        assertTrue(toolVisible(HuntPhase.RUNNING,true,true))
        assertFalse(toolVisible(HuntPhase.RUNNING,false,true))
        assertFalse(toolVisible(HuntPhase.RUNNING,true,false))
        for(phase in listOf(HuntPhase.LOGO,HuntPhase.SAFETY,HuntPhase.GESTURE,HuntPhase.TOOLS,HuntPhase.RESULTS))assertFalse(toolVisible(phase,true,true))
    }

    @Test fun silkTrailGrowsWithTravelInsteadOfStartingBehindTheUser() {
        assertTrue(projectileTrailLength(0f)<.17f)
        assertEquals(.1f,projectileTrailLength(.1f),.0001f)
        assertEquals(.75f,projectileTrailLength(2f),.0001f)
        assertTrue(projectileTrailLength(-1f)>0f)
    }

    @Test fun palmContactEquipsEvenWhenWristHasNotReachedTheToolCenter() {
        val tool=V3(.24f,1.3f,-.65f)
        assertTrue(toolPickupInReach(tool,tool+V3(0f,0f,.03f),tool+V3(0f,0f,.24f)))
        assertTrue(toolPickupInReach(tool,tool+V3(0f,0f,.23f),tool+V3(0f,0f,.05f)))
    }
    @Test fun proximityPickupRejectsMissingHandsAndDistantHands() {
        val tool=V3(.24f,1.3f,-.65f)
        assertFalse(toolPickupInReach(tool,null,null))
        assertFalse(toolPickupInReach(tool,tool+V3(.4f,0f,0f),tool+V3(.5f,0f,0f)))
        assertFalse(toolPickupInReach(tool,V3(Float.NaN,0f,0f),null))
    }

    @Test fun returningFromToolsForgetsOldPoseAndRecordsNewOne() {
        val engine=HuntEngine();val memory=GestureMemory();val oldPose=List(15){.4f};val newPose=List(15){1.1f}
        repeat(26){memory.register(oldPose,oldPose,.1f)}
        assertTrue(memory.fire(0,oldPose,.02f));assertTrue(memory.isMatching(0))
        engine.phase=HuntPhase.TOOLS
        assertTrue(engine.returnToGesture(memory))
        assertEquals(HuntPhase.GESTURE,engine.phase);assertFalse(memory.registered);assertEquals(0f,memory.progress,0f)
        assertFalse(memory.isMatching(0));assertFalse(memory.fire(0,oldPose,.02f))
        repeat(26){memory.register(newPose,newPose,.1f)};assertTrue(memory.registered)
        repeat(10){assertFalse(memory.fire(0,oldPose,.02f))}
        assertTrue(memory.fire(0,newPose,.02f))
    }
    @Test fun reregistrationDoesNotResetAnActiveMission() {
        val engine=HuntEngine();val memory=GestureMemory();repeat(26){memory.register(pose,pose,.1f)}
        engine.start()
        assertFalse(engine.returnToGesture(memory));assertEquals(HuntPhase.RUNNING,engine.phase);assertTrue(memory.registered)
    }

    @Test fun wallCreatureKeepsVerticalPoseUntilActuallyReachingTheFloor() {
        assertFalse(wallReachedFloor(.10f,0f))
        assertFalse(wallReachedFloor(.30f,.20f))
        assertTrue(wallReachedFloor(.22f,.20f))
    }
    @Test fun creaturePathIsProjectedOntoItsOwnWallOrFloor() {
        near(V3(1f,.5f,1.988f),pointOnSurface(V3(1f,.5f,1.7f),V3(0f,0f,2f),V3(0f,0f,-1f)))
        near(V3(1f,.012f,2f),pointOnSurface(V3(1f,.5f,2f),V3(0f,0f,0f),V3(0f,1f,0f)))
    }
    @Test fun wallAndFloorMetadataCannotUseTheWrongGeometricNormal() {
        assertTrue(surfaceNormalMatchesType(V3(0f,1f,0f),false))
        assertTrue(surfaceNormalMatchesType(V3(1f,0f,0f),true))
        assertFalse(surfaceNormalMatchesType(V3(0f,1f,0f),true))
        assertFalse(surfaceNormalMatchesType(V3(1f,0f,0f),false))
    }

}
