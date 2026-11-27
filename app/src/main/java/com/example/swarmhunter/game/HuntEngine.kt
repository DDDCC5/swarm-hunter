package com.example.swarmhunter.game

import kotlin.math.*

data class V3(val x:Float,val y:Float,val z:Float) {
    operator fun plus(v:V3)=V3(x+v.x,y+v.y,z+v.z)
    operator fun minus(v:V3)=V3(x-v.x,y-v.y,z-v.z)
    operator fun times(v:Float)=V3(x*v,y*v,z*v)
    fun cross(v:V3)=V3(y*v.z-z*v.y,z*v.x-x*v.z,x*v.y-y*v.x)
    fun dot(v:V3)=x*v.x+y*v.y+z*v.z
    fun length()=sqrt(dot(this))
    fun normalized():V3 {val n=length();return if(n<.00001f)V3(0f,0f,-1f)else this*(1/n)}
}
/** SDK aim is authoritative; custom poses without aim use a virtual shoulder-to-hand ray. */
fun handRayDirection(head:V3,hand:V3,forward:V3,right:V3,side:Int,official:V3?):V3 {
    if(official!=null&&listOf(official.x,official.y,official.z).all{it.isFinite()}&&official.length()>.001f)return official.normalized()
    val shoulder=head+right*(if(side==0)-.15f else .15f)+V3(0f,-.18f,0f)
    val delta=hand-shoulder
    return if(delta.length()<.1f||delta.normalized().dot(forward)<.1f)forward.normalized()else delta.normalized()
}
fun handRayRotation(direction:V3,upHint:V3):Q4 {
    val d=direction.normalized();var up=upHint-d*upHint.dot(d)
    if(up.length()<.001f){val hint=if(abs(d.z)<.9f)V3(0f,0f,-1f)else V3(1f,0f,0f);up=hint-d*hint.dot(d)}
    return surfaceRotation(up,d)
}
fun raySphere(origin:V3,direction:V3,center:V3,radius:Float):Float? {
    val oc=origin-center;val d=direction.normalized();val b=oc.dot(d);val c=oc.dot(oc)-radius*radius;val h=b*b-c
    if(h<0)return null
    val first=-b-sqrt(h);val last=-b+sqrt(h)
    return when {first>=0->first;last>=0->last;else->null}
}
enum class HuntPhase { LOGO, SAFETY, GESTURE, TOOLS, PICKUP, PLACEMENT, PRACTICE, RUNNING, RESULTS }
fun toolVisible(phase:HuntPhase,equipped:Boolean,tracked:Boolean)=when(phase){
    HuntPhase.PICKUP->!equipped||tracked
    HuntPhase.PLACEMENT,HuntPhase.PRACTICE,HuntPhase.RUNNING->equipped&&tracked
    else->false
}
data class Threat(val id:Int,val species:Int,var position:V3,var visible:Boolean=false,var alive:Boolean=true,var age:Float=0f,var windup:Float=0f,var health:Int=1,var slowRemaining:Float=0f)

/** Pure game state; runtime owns world transforms, sound, physics and UI publication. */
class HuntEngine {
    var phase=HuntPhase.LOGO
    var paused=false
    var lives=3;private set
    var kills=0;private set
    var survival=0f;private set
    var invulnerable=0f;private set
    val threats=mutableListOf<Threat>()
    private var serial=0
    fun returnToGesture(memory:GestureMemory):Boolean {
        if(phase!=HuntPhase.TOOLS)return false
        memory.reset();phase=HuntPhase.GESTURE;return true
    }
    private val scoredShots=mutableSetOf<Int>()
    private val issuedShots=linkedSetOf<Int>()
    private var shotSerial=0
    fun start(){lives=3;kills=0;survival=0f;invulnerable=0f;paused=false;threats.clear();shotsFired=0;shotsHit=0;trapKills=0;practiceHits=0;scoredShots.clear();issuedShots.clear();phase=HuntPhase.RUNNING}
    fun tick(dt:Float){if(phase !in listOf(HuntPhase.RUNNING,HuntPhase.PRACTICE)||paused)return
        val step=dt.coerceAtLeast(0f);if(phase==HuntPhase.RUNNING)survival+=step
        invulnerable=(invulnerable-step).coerceAtLeast(0f);threats.filter{it.alive}.forEach{it.age+=step;it.slowRemaining=(it.slowRemaining-step).coerceAtLeast(0f)}}
    fun spawn(species:Int,position:V3,health:Int=1)=Threat(++serial,species,position,health=health).also{threats.add(it)}
    fun reveal(id:Int){threats.find{it.id==id}?.visible=true}
    fun kill(id:Int):Boolean {val t=threats.find{it.id==id}?:return false;if(!t.alive||!t.visible||paused||phase !in listOf(HuntPhase.RUNNING,HuntPhase.PRACTICE))return false;t.alive=false;if(phase==HuntPhase.PRACTICE)practiceHits++ else kills++;return true}
    fun attack(id:Int):Boolean{val t=threats.find{it.id==id}?:return false;return t.alive&&t.visible&&hurt()}
    fun hurt():Boolean{if(phase!=HuntPhase.RUNNING||paused||invulnerable>0)return false;lives--;invulnerable=2f;if(lives==0)phase=HuntPhase.RESULTS;return true}
    fun nearestEligibleHit(hits:List<Pair<Int,Float>>):Int? = hits.filter { (id,d) -> d>=0 && threats.any{it.id==id&&it.alive&&it.visible} }.minByOrNull{it.second}?.first
    fun pruneDead(id:Int){threats.removeAll{it.id==id&&!it.alive}}
    var shotsFired=0;private set
    var shotsHit=0;private set
    var trapKills=0;private set
    var practiceHits=0;private set
    fun startPractice(){start();phase=HuntPhase.PRACTICE}
    fun recordShot():Int {if(phase!=HuntPhase.RUNNING||paused)return 0;shotsFired++;val id=++shotSerial;issuedShots.add(id)
        // Native projectiles live at most1.4s; this window exceeds their possible dual-hand count.
        while(issuedShots.size>256){val old=issuedShots.first();issuedShots.remove(old);scoredShots.remove(old)}
        return id}
    fun creditShot(id:Int):Boolean{if(phase!=HuntPhase.RUNNING||id !in issuedShots||!scoredShots.add(id))return false;shotsHit++;return true}
    fun accuracy()=if(shotsFired==0)0f else shotsHit.toFloat()/shotsFired
    fun combatScore()=kills*10f+accuracy()*30f+survival.coerceAtMost(120f)*.25f
    fun retireHidden(id:Int):Boolean {val t=threats.firstOrNull{it.id==id&&it.alive&&!it.visible}?:return false;return threats.remove(t)}
    fun hit(id:Int,damage:Int,slowSeconds:Float=0f,trap:Boolean=false):HitOutcome {
        val t=threats.firstOrNull{it.id==id&&it.visible&&it.alive}?:return HitOutcome.NONE
        if(paused||damage<=0||phase !in listOf(HuntPhase.RUNNING,HuntPhase.PRACTICE))return HitOutcome.NONE
        t.health=(t.health-damage).coerceAtLeast(0);t.slowRemaining=max(t.slowRemaining,slowSeconds.coerceAtLeast(0f))
        if(t.health>0)return HitOutcome.DAMAGED
        if(!kill(id))return HitOutcome.NONE
        if(trap&&phase==HuntPhase.RUNNING)trapKills++
        return HitOutcome.KILLED
    }
    fun grade():String=when {kills>=25&&combatScore()>=300f->"S";kills>=12&&combatScore()>=180f->"A";kills>=4&&combatScore()>=70f->"B";else->"C"}

}

/** Rotation/size-invariant finger bend features are supplied by the official hand adapter. */
class GestureMemory {
    var registered=false;private set
    var progress=0f;private set
    private var candidate:List<Float>?=null
    private val templates=arrayOfNulls<List<Float>>(2)
    var adjusting=false;private set
    private var smoothedLeft:List<Float>?=null
    private var smoothedRight:List<Float>?=null
    private var captureMean:List<Float>?=null
    private var invalidTime=0f
    private val matching=booleanArrayOf(false,false)
    private val shotCooldown=floatArrayOf(0f,0f)
    private fun distance(a:List<Float>,b:List<Float>):Float=if(a.size!=b.size||a.isEmpty())Float.MAX_VALUE else sqrt(a.indices.sumOf{((a[it]-b[it])*(a[it]-b[it])).toDouble()}.toFloat()/a.size)
    private fun pauseCapture(dt:Float) {
        adjusting=true
        invalidTime+=dt
        // A transient disagreement pauses valid hold time; only sustained disagreement restarts it.
        if(invalidTime>=1f){progress=0f;candidate=null;captureMean=null;smoothedLeft=null;smoothedRight=null;invalidTime=0f}
    }
    fun register(left:List<Float>?,right:List<Float>?,dt:Float) {
        if(registered)return
        val step=dt.coerceIn(0f,.1f)
        if(left==null||right==null||left.isEmpty()||left.size!=right.size||!left.all(Float::isFinite)||!right.all(Float::isFinite)){pauseCapture(step);return}
        val alpha=1f-exp(-step/.12f)
        fun smooth(previous:List<Float>?,current:List<Float>)=if(previous==null||previous.size!=current.size)current.toList()else current.indices.map{previous[it]+alpha*(current[it]-previous[it])}
        smoothedLeft=smooth(smoothedLeft,left);smoothedRight=smooth(smoothedRight,right)
        val mean=left.indices.map{(smoothedLeft!![it]+smoothedRight!![it])/2f}
        if(distance(left,right)>.28f||(candidate!=null&&distance(candidate!!,mean)>.24f)){pauseCapture(step);return}
        adjusting=false;invalidTime=0f
        if(candidate==null)candidate=mean.toList()
        val next=progress+step
        captureMean=if(captureMean==null)mean.toList()else mean.indices.map{(captureMean!![it]*progress+mean[it]*step)/next.coerceAtLeast(.00001f)}
        progress=next
        if(progress>=2.5f){
            // Save each hand independently, and only accept a template the live pose can fire.
            if(distance(smoothedLeft!!,left)>.16f||distance(smoothedRight!!,right)>.16f){progress=2.4f;adjusting=true;return}
            templates[0]=smoothedLeft!!.toList();templates[1]=smoothedRight!!.toList();registered=true;progress=2.5f
        }
    }
    /** A matching held pose emits bounded shots. Missing joints always stop this hand. */
    fun fire(hand:Int,features:List<Float>?,dt:Float,interval:Float=.20f):Boolean {
        if(hand !in 0..1)return false
        val target=templates[hand]?:return false
        val step=if(dt.isFinite())dt.coerceIn(0f,.25f)else 0f
        shotCooldown[hand]=(shotCooldown[hand]-step).coerceAtLeast(0f)
        if(features==null||features.size!=target.size||!features.all(Float::isFinite)){
            matching[hand]=false;return false
        }
        val d=distance(target,features)
        // Current-frame match is mandatory for every shot, including a held pose.
        // Cooldown continues while open: reposing never bypasses weapon cadence.
        matching[hand]=d<=.18f
        if(!matching[hand]||shotCooldown[hand]>.00001f)return false
        shotCooldown[hand]=if(interval.isFinite())interval.coerceAtLeast(.05f)else .20f
        return true
    }
    fun matchDistance(hand:Int,features:List<Float>?):Float?=if(hand in 0..1&&features!=null)templates[hand]?.let{distance(it,features)}else null
    fun isMatching(hand:Int)=hand in 0..1&&matching[hand]
    fun suspendInput(){matching.fill(false);shotCooldown.fill(0f)}
    fun reset(){registered=false;progress=0f;candidate=null;templates.fill(null);captureMean=null;smoothedLeft=null;smoothedRight=null;invalidTime=0f;adjusting=false;suspendInput()}

}

enum class AttackEvent { PREPARE, STRIKE, RECOVERED }
/** Contact at the authored frame; recovery survives a target stepping out of range. */
class AttackCycle {
    private var cooldown=0f
    val readyToPrepare get()=!active&&cooldown<=0f
    var active=false;private set
    private var elapsed=0f
    private var contacted=false
    fun tick(dt:Float,near:Boolean):AttackEvent? {
        val step=dt.coerceAtLeast(0f)
        cooldown=(cooldown-step).coerceAtLeast(0f)
        if(active){
            elapsed+=step
            if(!contacted&&elapsed>=.56f){contacted=true;cooldown=1.5f;if(near)return AttackEvent.STRIKE}
            if(elapsed>=1.2f){active=false;return AttackEvent.RECOVERED}
            return null
        }
        if(near&&cooldown<=0f){active=true;elapsed=0f;contacted=false;return AttackEvent.PREPARE}
        return null
    }
}

/** Combat accepts either shooting hand; both missing hands have a one-second recovery window. */
class CombatTrackingGate {
    private var missing=1f
    var ready=false;private set
    fun update(dt:Float,left:Boolean,right:Boolean):Boolean {
        missing=if(left||right)0f else missing+dt.coerceAtLeast(0f)
        ready=missing<1f
        return ready
    }
    fun reset(){missing=1f;ready=false}
}


/** Unit quaternion math shared by authored-model and portal-plane alignment. */
data class Q4(val x:Float,val y:Float,val z:Float,val w:Float) {
    operator fun times(q:Q4)=Q4(w*q.x+x*q.w+y*q.z-z*q.y,w*q.y-x*q.z+y*q.w+z*q.x,w*q.z+x*q.y-y*q.x+z*q.w,w*q.w-x*q.x-y*q.y-z*q.z)
    fun rotate(v:V3):V3 {val axis=V3(x,y,z);val t=axis.cross(v)*2f;return v+t*w+axis.cross(t)}
    companion object {
        fun axisAngle(axis:V3,angle:Float):Q4 {val n=axis.normalized();val h=angle/2f;val s=sin(h);return Q4(n.x*s,n.y*s,n.z*s,cos(h))}
        fun between(a:V3,b:V3):Q4 {val u=a.normalized();val v=b.normalized();val d=u.dot(v).coerceIn(-1f,1f)
            if(d<-.9999f){val axis=u.cross(if(abs(u.x)<.8f)V3(1f,0f,0f)else V3(0f,0f,1f));return axisAngle(axis,Math.PI.toFloat())}
            val c=u.cross(v);val w=1f+d;val norm=sqrt(c.dot(c)+w*w).coerceAtLeast(.00001f);return Q4(c.x/norm,c.y/norm,c.z/norm,w/norm)
        }
    }
}
fun surfaceRotation(normal:V3,forward:V3):Q4 {
    val n=normal.normalized();var f=forward-n*forward.dot(n)
    if(f.length()<.00001f){val fallback=if(abs(n.z)<.9f)V3(0f,0f,-1f)else V3(0f,-1f,0f);f=fallback-n*fallback.dot(n)}
    f=f.normalized();val up=Q4.between(V3(0f,1f,0f),n);val current=up.rotate(V3(0f,0f,-1f));val angle=atan2(n.dot(current.cross(f)),current.dot(f))
    return Q4.axisAngle(n,angle)*up
}
fun emergencePosition(progress:Float,depth:Float):V3 {val t=progress.coerceIn(0f,1f);val s=t*t*(3f-2f*t);return V3(0f,.20f*t,-depth*(1f-s))}
fun beamLengthScale(hitDistance:Float)=((hitDistance-.17f).coerceAtLeast(0f))/.9f

/** Limit the visible trail to travelled distance so it never starts 0.75m behind the muzzle. */
fun projectileTrailLength(travelled:Float)=travelled.coerceIn(.06f,.75f)

/** Equipment depends only on hand proximity; finger shapes never enter this decision. */
fun toolPickupInReach(tool:V3,palm:V3?,wrist:V3?)=listOfNotNull(palm,wrist).any{point->
    point.x.isFinite()&&point.y.isFinite()&&point.z.isFinite()&&(point-tool).length()<=.18f
}

fun wallReachedFloor(bodyY:Float,floorY:Float)=bodyY<=floorY+.025f
fun pointOnSurface(point:V3,origin:V3,normal:V3,clearance:Float=.012f):V3 {
    val n=normal.normalized();return point+n*(clearance-(point-origin).dot(n))
}
fun surfaceNormalMatchesType(normal:V3,wall:Boolean):Boolean {
    if(!listOf(normal.x,normal.y,normal.z).all{it.isFinite()}||normal.length()<.001f)return false
    val y=normal.normalized().y
    return if(wall)abs(y)<.25f else y>.85f
}

/** Per-hand minimum shot interval, without a release gesture or catch-up burst. */
fun toolFireInterval(tool:Int)=when(tool){0->.20f;1->.15f;2->.32f;3->.55f;else->.20f}
