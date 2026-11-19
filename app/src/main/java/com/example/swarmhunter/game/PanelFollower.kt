package com.example.swarmhunter.game

data class PanelPose(val position:V3,val rotation:Q4)

/** Head position and yaw follow every frame; pitch and roll never tilt the UI. */
class UprightPanelFollower {
    var heading=V3(0f,0f,-1f);private set
    fun update(head:V3,viewForward:V3,distance:Float,heightOffset:Float):PanelPose {
        val horizontal=V3(viewForward.x,0f,viewForward.z)
        // Close to vertical, yaw is undefined: keep the previous stable heading.
        if(horizontal.length()>.05f)heading=horizontal.normalized()
        return PanelPose(head+heading*distance+V3(0f,heightOffset,0f),surfaceRotation(V3(0f,1f,0f),heading))
    }
}
