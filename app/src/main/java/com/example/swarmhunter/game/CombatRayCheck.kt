package com.example.swarmhunter.game

import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.ecs.resource.*
import com.pico.spatial.core.ecs.simulation.*
import com.pico.spatial.core.math.*
import kotlinx.coroutines.delay

/** Offscreen native query check; no game enemies, user input, damage or score. */
internal suspend fun verifyCombatRayCast(scene:Scene,root:Entity):String {
    val frame=Entity();root.addChild(frame)
    frame.components[TransformComponent::class.java]!!.position=Vector3(0f,10f,-2f)
    val portalWorld=Entity();frame.addChild(portalWorld);portalWorld.components.set(PortalWorldComponent())
    val target=Entity();root.addChild(target)
    val transform=target.components[TransformComponent::class.java]!!
    transform.position=Vector3(0f,10f,-2f)
    target.components.set(CollisionComponent(listOf(ShapeResource.createSphere(.15f)),PhysicsMaterialResource()))
    target.components.set(RigidBodyComponent(MassProperties(),RigidBodyMode.KINEMATIC))
    fun hit():Float?=scene.rayCast(Vector3(0f,10f,0f),Vector3(0f,0f,-1f),3f,CollisionCastHitMode.ALL,CollisionGroup(),root).results.firstOrNull{it.entity==target}?.distance
    var projectile:Entity?=null
    try {
        delay(180)
        val regular=hit();check(regular!=null){"Ordinary collider not found by exact-entity raycast"}
        target.enabled=false;target.setParent(portalWorld);transform.position=Vector3.ZERO;target.components.set(PortalCrossableComponent())
        delay(100);target.enabled=true;delay(120)
        val position=root.convertPositionFrom(Vector3.ZERO,target);val rotation=root.convertRotationFrom(Quat.identity(),target)
        target.components.remove(PortalCrossableComponent::class.java);check(target.setParent(root))
        transform.position=position;transform.quaternion=rotation
        delay(180)
        val emerged=hit();check(emerged!=null){"Reparented portal collider not found by raycast"}
        val shot=Entity();projectile=shot;root.addChild(shot)
        val shotTransform=shot.components[TransformComponent::class.java]!!
        shotTransform.position=Vector3(0f,10f,0f)
        shot.components.set(CollisionComponent(listOf(ShapeResource.createSphere(.025f)),PhysicsMaterialResource()))
        shot.components.set(RigidBodyComponent(MassProperties(),RigidBodyMode.KINEMATIC))
        delay(100)
        var last=shotTransform.position
        shot.components.set(PhysicsVelocityComponent(Vector3(0f,0f,-10f),Vector3.ZERO))
        var swept=false
        repeat(25){if(!swept){delay(20);val current=shotTransform.position
            val delta=V3(current.x-last.x,current.y-last.y,current.z-last.z);val distance=delta.length()
            if(distance>.00001f){val direction=delta.normalized()
                swept=scene.rayCast(last,Vector3(direction.x,direction.y,direction.z),distance+.04f,CollisionCastHitMode.ALL,CollisionGroup(),root).results.any{it.entity==target}
            };last=current
        }}
        check(swept){"10m/s projectile swept ray missed reparented target"}
        return "regular=$regular emerged=$emerged swept10mps=$swept"
    }finally{projectile?.let{if(it.valid)it.destroy()};if(target.valid)target.destroy();if(frame.valid)frame.destroy()}
}
