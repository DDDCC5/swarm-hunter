package com.example.swarmhunter.game

import com.pico.spatial.core.ecs.*
import kotlinx.coroutines.delay

/** Debug startup verifies the actual SDK importer, not just the GLB JSON. */
suspend fun verifyAnimationImport(root:Entity,models:Map<String,Entity>):String {
    var count=0
    for((id,template)in models){
        val clone=checkNotNull(template.clone(Entity.CloneOptions(recursive=true,shouldShareMaterialInstance=true)))
        root.addChild(clone);clone.components.set(OpacityControllerComponent(0f));clone.enabled=true
        delay(40)
        try{
            val roots=clone.findSkeletonRootEntity(includeDisabled=true)
            val names=roots.flatMap{it.getAnimationResources().map{a->a.getName()}}.toSet()
            val expected=if(id=="trap_pulse")setOf("deploy","idle","arm","trigger","recover")else setOf("idle","crawl","attack","hit","fall","turn_left","turn_right")
            check(names.containsAll(expected)){"$id missing imported clips: ${expected-names}"}
            val name=if(id=="trap_pulse")"deploy"else"attack"
            for(skeleton in roots){
                val animation=skeleton.getAnimationResources().firstOrNull{it.getName()==name}?:continue
                val resource=animation.repeat(0).also{it.toGlobal()}
                val player=skeleton.playAnimation(resource)
                try{
                    for(frame in 0..25){if(player.getDuration()>0f)break;delay(20)}
                    check(kotlin.math.abs(player.getDuration()-(if(id=="trap_pulse")1f else 1.2f))<.04f){"$id imported duration=${player.getDuration()}"};player.setSpeed(.75f);check(kotlin.math.abs(player.getSpeed()-.75f)<.01f)}
                finally{player.stop();player.close();resource.close()}
            }
            count++
        }finally{if(clone.valid)clone.destroy()}
    }
    return "$count models: complete clips, duration and playback speed"
}
