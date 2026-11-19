package com.example.swarmhunter.game

import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.ecs.resource.*
import com.pico.spatial.core.ecs.simulation.*
import com.pico.spatial.core.math.Vector3
import kotlinx.coroutines.delay

/** Debug-only, offscreen physical regression. Does not send input or create game targets. */
internal suspend fun verifyProjectileMotion(root:Entity):Float {
    val entity=Entity();root.addChild(entity)
    val transform=entity.components[TransformComponent::class.java]!!
    transform.position=Vector3(0f,10f,0f)
    entity.components.set(CollisionComponent(listOf(ShapeResource.createSphere(.025f)),PhysicsMaterialResource()))
    entity.components.set(RigidBodyComponent(MassProperties(),RigidBodyMode.KINEMATIC))
    try {
        delay(150)
        val start=transform.position.z
        entity.components.set(PhysicsVelocityComponent(Vector3(0f,0f,-2f),Vector3.ZERO))
        delay(300)
        val moved=start-transform.position.z
        check(moved>.2f){"Projectile did not advance: displacement=$moved expected about 0.6m"}
        return moved
    }finally{if(entity.valid)entity.destroy()}
}
