package com.example.swarmhunter

import com.example.swarmhunter.game.*
import org.junit.Assert.*
import org.junit.Test

class HandAttachmentTrackerTest {
    private val origin=V3(.18f,1.3f,-.65f)
    private val forward=V3(0f,0f,-1f)
    @Test fun stationaryHandRejectsSubMillimeterAndSubDegreeJitter() {
        val tracker=HandAttachmentTracker();tracker.update(origin,forward,.016f)
        repeat(120){i->val sign=if(i%2==0)1f else -1f
            val pose=tracker.update(origin+V3(.001f*sign,0f,0f),V3(.004f*sign,0f,-1f),.016f)
            assertTrue((pose.origin-origin).length()<.0001f)
            assertTrue(pose.direction.dot(forward)>.99999f)
        }
    }
    @Test fun deliberateMovementFollowsWithinTwoTenthsWithoutOvershoot() {
        val tracker=HandAttachmentTracker();tracker.update(origin,forward,.016f)
        val destination=origin+V3(.20f,0f,0f);val direction=V3(.4f,0f,-1f).normalized()
        var pose=tracker.update(destination,direction,.016f)
        repeat(9){pose=tracker.update(destination,direction,.016f)}
        assertTrue((pose.origin-destination).length()<.01f)
        assertTrue(pose.origin.x<=destination.x)
        assertTrue(pose.direction.dot(direction)>.999f)
    }
    @Test fun reacquiringHandStartsAtActualLocationWithoutSweepingFromOldPose() {
        val tracker=HandAttachmentTracker();tracker.update(origin,forward,.016f);tracker.reset()
        val next=origin+V3(.3f,.2f,0f);val pose=tracker.update(next,V3(1f,0f,0f),.016f)
        assertEquals(next,pose.origin);assertEquals(V3(1f,0f,0f),pose.direction)
    }
    @Test fun filterConvergesAtDifferentFrameRates() {
        fun result(dt:Float):AttachmentPose{val tracker=HandAttachmentTracker();tracker.update(origin,forward,dt);var pose=tracker.update(origin,forward,dt)
            repeat((.2f/dt).toInt()){pose=tracker.update(origin+V3(.1f,0f,0f),V3(.2f,0f,-1f),dt)};return pose}
        val fast=result(1f/90f);val slow=result(1f/30f)
        assertTrue((fast.origin-slow.origin).length()<.004f);assertTrue(fast.direction.dot(slow.direction)>.999f)
    }
}
