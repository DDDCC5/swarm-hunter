package com.example.swarmhunter.game

import kotlin.math.*

enum class HuntDifficulty { RELAXED, STANDARD, CHALLENGE }
data class EncounterSettings(val capacity:Int,val spawnSeconds:Float,val speed:Float,val unseenSeconds:Float,val minForwardDot:Float,val attackSpacing:Float,val resting:Boolean)
fun encounterSettings(level:HuntDifficulty,seconds:Float):EncounterSettings {
 val time=seconds.coerceAtLeast(0f);val ramp=(time/150f).coerceIn(0f,1f)
 val resting=time%45f>=37f
 return when(level){
  HuntDifficulty.RELAXED->EncounterSettings(2+(ramp*3).toInt(),6f-2f*ramp,.09f+.05f*ramp,20f,if(time<20f).5f else if(time<45f)0f else -1f,2f,resting)
  HuntDifficulty.STANDARD->EncounterSettings(3+(ramp*4).toInt(),5.4f-2.6f*ramp,.12f+.08f*ramp,17f,if(time<12f).4f else if(time<25f)0f else -1f,1.65f,resting)
  HuntDifficulty.CHALLENGE->EncounterSettings(4+(ramp*5).toInt(),4.4f-2.4f*ramp,.15f+.10f*ramp,14f,if(time<10f).2f else -1f,1.35f,resting)
 }
}
enum class HiddenAction { NONE, REMIND, RECYCLE }
fun hiddenAction(age:Float,visible:Boolean,reminders:Int,timeout:Float)=when{
 visible->HiddenAction.NONE
 age>=timeout->HiddenAction.RECYCLE
 reminders<2&&age>=6f+reminders*5f->HiddenAction.REMIND
 else->HiddenAction.NONE
}
class AttackBudget {
 private var next=Float.NEGATIVE_INFINITY
 fun acquire(clock:Float,spacing:Float):Boolean{if(clock<next)return false;next=clock+spacing.coerceAtLeast(1.2f);return true}
 fun reset(){next=Float.NEGATIVE_INFINITY}
}
enum class CreatureRole { SWIFT, STANDARD, ARMORED }
fun speciesRole(species:Int)=when(species){0,4,5->CreatureRole.SWIFT;3,6->CreatureRole.ARMORED;else->CreatureRole.STANDARD}
fun creatureHealth(species:Int,level:HuntDifficulty):Int {
 val hp=when(speciesRole(species)){CreatureRole.SWIFT->1;CreatureRole.STANDARD->2;CreatureRole.ARMORED->4}
 return (hp-if(level==HuntDifficulty.RELAXED)1 else 0).coerceAtLeast(1)
}
fun creatureSpeedFactor(species:Int)=when(speciesRole(species)){CreatureRole.SWIFT->1.25f;CreatureRole.STANDARD->1f;CreatureRole.ARMORED->.7f}
data class WeaponRule(val damage:Int,val range:Float,val radius:Float=0f,val slowSeconds:Float=0f)
fun weaponRule(tool:Int)=when(tool){0->WeaponRule(1,6f,slowSeconds=3f);1->WeaponRule(2,7f);2->WeaponRule(2,9.8f);else->WeaponRule(2,3f,1.1f)}
/** Projectile blasts respect all scanned physical surfaces; trap fields intentionally use wall-only occlusion. */
fun areaTargets(origin:V3,radius:Float,candidates:List<Pair<Int,V3>>,room:RoomSurfaceRegistry,wallsOnly:Boolean=false,contactId:Int?=null):List<Int> =
 candidates.filter{(id,p)->val d=p-origin;val length=d.length();length<=radius&&(id==contactId||length<.0005f||room.cast(origin,d,length,wallsOnly)==null)}.map{it.first}
enum class HitOutcome { NONE, DAMAGED, KILLED }

class HitFeedback {
 var at=-10f;private set
 var killed=false;private set
 fun record(outcome:HitOutcome,clock:Float){if(outcome==HitOutcome.NONE)return;at=clock;killed=outcome==HitOutcome.KILLED}
 fun reset(){at=-10f;killed=false}
 fun active(clock:Float)=clock>=at&&clock-at<.22f
}
