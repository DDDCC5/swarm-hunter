package com.example.swarmhunter.game
/** HMD view-centre gaze in game-root metres. No eye-tracking permission or hand ray needed. */
fun gazePlacementHit(room:RoomSurfaceRegistry,head:V3,look:V3):SurfaceHit? {
 val hit=room.cast(head,look,3.5f)?:return null
 return hit.takeIf{it.distance>=.12f&&surfaceNormalMatchesType(it.normal,it.surface.kind==RoomSurfaceKind.WALL)}
}
/** Model +Y is its mounting-face normal, hence wall mounting is automatically vertical. */
fun trapPlacementRotation(hit:SurfaceHit,heading:V3):Q4 = surfaceRotation(hit.normal,if(hit.surface.kind==RoomSurfaceKind.WALL)V3(0f,-1f,0f)else heading)
