package com.example.swarmhunter.game

/** A result belongs only to the newest requested anchor snapshot. */
class RoomSnapshotGate {
 private var generation=0
 fun request():Int=++generation
 fun invalidate(){generation++}
 fun accepts(token:Int)=token==generation
}
class RoomTrackingGrace {
 private var missing=0f
 var available=false;private set
 var expired=false;private set
 fun update(running:Boolean,dt:Float){available=running;missing=if(running)0f else missing+dt.coerceAtLeast(0f);expired=missing>=2f}
}
fun canRicochet(tool:Int,kind:RoomSurfaceKind)=tool==2&&kind==RoomSurfaceKind.WALL
