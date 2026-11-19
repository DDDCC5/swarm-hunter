package com.example.swarmhunter.game

import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.math.*
import kotlin.math.PI

/** Debug-only portal hierarchy check for floor and wall orientation; no user input or visuals. */
internal fun verifySurfaceAlignment(root:Entity):String {
    val cases=listOf("floor" to V3(0f,1f,0f),"wall_x" to V3(1f,0f,0f),"wall_z" to V3(0f,0f,1f))
    for((name,normal)in cases){
        val forward=if(name=="floor")V3(0f,0f,-1f)else V3(0f,-1f,0f)
        val model=surfaceRotation(normal,forward);val frameRotation=model*Q4.axisAngle(V3(1f,0f,0f),-PI.toFloat()/2f)
        val frame=Entity();root.addChild(frame)
        frame.components[TransformComponent::class.java]!!.quaternion=Quat(frameRotation.x,frameRotation.y,frameRotation.z,frameRotation.w)
        try {
            val world=Entity();frame.addChild(world);world.components.set(PortalWorldComponent())
            val body=Entity();world.addChild(body);body.components.set(PortalCrossableComponent())
            body.components[TransformComponent::class.java]!!.quaternion=Quat(Vector3(1f,0f,0f),PI.toFloat()/2f)
            val up=root.convertRotationFrom(Quat.identity(),body).rotateVector(Vector3(0f,1f,0f))
            check(V3(up.x,up.y,up.z).dot(normal)>.999f){"Portal body orientation disagrees with $name normal"}
            body.components.remove(PortalCrossableComponent::class.java);check(body.setParent(root))
            try {
                body.components[TransformComponent::class.java]!!.quaternion=Quat(model.x,model.y,model.z,model.w)
                val emergedUp=root.convertRotationFrom(Quat.identity(),body).rotateVector(Vector3(0f,1f,0f))
                check(V3(emergedUp.x,emergedUp.y,emergedUp.z).dot(normal)>.999f){"Emerged body orientation disagrees with $name normal"}
            }finally{if(body.valid)body.destroy()}
        }finally{if(frame.valid)frame.destroy()}
    }
    return "floor, wall_x, wall_z before/after portal emergence"
}
