package com.example.swarmhunter.game
import kotlin.math.*
const val CREATURE_MODEL_SCALE=.9f
const val EMERGENCE_SECONDS=1.2f
const val EMERGENCE_TRAVEL=.12f
const val DEATH_SECONDS=1.7f
/** One presentation edge owns model visibility and arrival/crawl audio, in every mode. */
class ArrivalCue {
 private var started=false
 fun start(modelVisible:Boolean):Boolean {if(started||!modelVisible)return false;started=true;return true}
}
fun creatureSpan(species:Int)=listOf(.12f,.22f,.18f,.36f,.28f,.15f,.14f,.13f)[species]*CREATURE_MODEL_SCALE
fun nestScale(species:Int)=((creatureSpan(species)+.035f)/.36f).coerceAtLeast(.42f)
/** Feet remain on the scanned plane while the creature crawls out of the visible opening. */
fun visibleEmergencePosition(progress:Float)=V3(0f,EMERGENCE_TRAVEL*progress.coerceIn(0f,1f),0f)
fun spawnNormalMatches(normal:V3,kind:RoomSurfaceKind):Boolean {
 if(!listOf(normal.x,normal.y,normal.z).all(Float::isFinite)||normal.length()<.001f)return false
 val y=normal.normalized().y
 return when(kind){RoomSurfaceKind.WALL->abs(y)<.25f;RoomSurfaceKind.CEILING->y<-.85f;else->y>.85f}
}
fun deathDissolveDue(age:Float)=age>=1f
fun deathOpacity(age:Float)=if(age<=1f)1f else ((DEATH_SECONDS-age)/.7f).coerceIn(0f,1f)

/** Per-species rhythm signatures applied to distinct approved recordings, not voice/audio globals. */
fun creatureSoundRate(species:Int)=listOf(1.25f,.92f,1.08f,.72f,1.48f,1.62f,.80f,1f)[species]
fun arrivalDuration(species:Int)=1.25f/creatureSoundRate(species)
