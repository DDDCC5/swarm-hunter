package com.example.swarmhunter.game

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ObjectAudioComponent
import com.pico.spatial.core.ecs.resource.AudioResource

/** Debug startup regression against the real SDK, including emitter destruction. No audible output. */
internal fun verifyAudioReuse(root:Entity,samples:List<AudioResource>) {
    for(resource in samples) {
        val name=resource.getName()
        repeat(2) {
            val emitter=Entity()
            root.addChild(emitter)
            emitter.components.set(ObjectAudioComponent(volume=0f,reverbVolume=0f))
            val player=emitter.playAudio(resource)
            player.stop()
            player.close()
            emitter.destroy()
            check(resource.valid) { "Cached audio lost ownership after emitter destruction: $name" }
        }
    }
}
