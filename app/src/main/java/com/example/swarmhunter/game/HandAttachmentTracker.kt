package com.example.swarmhunter.game

import kotlin.math.*

data class AttachmentPose(val origin:V3,val direction:V3)

/** Filters the rigid hand attachment independently of finger pose and SDK gesture aim. */
class HandAttachmentTracker {
    private var pose:AttachmentPose?=null
    fun reset(){pose=null}
    fun update(origin:V3,direction:V3,dt:Float):AttachmentPose {
        val target=direction.normalized();val previous=pose
        if(previous==null)return AttachmentPose(origin,target).also{pose=it}
        val step=dt.coerceIn(0f,.05f)
        val delta=origin-previous.origin;val distance=delta.length()
        // A small stationary dead zone removes pose-estimator jitter; intentional movement
        // accelerates the filter instead of inheriting a long, fixed smoothing delay.
        val positionTau=.055f-(.035f*(distance/.05f).coerceIn(0f,1f))
        val position=if(distance<=.0015f)previous.origin else previous.origin+delta*(1f-exp(-step/positionTau))
        val cosine=previous.direction.dot(target).coerceIn(-1f,1f)
        val angle=acos(cosine)
        val directionTau=.075f-.05f*(angle/.25f).coerceIn(0f,1f)
        val filtered=when {
            angle<=.0105f->previous.direction
            cosine<-.5f->target
            else->(previous.direction*(exp(-step/directionTau))+target*(1f-exp(-step/directionTau))).normalized()
        }
        return AttachmentPose(position,filtered).also{pose=it}
    }
}
