package com.example.swarmhunter.game
/** Returns null for missing evidence, never a stale closed hand. Ratio is tip distance / full palm. */
class PlacementPinch {
 private var closed:Boolean?=null
 fun update(official:Float?,tipRatio:Float?):Boolean? {
  val strength=official?.takeIf{it.isFinite()&&it in 0f..1f}
  val ratio=tipRatio?.takeIf{it.isFinite()&&it>=0f}
  closed=when {
   ratio!=null&&ratio<=.27f->true
   ratio!=null&&ratio>=.55f->false
   strength!=null->when{strength>=.70f->true;strength<=.35f->false;else->closed}
   ratio!=null->closed
   else->null
  };return closed
 }
 fun reset(){closed=null}
}
enum class PlacementHint { TRACKING, SURFACE, EDGE, PANEL, READY, PLACED, LIMIT }
