package com.example.swarmhunter.game
/** In-place locomotion must match the displayed model's scaled stride in metres. */
fun creaturePlaybackSpeed(metresPerSecond:Float,strideMetres:Float,modelScale:Float=1.6f):Float =
    if(strideMetres>0f&&modelScale>0f)(metresPerSecond/(strideMetres*modelScale)).coerceAtLeast(0f)else 0f
