package com.example.swarmhunter
import com.example.swarmhunter.game.*
import org.junit.Assert.*
import org.junit.Test
class GazePlacementTest {
 private val room=RoomSurfaceRegistry().also{it.replace(listOf(
  RoomSurface("floor",RoomSurfaceKind.FLOOR,V3(0f,0f,0f),Q4(0f,0f,0f,1f),listOf(V3(-3f,0f,-3f),V3(3f,0f,-3f),V3(3f,0f,3f),V3(-3f,0f,3f)),listOf(0,2,1,0,3,2)),
  RoomSurface("wall",RoomSurfaceKind.WALL,V3(0f,0f,-2f),Q4(0f,0f,0f,1f),listOf(V3(-3f,0f,0f),V3(3f,0f,0f),V3(3f,3f,0f),V3(-3f,3f,0f)),listOf(0,1,2,0,2,3)))) }
 @Test fun lookingDownSelectsFloorAndKeepsTrapHorizontal(){val h=gazePlacementHit(room,V3(0f,1.6f,0f),V3(0f,-1f,-.3f))!!;assertEquals("floor",h.surface.id);val q=trapPlacementRotation(h,V3(0f,0f,-1f));assertTrue(q.rotate(V3(0f,1f,0f)).dot(V3(0f,1f,0f))>.999f)}
 @Test fun lookingAheadSelectsWallAndTurnsTrapVertically(){val h=gazePlacementHit(room,V3(0f,1.6f,0f),V3(0f,0f,-1f))!!;assertEquals("wall",h.surface.id);val q=trapPlacementRotation(h,V3(0f,0f,-1f));assertTrue(q.rotate(V3(0f,1f,0f)).dot(V3(0f,0f,1f))>.999f)}
 @Test fun gazeIsBoundedAndDoesNotInventPlanesInAir(){assertNull(gazePlacementHit(room,V3(0f,1.6f,0f),V3(0f,1f,0f)));room.replace(emptyList());assertNull(gazePlacementHit(room,V3(0f,1.6f,0f),V3(0f,-1f,0f)))}
 @Test fun pinchConfirmsCurrentGazeSurfaceAndHoldingCannotPlaceAgain(){val t=TrapController();val floor=gazePlacementHit(room,V3(0f,1.6f,0f),V3(0f,-1f,-.3f))!!;t.pinch(0,false,floor,room);val wall=gazePlacementHit(room,V3(0f,1.6f,0f),V3(0f,0f,-1f))!!;assertEquals("wall",t.pinch(0,true,wall,room)!!.anchorId);assertNull(t.pinch(0,true,floor,room))}
}
