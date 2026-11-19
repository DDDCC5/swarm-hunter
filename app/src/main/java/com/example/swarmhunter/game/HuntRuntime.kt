package com.example.swarmhunter.game

import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.example.swarmhunter.platform.LaunchActivity
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.ecs.audio.*
import com.pico.spatial.core.ecs.resource.*
import com.pico.spatial.core.ecs.simulation.*
import com.pico.spatial.core.math.*
import com.pico.spatial.sense.plane.*
import com.pico.spatial.sense.base.SemanticLabelType
import com.pico.spatial.sense.base.TrackingState
import com.pico.spatial.tracking.hand.*
import com.pico.spatial.tracking.hmd.*
import com.pico.spatial.tracking.controller.*
import kotlinx.coroutines.*
import kotlin.math.*
import kotlin.random.Random

private fun Vector3.v()=V3(x,y,z)
private fun V3.vec()=Vector3(x,y,z)
private fun Q4.quat()=Quat(x,y,z,w)
private fun Entity.safeDestroy(){if(valid)destroy()}
private val Entity.t get()=components[TransformComponent::class.java]!!
object SessionConsent {var accepted=false}
data class HuntSnapshot(val phase:HuntPhase=HuntPhase.LOGO,val lives:Int=3,val kills:Int=0,val seconds:Int=0,val grade:String="C",val progress:Float=0f,val selected:Int=0,val equippedLeft:Boolean=false,val equippedRight:Boolean=false,val controller:Boolean=false,val tracking:Boolean=false,val loading:Boolean=true,val planes:Int=0,val error:String?=null,val hurt:Boolean=false,val handPermission:Boolean=false,val registrationAdjusting:Boolean=false,val armedHands:Int=0)

class HuntRuntime(private val context:Context,private val scope:CoroutineScope) {
    val engine=HuntEngine();val gesture=GestureMemory();private val combatTracking=CombatTrackingGate()
    var state by mutableStateOf(HuntSnapshot());private set
    val hands=HandTrackingProvider();val hmd=HMDTrackingProvider();private val controllers=ControllerTrackingProvider()
    val root=Entity();private var content:SpatialViewContent?=null;private var panel:Entity?=null
    private val activeAnimations=mutableListOf<Pair<Entity,AnimationResource>>()
    private val templates=mutableMapOf<String,Entity>();private val audio=mutableMapOf<String,AudioResource>();private val voices=mutableMapOf<String,AudioResource>()
    private var laserCoreTemplate:Entity?=null
    private var silkCoreTemplate:Entity?=null
    private var lastAimTrace=0f
    private var collisionCheckStarted=false
    private val handAttachments=Array(2){HandAttachmentTracker()}
    private val stableRays=arrayOfNulls<HandRay>(2)
    private val eventWriter=java.util.concurrent.Executors.newSingleThreadExecutor()
    private fun trace(message:String){Log.i("SwarmHunter",message);val line="${java.lang.System.currentTimeMillis()} $message\n";if(!eventWriter.isShutdown)eventWriter.execute{runCatching{val f=java.io.File(context.cacheDir,"hunter-gameplay.log");if(f.length()>1048576)f.writeText("");f.appendText(line)}}}
    private val tools=arrayOfNulls<Entity>(2);private val toolMounts=Array(2){Entity()};private val equipped=booleanArrayOf(false,false)
    private val threats=mutableMapOf<Int,EnemyVisual>();private val projectiles=mutableListOf<Projectile>();private val temporary=mutableListOf<Pair<Entity,Float>>()
    private val planes=mutableMapOf<java.util.UUID,PlaneAnchor>()
    private var scene:Scene?=null;private var now=0f;private var publish=0f;private var nextSpawn=3f;private var logoTime=0f;private var selected=0;private var loading=true;private var error:String?=null
    private var head=V3(0f,1.5f,0f);private var forward=V3(0f,0f,-1f);private var headRotation=Quat.identity();private var lastPhase=HuntPhase.LOGO
    private var lastHandPresence=-1
    private var handData:HandTrackingData?=null;private var lastHandAt=0L;@Volatile private var controllerAt=0L
    private var providersStarted=false;private var handStarted=false;private var planeStarted=false;private var cancelPlane:(()->Unit)?=null;var cancelFrame:(()->Unit)?=null
    private var swipeAnchor:Float?=null;private var swipeCooldown=0f;private var voiceEntity=Entity();private var voiceController:AudioPlayerController?=null
    var appActive=true
    private val panelFollower=UprightPanelFollower()
    private var wasPaused=false
    private var latestLeft:HandPose?=null;private var latestRight:HandPose?=null
    private val species=listOf("sp01_jumping","sp02_wolf","sp03_orb","sp04_tarantula","sp05_longlegs","in01_roach","in02_beetle","in03_weevil")
    private val weapons=listOf("web","laser","energy","pulse")
    private val listener=ControllerTrackingProvider.ControllerActionListener { data ->
        fun active(a:ControllerAction)=a.triggerTouched||a.triggerPressed||a.triggerValue>.08f||a.gripPressed||a.gripValue>.08f||a.thumbstickTouched||a.thumbstickPressed||a.aButtonTouched||a.bButtonTouched||a.xButtonTouched||a.yButtonTouched
        if(active(data.left)||active(data.right))controllerAt=SystemClock.elapsedRealtime()
    }
    private data class EnemyVisual(
        val body:Entity,val frame:Entity,val rift:Entity,val portal:Entity,val portalWorld:Entity,
        val crawl:Entity,val crawlPlayer:AudioPlayerController?,var wall:Boolean,var surfaceRotation:Quat,var surfaceOrigin:V3,
        val floorY:Float,val depth:Float,val holeScale:Float,val attack:AttackCycle=AttackCycle(),
        var emerged:Boolean=false,var moving:Boolean=false,var closedAt:Float=0f,var dying:Float=0f,var cue:Float=0f
    )
    private data class Projectile(val entity:Entity,val visual:Entity,val core:Entity?,val tool:Int,val direction:V3,val launch:V3,var last:V3,var age:Float=0f)
    fun receiveHands(data:HandTrackingData){handData=data;lastHandAt=SystemClock.elapsedRealtime();val presence=(if(data.left!=null)1 else 0)+(if(data.right!=null)2 else 0);if(presence!=lastHandPresence){lastHandPresence=presence;trace("HAND_FRAME left=${data.left!=null} right=${data.right!=null}")}}
    fun beginProviders(){if(providersStarted)return;providersStarted=true;hmd.start();controllers.addControllerActionListener(listener);controllers.start()}
    suspend fun initialize(c:SpatialViewContent,p:Entity?) {
        content=c;panel=p;c.addEntity(root);p?.let{root.addChild(it);it.t.position=Vector3(0f,1.4f,-1.8f)}
        toolMounts.forEach{root.addChild(it)};root.addChild(voiceEntity);voiceEntity.components.set(ChannelAudioComponent())
        try {
            val modelFiles=species+listOf("rift_wall","rift_floor")+listOf("fx_web","fx_laser","fx_energy","fx_pulse","fx_web_impact","fx_laser_impact","fx_energy_impact","fx_pulse_impact","fx_monster_dissolve","fx_rift_dust")+weapons.flatMap{w->val i=weapons.indexOf(w)+1;listOf("wp0${i}_${w}_left","wp0${i}_${w}_right")}
            for(id in modelFiles){templates[id]=Entity.loadSuspend("asset://models/$id.glb").also{root.addChild(it);it.enabled=false}}
            // The pulse core uses the same proven native rendering path as the aim guide.
            // Prebuild it so a firing gesture never waits for skinned GLB animation evaluation.
            laserCoreTemplate=Entity().also{root.addChild(it);val material=UnlitMaterial.create();material.setBaseColor(Color4(1f,.91f,.72f,1f));material.setDepthTest(false);it.components.set(ModelComponent(MeshResource.createBox(Vector3(.010f,.010f,1f)),material));it.enabled=false}
            silkCoreTemplate=Entity().also{root.addChild(it);val material=UnlitMaterial.create();material.setBaseColor(Color4(.94f,.86f,.69f,1f));material.setDepthTest(false);it.components.set(ModelComponent(MeshResource.createBox(Vector3(.007f,.007f,1f)),material));it.enabled=false}
            // Decode all gameplay audio before any gesture-driven shot; no I/O on the firing path.
            for(file in context.assets.list("audio/sfx").orEmpty().filter{it.endsWith(".wav")}){
                val id=file.removeSuffix(".wav");val cfg=AudioResourceConfig("",randomStart=false,loopEnable=id.contains("_crawl_"))
                audio[id]=AudioResource.load(id,"asset://audio/sfx/$file",LoadType.FROM_ASSETS,cfg).also{it.toGlobal()}
            }
            val locale=if(context.resources.configuration.locales[0].language=="zh")"zh-CN" else "en"
            for(file in context.assets.list("audio/voice/$locale").orEmpty().filter{it.endsWith(".wav")})voices[file.removeSuffix(".wav")]=AudioResource.load("voice_${file.removeSuffix(".wav")}","asset://audio/voice/$locale/$file",LoadType.FROM_ASSETS).also{it.toGlobal()}
            if(context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE!=0){
                verifyAudioReuse(root,listOfNotNull(audio["rift_wall_crack_1"],audio["web_fire_1"],voices["hands_visible"]))
                trace("AUDIO_REUSE_CHECK passed: repeated playback and emitter destruction")
                trace("SURFACE_ALIGNMENT_CHECK passed: ${verifySurfaceAlignment(root)}")
                trace("PROJECTILE_MOTION_CHECK passed: moved=${verifyProjectileMotion(root)}")
            }
            loading=false;beginProviders();trace("ASSETS_READY package=com.example.swarmhunter")
        }catch(e:Exception){if(e is CancellationException)throw e;error="Asset loading: ${e.message}";Log.e("SwarmHunter","Asset initialization failed",e)}
        emit()
    }
    fun consent(){if(controllerUsed())return;SessionConsent.accepted=true;LaunchActivity.current.get()?.requestTrackingPermissions();engine.phase=HuntPhase.GESTURE;beginProviders();speak("hands_visible");emit()}
    fun retryPermissions(){LaunchActivity.current.get()?.requestTrackingPermissions()}
    fun chooseTool(index:Int){if(controllerUsed())return;selected=(index+4)%4;soundAt("${weapons[selected]}_switch_1",head);emit()}
    fun reregisterGesture(){
        if(controllerUsed()||!engine.returnToGesture(gesture))return
        equipped.fill(false);tools.forEach{it?.enabled=false}
        handAttachments.forEach{it.reset()};stableRays.fill(null);swipeAnchor=null;swipeCooldown=0f
        trace("GESTURE_REGISTRATION_RESET source=tools");speak("hands_visible");emit()
    }
    fun confirmTools(){if(controllerUsed()||engine.phase!=HuntPhase.TOOLS||!gesture.registered)return;equipped.fill(false);handAttachments.forEach{it.reset()};stableRays.fill(null);engine.phase=HuntPhase.PICKUP;placeTools();trace("PICKUP_READY tool=$selected leftVisible=${tools[0]?.enabled} rightVisible=${tools[1]?.enabled}");speak("tool_pickup");emit()}
    fun start(){if(controllerUsed()||!equipped.all{it})return;clearBattle();engine.start();gesture.suspendInput();combatTracking.reset();nextSpawn=2.5f;trace("GAME_START eligiblePlanes=${eligiblePlanes().size}");speak("mission_start");emit()}
    fun retry(){if(controllerUsed())return;clearBattle();engine.phase=HuntPhase.TOOLS;equipped.fill(false);tools.forEach{it?.enabled=false};speak("tool_select");emit()}
    private fun controllerUsed()=SystemClock.elapsedRealtime()-controllerAt<1500L
    private fun newModel(id:String,parent:Entity=root):Entity {val e=checkNotNull(templates[id]?.clone(Entity.CloneOptions(recursive=true,shouldShareMaterialInstance=true))){"Model clone failed: $id"};parent.addChild(e);e.enabled=true;return e}
    private fun animate(e:Entity,name:String,loop:Boolean=false,freezeAtEnd:Boolean=false){for(skeleton in e.findSkeletonRootEntity()){skeleton.stopAllAnimations();val previous=activeAnimations.filter{it.first==skeleton};previous.forEach{it.second.close()};activeAnimations.removeAll(previous.toSet());val a=skeleton.getAnimationResources().firstOrNull{it.getName()==name}?:continue;val resource=a.repeat(if(loop)100000 else 0).also{it.toGlobal()};val player=skeleton.playAnimation(resource);if(freezeAtEnd){player.setTime((player.getDuration()-.001f).coerceAtLeast(0f));player.pause()};activeAnimations.add(skeleton to resource)}}
    private fun collider(e:Entity,radius:Float){e.components.set(CollisionComponent(listOf(ShapeResource.createSphere(radius)),PhysicsMaterialResource()));e.components.set(RigidBodyComponent(MassProperties(),RigidBodyMode.KINEMATIC))}
    private fun emitter(position:V3,parent:Entity=root):Entity=Entity().also{parent.addChild(it);it.t.position=position.vec();it.components.set(ObjectAudioComponent(volume=.9f,distanceAttenuationMode=DistanceAttenuationMode.FIXED,reverbVolume=.1f))}
    private fun soundAt(id:String,position:V3,life:Float=3f):Entity? {val a=audio[id]?:return null;return emitter(position).also{it.playAudio(a);temporary.add(it to(now+life))}}
    private fun speak(id:String){val a=voices[id]?:return;voiceController?.stop();voiceController?.close();voiceController=voiceEntity.playAudio(a)}
    private fun effect(id:String,position:V3,seconds:Float=.8f){val e=newModel(id);e.t.position=position.vec();animate(e,if(id=="fx_monster_dissolve")"dissolve"else"burst");temporary.add(e to(now+seconds))}
    private fun placeTools(){for(i in 0..1){tools[i]?.safeDestroy();val id="wp0${selected+1}_${weapons[selected]}_${if(i==0)"left"else"right"}";tools[i]=newModel(id,toolMounts[i]);animate(tools[i]!!,"equip")};updateWaitingTools()}
    private fun updateWaitingTools(){
        val flatForward=panelFollower.heading;val right=flatForward.cross(V3(0f,1f,0f))
        val rotation=surfaceRotation(V3(0f,1f,0f),flatForward).quat()
        for(i in 0..1)if(!equipped[i]){toolMounts[i].t.position=(head+flatForward*.65f+right*(if(i==0)-.24f else .24f)+V3(0f,-.25f,0f)).vec();toolMounts[i].t.quaternion=rotation}
    }
    private fun features(p:HandPose?):List<Float>? {
        p?:return null;val j=p.handJoints.map{it.position.v()};if(j.size<26)return null
        val result=ArrayList<Float>(15)
        for(indices in listOf(listOf(2,3,4,5),listOf(6,7,8,9,10),listOf(11,12,13,14,15),listOf(16,17,18,19,20),listOf(21,22,23,24,25))){
            val points=indices.map{j[it]};for(k in 0..1){val a=(points[k+1]-points[k]).normalized();val b=(points[k+2]-points[k+1]).normalized();result.add(acos(a.dot(b).coerceIn(-1f,1f)))}
            val palm=(j[11]-j[1]).length().coerceAtLeast(.03f);result.add((points.last()-j[0]).length()/palm)
        };return result.takeIf{it.all(Float::isFinite)}
    }
    fun frame(dtInput:Float,world:Scene){if(loading||error!=null)return;scene=world;
        if(!collisionCheckStarted&&context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE!=0){collisionCheckStarted=true;scope.launch{try{trace("COMBAT_RAY_CHECK passed: ${verifyCombatRayCast(world,root)}")}catch(e:Exception){if(e is CancellationException)throw e;trace("COMBAT_RAY_CHECK failed: ${e.message}")}}}
val dt=dtInput.coerceIn(0f,.05f);now+=dt
        val hp=hmd.latestData.hmdPose;head=root.convertPositionFrom(hp.position,null).v();headRotation=root.convertRotationFrom(hp.rotation,null);forward=headRotation.rotateVector(Vector3(0f,0f,-1f)).v().normalized()
        val fresh=SystemClock.elapsedRealtime()-lastHandAt<150;latestLeft=if(fresh)handData?.left else null;latestRight=if(fresh)handData?.right else null
        val bothTracked=latestLeft!=null&&latestRight!=null;val tracked=if(engine.phase==HuntPhase.RUNNING)combatTracking.update(dt,latestLeft!=null,latestRight!=null)else bothTracked;val blocked=controllerUsed();engine.paused=!appActive||!tracked||blocked||eligiblePlanes().isEmpty()
        if(engine.phase==HuntPhase.LOGO){logoTime+=dt;if(logoTime>=1.2f){engine.phase=if(SessionConsent.accepted)HuntPhase.GESTURE else HuntPhase.SAFETY;if(SessionConsent.accepted){LaunchActivity.current.get()?.requestTrackingPermissions();speak("hands_visible")}}}
        if(!handStarted&&SessionConsent.accepted&&ContextCompat.checkSelfPermission(context,"com.picovr.permission.HAND_TRACKING")==PackageManager.PERMISSION_GRANTED){val result=hands.start();handStarted=true;trace("HAND_PROVIDER start=$result support=${hands.supportState} state=${hands.state}")}
        startPlanesIfPermitted()
        if(engine.phase==HuntPhase.RUNNING&&engine.paused!=wasPaused){
            trace("COMBAT_PAUSED value=${engine.paused} hands=$tracked controller=$blocked surfaces=${eligiblePlanes().size} foreground=$appActive")
            gesture.suspendInput()
            for(p in projectiles){p.entity.components.set(PhysicsVelocityComponent(if(engine.paused)Vector3.ZERO else(p.direction*if(p.tool==0)10f else 7f).vec(),Vector3.ZERO));p.last=p.entity.t.position.v()}
            threats.values.forEach{if(engine.paused)it.crawlPlayer?.pause() else it.crawlPlayer?.play()}
        }
        wasPaused=engine.paused
        val running=engine.phase==HuntPhase.RUNNING
        val panelPose=panelFollower.update(head,forward,if(running)1.5f else 1.8f,if(running).4f else -.1f)
        panel?.t?.apply{position=panelPose.position.vec();quaternion=panelPose.rotation.quat()}
        if(engine.phase==HuntPhase.PICKUP)updateWaitingTools()
        if(!blocked&&appActive){when(engine.phase){
            HuntPhase.GESTURE->{gesture.register(features(latestLeft),features(latestRight),dt);if(gesture.registered){engine.phase=HuntPhase.TOOLS;swipeAnchor=null;swipeCooldown=0f;speak("gesture_recorded")}}
            HuntPhase.TOOLS->{swipeCooldown-=dt;latestRight?.let{val x=it[HandJoint.Index.PALM].position.x;val a=swipeAnchor;if(a==null)swipeAnchor=x else if(abs(x-a)>.16f&&swipeCooldown<=0){chooseTool(selected+if(x>a)1 else -1);swipeAnchor=x;swipeCooldown=.65f}}}
            HuntPhase.PICKUP->{for(i in 0..1){val p=if(i==0)latestLeft else latestRight;p?:continue
                val wrist=root.convertPositionFrom(p[HandJoint.Index.WRIST].position,null).v()
                val palm=root.convertPositionFrom(p[HandJoint.Index.PALM].position,null).v()
                if(!equipped[i]&&toolPickupInReach(toolMounts[i].t.position.v(),palm,wrist)){
                    equipped[i]=true;trace("TOOL_EQUIPPED hand=$i trigger=proximity tool=$selected")
                    soundAt("${weapons[selected]}_equip_1",wrist);if(equipped.all{it})speak("tool_equipped")
                }
            }}
            else->Unit
        }}
        for(i in 0..1){val pose=if(i==0)latestLeft else latestRight
            stableRays[i]=if(pose!=null&&equipped[i])updateHandRay(i,pose,dt)else null
            if(pose==null||!equipped[i])handAttachments[i].reset()
            tools[i]?.enabled=toolVisible(engine.phase,equipped[i],pose!=null)
            stableRays[i]?.let{ray->toolMounts[i].t.position=ray.origin.vec();toolMounts[i].t.quaternion=ray.rotation
                if(engine.phase==HuntPhase.RUNNING&&now-lastAimTrace>2f){trace("AIM hand=$i source=${ray.source} forwardDot=${ray.direction.dot(forward)}");if(i==1||latestRight==null)lastAimTrace=now}
            }
        }
        if(engine.phase==HuntPhase.RUNNING&&!engine.paused){engine.tick(dt);nextSpawn-=dt;if(nextSpawn<=0&&threats.size<7&&planes.isNotEmpty()){spawn();nextSpawn=Random.nextDouble(2.8,5.0).toFloat()}
            for(i in 0..1){val p=if(i==0)latestLeft else latestRight;if(gesture.fire(i,features(p),dt)&&p!=null)shoot(i)}
            updateBattle(dt,world)
        }
        if(engine.phase==HuntPhase.RESULTS&&lastPhase!=HuntPhase.RESULTS){trace("GAME_OVER kills=${engine.kills} survival=${engine.survival}");speak("mission_end");threats.values.forEach{it.crawlPlayer?.stop()}}
        if(lastPhase==HuntPhase.RUNNING&&engine.phase!=HuntPhase.RUNNING)projectiles.forEach{it.entity.components.set(PhysicsVelocityComponent(Vector3.ZERO,Vector3.ZERO))}
        lastPhase=engine.phase
        val finishedAnimations=activeAnimations.filter{!it.first.valid};finishedAnimations.forEach{it.second.close()};activeAnimations.removeAll(finishedAnimations.toSet())
        val expired=temporary.filter{it.second<=now};expired.forEach{it.first.safeDestroy()};temporary.removeAll(expired.toSet());publish+=dt;if(publish>=.05f){publish=0f;emit()}
    }
    private fun startPlanesIfPermitted(){if(planeStarted||!SessionConsent.accepted||ContextCompat.checkSelfPermission(context,"com.picovr.permission.SPATIAL_DATA")!=PackageManager.PERMISSION_GRANTED)return;planeStarted=true
        val subscription=PlaneTrackingManager.subscribeAnchorUpdate { _ ->scope.launch{if(PlaneTrackingManager.state!=TrackingState.RUNNING)return@launch;val all=PlaneTrackingManager.loadAllAnchors();planes.clear();all.forEach{planes[it.anchorUUID]=it}}};cancelPlane={subscription.cancel()};PlaneTrackingManager.start();scope.launch{while(PlaneTrackingManager.state!=TrackingState.RUNNING){delay(100)};PlaneTrackingManager.loadAllAnchors().forEach{planes[it.anchorUUID]=it}}
    }
    private fun eligiblePlanes()=planes.values.filter{it.boundingBoxSize.x>.55f&&it.boundingBoxSize.y>.55f&&((it.semantics==SemanticLabelType.FLOOR&&it.planeOrientation==PlaneOrientation.HORIZONTAL_UPWARD)||(it.semantics==SemanticLabelType.WALL&&it.planeOrientation==PlaneOrientation.VERTICAL))&&it.indices.size>=3}
    private fun samplePlane(plane:PlaneAnchor):V3? {
        val vertices=plane.vertices;val indices=plane.indices;val rotation=plane.transform.rotation.toQuat()
        repeat(60){val triangle=Random.nextInt(indices.size/3)*3;val ids=indices.subList(triangle,triangle+3);if(ids.any{it !in vertices.indices})return@repeat
            val a=vertices[ids[0]];val b=vertices[ids[1]];val c=vertices[ids[2]];var u=Random.nextFloat();var v=Random.nextFloat();if(u+v>1f){u=1-u;v=1-v}
            val local=a+(b-a)*u+(c-a)*v;val world=plane.transform.position+rotation.rotateVector(local);val p=root.convertPositionFrom(world,null).v();val horizontal=V3(p.x-head.x,0f,p.z-head.z).length()
            if(horizontal in 1.5f..3.5f&&(p-head).length()<4.5f&&(plane.planeOrientation!=PlaneOrientation.VERTICAL||p.y>=floorHeightNear(p)+.55f))return p
        };return null
    }
    private fun floorHeightNear(position:V3):Float {
        val floors=eligiblePlanes().filter{it.semantics==SemanticLabelType.FLOOR&&it.vertices.isNotEmpty()}.map{plane->
            val center=plane.vertices.fold(Vector3.ZERO){sum,v->sum+v}*(1f/plane.vertices.size)
            root.convertPositionFrom(plane.transform.position+plane.transform.rotation.toQuat().rotateVector(center),null).v()
        }
        val below=floors.filter{it.y<=position.y+.05f}
        return (below.ifEmpty{floors}).minByOrNull{(it.x-position.x).pow(2)+(it.z-position.z).pow(2)}?.y?:0f
    }
    private fun measuredNormal(plane:PlaneAnchor,position:V3):V3 {
        val vertices=plane.vertices
        for(i in 0 until plane.indices.size-2 step 3){val a=plane.indices[i];val b=plane.indices[i+1];val c=plane.indices[i+2];if(a !in vertices.indices||b !in vertices.indices||c !in vertices.indices)continue
            val cross=(vertices[b]-vertices[a]).v().cross((vertices[c]-vertices[a]).v());if(cross.length()<.00001f)continue
            var n=root.convertRotationFrom(plane.transform.rotation.toQuat(),null).rotateVector(cross.normalized().vec()).v().normalized()
            if(if(plane.semantics==SemanticLabelType.FLOOR)n.y<0f else n.dot(head-position)<0f)n=n*-1f
            return n
        }
        error("Detected plane contains no valid triangle")
    }
    private fun spawn(){
        var chosen:Triple<PlaneAnchor,V3,V3>?=null
        for(plane in eligiblePlanes().shuffled()){val pos=samplePlane(plane)?:continue;val n=measuredNormal(plane,pos);if(!surfaceNormalMatchesType(n,plane.planeOrientation==PlaneOrientation.VERTICAL))continue;chosen=Triple(plane,pos,n);break}
        val(plane,pos,normal)=chosen?:run{trace("SPAWN_SKIPPED eligible=${eligiblePlanes().size} reason=no_safe_sample");return};val wall=plane.planeOrientation==PlaneOrientation.VERTICAL
        val tangent=if(wall)V3(0f,-1f,0f)else V3(head.x-pos.x,0f,head.z-pos.z)
        val modelQ=surfaceRotation(normal,tangent);val portalQ=modelQ*Q4.axisAngle(V3(1f,0f,0f),-PI.toFloat()/2f)
        val kind=Random.nextInt(species.size);val t=engine.spawn(kind,pos);trace("SPAWN id=${t.id} species=${species[kind]} wall=$wall normalY=${normal.y} viewDot=${(pos-head).normalized().dot(forward)}")
        val span=listOf(.12f,.22f,.18f,.36f,.28f,.15f,.14f,.13f)[kind]*1.6f
        val holeScale=max(1f,(span+.05f)/.36f);val depth=max(.25f,span*.65f)
        val frame=Entity();root.addChild(frame);frame.t.position=pos.vec();frame.t.quaternion=portalQ.quat()
        val world=Entity();frame.addChild(world);world.components.set(PortalWorldComponent())
        val portal=Entity();frame.addChild(portal);portal.t.position=Vector3(0f,0f,.002f);portal.t.scaleVector=Vector3(.025f,.025f,.025f)
        portal.components.set(ModelComponent(MeshResource.createPlane(.36f,.36f,.18f),PortalMaterial()))
        portal.components.set(PortalComponent(targetEntity=world,enable=true,doubleSide=false,allowClipping=true,allowEntityCrossing=true))
        val rift=newModel(if(wall)"rift_wall"else"rift_floor",frame);rift.t.quaternion=Quat(Vector3(1f,0f,0f),PI.toFloat()/2f);rift.t.scaleVector=Vector3(holeScale,holeScale,holeScale);animate(rift,"open")
        val body=Entity();world.addChild(body);body.components.set(PortalCrossableComponent());body.t.position=emergencePosition(0f,depth).vec();body.t.quaternion=Quat(Vector3(1f,0f,0f),PI.toFloat()/2f)
        val mesh=newModel(species[kind],body);mesh.t.scaleVector=Vector3(1.6f,1.6f,1.6f);body.enabled=false;collider(body,max(.08f,span*.4f))
        val crawl=emitter(pos);val player=audio["${species[kind]}_crawl_1"]?.let{crawl.playAudio(it)}
        soundAt("rift_${if(wall)"wall"else"floor"}_crack_${Random.nextInt(1,4)}",pos)
        val floorY=if(wall)floorHeightNear(pos)else pos.y
        threats[t.id]=EnemyVisual(body,frame,rift,portal,world,crawl,player,wall,modelQ.quat(),pos,floorY,depth,holeScale)
    }
    private fun updateBattle(dt:Float,world:Scene){for(t in engine.threats.toList()){val v=threats[t.id]?:continue
        if(!t.alive){v.dying+=dt;if(v.dying>1.2f){v.body.safeDestroy();v.frame.safeDestroy();v.crawl.safeDestroy();threats.remove(t.id);engine.pruneDead(t.id)};continue}
        if(v.portal.valid){val open=(if(v.moving)(1f-(now-v.closedAt)/1.5f)else t.age/1.5f).coerceIn(.025f,1f)*v.holeScale;v.portal.t.scaleVector=Vector3(open,open,open)}
        val toward=(t.position-head).normalized();if(!t.visible&&t.age>.7f&&toward.dot(forward)>.67f){engine.reveal(t.id);trace("REVEAL id=${t.id}");v.body.enabled=true;animate(v.body,"crawl",true);soundAt("${species[t.species]}_emerge_1",t.position);v.cue=t.age}
        if(t.visible&&!v.emerged){val progress=((t.age-v.cue)/2.5f).coerceIn(0f,1f);v.body.t.position=emergencePosition(progress,v.depth).vec();t.position=root.convertPositionFrom(Vector3.ZERO,v.body).v();v.crawl.t.position=t.position.vec()
            if(progress>=1f){val p=root.convertPositionFrom(Vector3.ZERO,v.body);v.body.components.remove(PortalCrossableComponent::class.java);check(v.body.setParent(root)){"Unable to detach emerged creature from portal"};t.position=pointOnSurface(p.v(),v.surfaceOrigin,v.surfaceRotation.rotateVector(Vector3(0f,1f,0f)).v());v.body.t.position=t.position.vec();v.body.t.quaternion=v.surfaceRotation;v.emerged=true;trace("EMERGED id=${t.id}")}
        }
        if(t.visible&&t.age-v.cue>3f){if(!v.moving){v.moving=true;v.closedAt=now;animate(v.body,"crawl",true);animate(v.rift,"close");temporary.add(v.frame to(now+1.5f))}
            if(v.wall&&wallReachedFloor(t.position.y,v.floorY)){
                v.wall=false;v.surfaceRotation=Quat.identity();v.surfaceOrigin=V3(t.position.x,v.floorY,t.position.z)
                t.position=pointOnSurface(t.position,v.surfaceOrigin,V3(0f,1f,0f));v.body.t.position=t.position.vec()
                trace("WALL_TO_FLOOR id=${t.id} clearance=${t.position.y-v.floorY}")
            }
            val normal=v.surfaceRotation.rotateVector(Vector3(0f,1f,0f)).v()
            val desired=if(v.wall)V3(0f,-1f,0f)else head-t.position;val delta=desired-normal*desired.dot(normal)
            val distance=V3(head.x-t.position.x,0f,head.z-t.position.z).length();val near=!v.wall&&distance<=.65f
            if(!near){val movement=delta.normalized();t.position=pointOnSurface(t.position+movement*(dt*(.12f+engine.survival*.0004f)),v.surfaceOrigin,normal);v.body.t.position=t.position.vec();v.body.t.quaternion=surfaceRotation(normal,movement).quat();v.crawl.t.position=t.position.vec()}
            when(v.attack.tick(dt,near)){
                AttackEvent.PREPARE->{animate(v.body,"attack");soundAt("${species[t.species]}_windup_1",t.position);soundAt("${species[t.species]}_attack_1",t.position)}
                AttackEvent.STRIKE->{soundAt("${species[t.species]}_landing_1",t.position);if(engine.attack(t.id)){speak(if(engine.lives==1)"last_life"else"player_hurt");soundAt("player_hurt",head)};animate(v.body,"crawl",true)}
                null->Unit
            }

        }
    }
        for(p in projectiles.toList()){p.age+=dt;val current=p.entity.t.position.v()
            if(p.tool==0)updateSilkTrail(p,(current-p.launch).length())
            if(p.age-dt<.1f&&p.age>=.1f)trace("PROJECTILE_ADVANCE tool=${p.tool} distance=${(current-p.launch).length()} coreVisible=${p.core?.enabled}")
            val travel=current-p.last;val len=travel.length();if(len>.00001f){val hits=world.rayCast(p.last.vec(),travel.normalized().vec(),len+.04f,CollisionCastHitMode.ALL,CollisionGroup(),root).results;val hit=engine.nearestEligibleHit(hits.mapNotNull{h->threats.entries.firstOrNull{it.value.body==h.entity}?.let{it.key to h.distance}});if(hit!=null){eliminate(hit,p.tool);p.entity.safeDestroy();projectiles.remove(p);continue}};p.last=current;if(p.age>1.4f){p.entity.safeDestroy();projectiles.remove(p)}}
    }
    private data class HandRay(val origin:V3,val direction:V3,val rotation:Quat,val source:String)
    private fun updateHandRay(hand:Int,pose:HandPose,dt:Float):HandRay {
        val wrist=pose[HandJoint.Index.WRIST];val origin=root.convertPositionFrom(wrist.position,null).v()
        // Aim supplied for built-in hand gestures can jump/disappear as fingers curl.
        // Use wrist position throughout the custom firing gesture; never switch aim sources.
        val right=headRotation.rotateVector(Vector3(1f,0f,0f)).v()
        val direction=handRayDirection(head,origin,forward,right,hand,null)
        val stable=handAttachments[hand].update(origin,direction,dt)
        val up=headRotation.rotateVector(Vector3(0f,1f,0f)).v()
        return HandRay(stable.origin,stable.direction,handRayRotation(stable.direction,up).quat(),"stable_wrist_position")
    }
    private fun shoot(hand:Int){val inputAge=SystemClock.elapsedRealtime()-lastHandAt;if(inputAge>150)return;val ray=stableRays[hand]?:return;val pos=ray.origin;val rot=ray.rotation;val direction=ray.direction
        val visibleTargets=engine.threats.filter{it.alive&&it.visible}
        val closestMiss=visibleTargets.map{val offset=it.position-pos;val along=offset.dot(direction);if(along>0f)(offset-direction*along).length()else Float.POSITIVE_INFINITY}.minOrNull()
        trace("SHOT_TARGETS visible=${visibleTargets.size} closestCenterMissMeters=$closestMiss")
        animate(tools[hand]!!,"fire");soundAt("${weapons[selected]}_fire_1",pos)
        if(selected==1){
            val hits=scene?.rayCast(pos.vec(),direction.vec(),7f,CollisionCastHitMode.ALL,CollisionGroup(),root)?.results.orEmpty()
            val eligible=hits.mapNotNull{h->threats.entries.firstOrNull{it.value.body==h.entity}?.let{it.key to h.distance}}
            val hit=engine.nearestEligibleHit(eligible);val distance=hit?.let{id->eligible.filter{it.first==id}.minOf{it.second}}?:7f
            val e=newModel("fx_laser");e.t.position=(pos+direction*.17f).vec();e.t.quaternion=rot;e.t.scaleVector=Vector3(2f,2f,beamLengthScale(distance));animate(e,"flight",freezeAtEnd=true);temporary.add(e to(now+.32f))
            val length=.9f*beamLengthScale(distance)
            val core=checkNotNull(laserCoreTemplate?.clone(Entity.CloneOptions(recursive=true,shouldShareMaterialInstance=true)))
            root.addChild(core);core.t.position=(pos+direction*(.17f+length*.5f)).vec();core.t.quaternion=rot;core.t.scaleVector=Vector3(1f,1f,length);core.enabled=true;temporary.add(core to(now+.32f))
            trace("LASER_VISUAL coreVisible=${core.enabled} length=$length lifetimeMs=320 sdkHits=${hits.size} eligibleHits=${eligible.size}")
            hit?.let{eliminate(it,selected)}
        }else{
            val e=Entity();root.addChild(e);e.t.position=(pos+direction*.17f).vec();e.t.quaternion=rot
            val visual=newModel("fx_${weapons[selected]}",e);visual.t.position=Vector3(0f,0f,when(selected){0->.75f;2->.2f;else->.3f});animate(visual,"flight",freezeAtEnd=true)
            val core=if(selected==0)checkNotNull(silkCoreTemplate?.clone(Entity.CloneOptions(recursive=true,shouldShareMaterialInstance=true))).also{e.addChild(it);it.enabled=true}else null
            val projectile=Projectile(e,visual,core,selected,direction,pos+direction*.17f,pos+direction*.17f)
            if(selected==0)updateSilkTrail(projectile,0f)
            collider(e,.025f);e.components.set(PhysicsVelocityComponent((direction*if(selected==0)10f else 7f).vec(),Vector3.ZERO));projectiles.add(projectile)
            trace("PROJECTILE_VISUAL tool=$selected modelVisible=${visual.enabled} coreVisible=${core?.enabled}")
        }
        trace("SHOT hand=$hand tool=$selected inputAgeMs=$inputAge aim=${ray.source} forwardDot=${direction.dot(forward)}")
    }
    private fun updateSilkTrail(projectile:Projectile,travelled:Float) {
        val length=projectileTrailLength(travelled)
        projectile.visual.t.position=Vector3(0f,0f,length)
        projectile.visual.t.scaleVector=Vector3(4f,4f,length/.75f)
        projectile.core?.t?.apply{position=Vector3(0f,0f,length*.5f);scaleVector=Vector3(1f,1f,length)}
    }
    private fun eliminate(id:Int,tool:Int){if(!engine.kill(id))return;trace("HIT id=$id tool=$tool kills=${engine.kills}");val v=threats[id]?:return;val t=engine.threats.first{it.id==id};animate(v.body,"fall");v.crawlPlayer?.stop();effect("fx_${weapons[tool]}_impact",t.position);effect("fx_monster_dissolve",t.position,1.2f);soundAt("${species[t.species]}_die_1",t.position);soundAt("${weapons[tool]}_impact_1",t.position)}
    private fun clearBattle(){threats.values.forEach{it.body.safeDestroy();it.frame.safeDestroy();it.crawl.safeDestroy()};threats.clear();projectiles.forEach{it.entity.safeDestroy()};projectiles.clear()}
    private fun emit(){val phaseLabel=if(context.resources.configuration.locales[0].language=="zh")when(engine.phase){HuntPhase.LOGO->"启动中";HuntPhase.SAFETY->"安全提示";HuntPhase.GESTURE->"手势注册";HuntPhase.TOOLS->"工具选择";HuntPhase.PICKUP->"双手接取";HuntPhase.RUNNING->"任务进行中";HuntPhase.RESULTS->"任务结算"}else engine.phase.name
        panel?.components?.get(AccessibilityComponent::class.java)?.let{if(it.value!=phaseLabel)it.value=phaseLabel}
        state=HuntSnapshot(engine.phase,engine.lives,engine.kills,engine.survival.toInt(),engine.grade(),gesture.progress/2.5f,selected,equipped[0],equipped[1],controllerUsed(),if(engine.phase==HuntPhase.RUNNING)combatTracking.ready else latestLeft!=null&&latestRight!=null,loading,eligiblePlanes().size,error,engine.invulnerable>1.7f,ContextCompat.checkSelfPermission(context,"com.picovr.permission.HAND_TRACKING")==PackageManager.PERMISSION_GRANTED,gesture.adjusting,(if(gesture.isArmed(0))1 else 0)+(if(gesture.isArmed(1))2 else 0))}
    fun dispose(){eventWriter.shutdown();voiceController?.stop();voiceController?.close();hands.stop();hmd.stop();controllers.removeControllerActionListener(listener);controllers.stop();cancelFrame?.invoke();cancelPlane?.invoke();if(planeStarted)PlaneTrackingManager.stop();clearBattle();root.safeDestroy();activeAnimations.forEach{it.second.close()};activeAnimations.clear();audio.values.forEach{it.close()};voices.values.forEach{it.close()}}
}
