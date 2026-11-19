package com.example.swarmhunter

import com.example.swarmhunter.game.*
import org.junit.Assert.*
import org.junit.Test

class PanelFollowerTest {
    private fun near(a:V3,b:V3){assertEquals(a.x,b.x,.0001f);assertEquals(a.y,b.y,.0001f);assertEquals(a.z,b.z,.0001f)}
    @Test fun panelFollowsPositionAndTurnOnEveryUpdate() {
        val follower=UprightPanelFollower()
        val first=follower.update(V3(0f,1.6f,0f),V3(0f,0f,-1f),1.8f,-.1f)
        near(V3(0f,1.5f,-1.8f),first.position)
        val second=follower.update(V3(2f,1.7f,3f),V3(1f,0f,0f),1.8f,-.1f)
        near(V3(3.8f,1.6f,3f),second.position)
        near(V3(1f,0f,0f),second.rotation.rotate(V3(0f,0f,-1f)))
    }
    @Test fun lookingUpOrDownKeepsPanelUprightAndAtHorizontalDistance() {
        val follower=UprightPanelFollower()
        val pose=follower.update(V3(0f,1.6f,0f),V3(.3f,.8f,-.4f),1.8f,-.1f)
        near(V3(0f,1f,0f),pose.rotation.rotate(V3(0f,1f,0f)))
        near(V3(1.08f,1.5f,-1.44f),pose.position)
    }
    @Test fun lookingStraightUpRetainsLastHeadingAndProducesFinitePose() {
        val follower=UprightPanelFollower()
        follower.update(V3(0f,1.6f,0f),V3(1f,0f,0f),1.8f,-.1f)
        val vertical=follower.update(V3(0f,1.6f,0f),V3(0f,1f,0f),1.8f,-.1f)
        near(V3(1.8f,1.5f,0f),vertical.position)
        near(V3(0f,1f,0f),vertical.rotation.rotate(V3(0f,1f,0f)))
    }
}
