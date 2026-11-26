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
import com.pico.spatial.core.ecs.animation.*
import org.json.JSONObject
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
data class HuntSnapshot(val phase:HuntPhase=HuntPhase.LOGO,val lives:Int=3,val kills:Int=0,val seconds:Int=0,val grade:String="C",val progress:Float=0f,val selected:Int=0,val equippedLeft:Boolean=false,val equippedRight:Boolean=false,val controller:Boolean=false,val tracking:Boolean=false,val loading:Boolean=true,val planes:Int=0,val error:String?=null,val hurt:Boolean=false,val handPermission:Boolean=false,val registrationAdjusting:Boolean=false,val armedHands:Int=0,val trapsPlaced:Int=0,val placementValid:Boolean=false,val placementHand:Int=1)

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
    private val room=RoomSurfaceRegistry()
    private val roomAdapter=RoomSurfaceAdapter(root,room)
    private val traps=TrapController()
    private val trapVisuals=mutableMapOf<Int,Entity>()
    private var trapGhost:Entity?=null
    private var placementCandidate:SurfaceHit?=null
    private val animationPlayers=mutableMapOf<Entity,List<AnimationPlaybackController>>()
    private val retiredPlayers=mutableListOf<Pair<AnimationPlaybackController,Float>>()
    private val animationNames=mutableMapOf<Entity,String>()
    private val retiredAnimations=mutableListOf<Pair<AnimationResource,Float>>()
    private val strides=mutableMapOf<String,Float>()
    private var disposed=false
    private var planeWasRunning=false
    private val roomTracking=RoomTrackingGrace()
    private val roomSnapshots=RoomSnapshotGate()
    private var trackingExpired=false
    private var rangeGhost:Entity?=null
    private var surfaceCheckStarted=false
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
    private data class Projectile(val entity:Entity,val visual:Entity,val core:Entity?,val tool:Int,val motion:RicochetState,val launch:V3,var last:V3,var age:Float=0f){val direction get()=motion.direction}
    fun receiveHands(data:HandTrackingData){handData=data;lastHandAt=SystemClock.elapsedRealtime();val presence=(if(data.left!=null)1 else 0)+(if(data.right!=null)2 else 0);if(presence!=lastHandPresence){lastHandPresence=presence;trace("HAND_FRAME left=${data.left!=null} right=${data.right!=null}")}}
    fun beginProviders(){if(providersStarted)return;providersStarted=true;hmd.start();controllers.addControllerActionListener(listener);controllers.start()}
    suspend fun initialize(c:SpatialViewContent,p:Entity?) {
        content=c;panel=p;c.addEntity(root);p?.let{root.addChild(it);it.t.position=Vector3(0f,1.4f,-1.8f)}
        toolMounts.forEach{root.addChild(it)};root.addChild(voiceEntity);voiceEntity.components.set(ChannelAudioComponent())
        try {
            val modelFiles=species+listOf("trap_pulse","fx_ricochet","fx_surface_impact","fx_trap_pulse","rift_wall","rift_floor")+listOf("fx_web","fx_laser","fx_energy","fx_pulse","fx_web_impact","fx_laser_impact","fx_energy_impact","fx_pulse_impact","fx_monster_dissolve","fx_rift_dust")+weapons.flatMap{w->val i=weapons.indexOf(w)+1;listOf("wp0${i}_${w}_left","wp0${i}_${w}_right")}
            for(id in modelFiles){templates[id]=Entity.loadSuspend("asset://models/$id.glb").also{root.addChild(it);it.enabled=false}}
            for(id in species){strides[id]=context.assets.open("models/$id.json").bufferedReader().use{JSONObject(it.readText()).getJSONObject("motion").getDouble("strideMeters").toFloat()}}
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
                trace("ANIMATION_IMPORT_CHECK passed: ${verifyAnimationImport(root,templates.filterKeys{it in species||it=="trap_pulse"})}")
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
    fun prepareTraps(){if(controllerUsed()||engine.phase!=HuntPhase.PICKUP||!equipped.all{it})return
        traps.reset();traps.suspendInput();engine.phase=HuntPhase.PLACEMENT;gesture.suspendInput();speak("trap_prepare");emit()}
    fun undoTrap(){if(engine.phase!=HuntPhase.PLACEMENT||controllerUsed())return;traps.undo()?.let{t->trapVisuals.remove(t.id)?.let{e->val p=e.t.position.v();animate(e,"recover");temporary.add(e to(now+.8f));soundAt("trap_recover",p)}};emit()}
    fun start(){if(controllerUsed()||engine.phase !in listOf(HuntPhase.PICKUP,HuntPhase.PLACEMENT)||!equipped.all{it})return;clearBattle();trapGhost?.enabled=false;rangeGhost?.enabled=false;placementCandidate=null;traps.suspendInput();trapVisuals.values.forEach{animate(it,"arm");soundAt("trap_arm",it.t.position.v())};engine.start();gesture.suspendInput();combatTracking.reset();nextSpawn=2.5f;trace("GAME_START eligiblePlanes=${eligiblePlanes().size}");speak("mission_start");emit()}
    fun retry(){if(controllerUsed())return;clearTraps();clearBattle();engine.phase=HuntPhase.TOOLS;equipped.fill(false);tools.forEach{it?.enabled=false};speak("tool_select");emit()}
    private fun controllerUsed()=SystemClock.elapsedRealtime()-controllerAt<1500L
    private fun newModel(id:String,parent:Entity=root):Entity {val e=checkNotNull(templates[id]?.clone(Entity.CloneOptions(recursive=true,shouldShareMaterialInstance=true))){"Model clone failed: $id"};parent.addChild(e);e.enabled=true;return e}
    private fun animate(e:Entity,name:String,loop:Boolean=false,freezeAtEnd:Boolean=false,speed:Float=1f){
        animationPlayers[e]?.forEach{retiredPlayers.add(it to(now+.16f))}
        animationNames[e]=name
        val players=mutableListOf<AnimationPlaybackController>()
        for(skeleton in e.findSkeletonRootEntity()){
            val a=skeleton.getAnimationResources().firstOrNull{it.getName()==name}?:continue
            val previous=activeAnimations.filter{it.first==skeleton}
            previous.forEach{retiredAnimations.add(it.second to(now+.16f))};activeAnimations.removeAll(previous.toSet())
            val resource=a.repeat(if(loop)100000 else 0).also{it.toGlobal()}
            val player=skeleton.playAnimation(resource,AnimationPlayConfig(transitionDuration=if(name=="crawl"||name=="idle"||name=="attack").10f else 0f))
            player.setSpeed(speed);if(freezeAtEnd){player.setTime((player.getDuration()-.001f).coerceAtLeast(0f));player.pause()}
            players.add(player);activeAnimations.add(skeleton to resource)
        }
        animationPlayers[e]=players
    }
    private fun locomotion(e:Entity,speciesIndex:Int,speed:Float){
        val multiplier=creaturePlaybackSpeed(speed,strides[species[speciesIndex]]?:.08f)
        if(animationNames[e]!="crawl")animate(e,"crawl",true,speed=multiplier)else animationPlayers[e]?.filter{it.valid}?.forEach{it.setSpeed(multiplier)}
    }
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
        val planeRunning=planeStarted&&PlaneTrackingManager.state==TrackingState.RUNNING
        roomTracking.update(planeRunning,if(planeStarted)dt else 0f)
        roomAdapter.setAvailable(planeRunning)
        if(!planeRunning)engine.paused=true
        if(planeStarted&&!planeRunning&&planeWasRunning){roomSnapshots.invalidate();traps.suspendInput();trace("ROOM_TRACKING_LOST paused=true")}
        if(roomTracking.expired&&!trackingExpired){planes.clear();roomAdapter.update(emptyList());clearTraps();trace("ROOM_TRACKING_EXPIRED reclaimed=true")}
        trackingExpired=roomTracking.expired
        if(planeRunning&&!planeWasRunning)refreshPlanes()
        planeWasRunning=planeRunning
        if(!surfaceCheckStarted&&roomAdapter.hasProxies&&context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE!=0){surfaceCheckStarted=true;scope.launch{delay(300);if(!disposed)runCatching{roomAdapter.verifyNative(world)}.onSuccess{trace("ROOM_COLLISION_CHECK passed: $it")}.onFailure{trace("ROOM_COLLISION_CHECK failed: ${it.message}")}}}
        if(engine.phase==HuntPhase.RUNNING&&engine.paused!=wasPaused){
            trace("COMBAT_PAUSED value=${engine.paused} hands=$tracked controller=$blocked surfaces=${eligiblePlanes().size} foreground=$appActive")
            gesture.suspendInput()
            for(p in projectiles){p.entity.components.set(PhysicsVelocityComponent(if(engine.paused)Vector3.ZERO else(p.direction*p.motion.speed).vec(),Vector3.ZERO));p.last=p.entity.t.position.v()}
            threats.values.forEach{v->if(engine.paused)v.crawlPlayer?.pause() else v.crawlPlayer?.play()
                animationPlayers[v.body]?.filter{it.valid}?.forEach{if(engine.paused)it.pause()else it.resume()}}
        }
        wasPaused=engine.paused
        val running=engine.phase==HuntPhase.RUNNING||engine.phase==HuntPhase.PLACEMENT
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
        if(engine.phase==HuntPhase.PLACEMENT)updatePlacement(blocked||!appActive||!room.available)
        updateTrapVisuals()
        if(engine.phase==HuntPhase.RUNNING&&!engine.paused){engine.tick(dt);nextSpawn-=dt;if(nextSpawn<=0&&threats.size<7&&planes.isNotEmpty()){spawn();nextSpawn=Random.nextDouble(2.8,5.0).toFloat()}
            for(i in 0..1){val p=if(i==0)latestLeft else latestRight;if(gesture.fire(i,features(p),dt)&&p!=null)shoot(i)}
            updateBattle(dt,world)
            for((trap,ids)in traps.tick(dt,room,engine.threats.filter{it.alive&&it.visible&&threats[it.id]?.emerged==true}.map{it.id to it.position})){
                val p=room.surfaces[trap.anchorId]?.world(trap.localPosition)?:continue
                trapVisuals.remove(trap.id)?.let{animate(it,"trigger");temporary.add(it to(now+.6f))}
                effect("fx_trap_pulse",p,.8f);soundAt("trap_trigger",p)
                ids.forEach{eliminate(it,3)};trace("TRAP_TRIGGER id=${trap.id} hits=${ids.size}")
            }
        }
        if(engine.phase==HuntPhase.RESULTS&&lastPhase!=HuntPhase.RESULTS){trace("GAME_OVER kills=${engine.kills} survival=${engine.survival}");speak("mission_end");threats.values.forEach{it.crawlPlayer?.stop()}}
        if(lastPhase==HuntPhase.RUNNING&&engine.phase!=HuntPhase.RUNNING)projectiles.forEach{it.entity.components.set(PhysicsVelocityComponent(Vector3.ZERO,Vector3.ZERO))}
        lastPhase=engine.phase
        val endedPlayers=retiredPlayers.filter{it.second<=now};endedPlayers.forEach{if(it.first.valid){it.first.stop();it.first.close()}};retiredPlayers.removeAll(endedPlayers.toSet())
        val retired=retiredAnimations.filter{it.second<=now};retired.forEach{it.first.close()};retiredAnimations.removeAll(retired.toSet())
        animationPlayers.keys.filter{!it.valid}.forEach{animationPlayers.remove(it);animationNames.remove(it)}
        val finishedAnimations=activeAnimations.filter{!it.first.valid};finishedAnimations.forEach{it.second.close()};activeAnimations.removeAll(finishedAnimations.toSet())
        val expired=temporary.filter{it.second<=now};expired.forEach{it.first.safeDestroy()};temporary.removeAll(expired.toSet());publish+=dt;if(publish>=.05f){publish=0f;emit()}
    }
    private fun refreshPlanes(){
        scope.launch{
            val request=roomSnapshots.request()
            val all=PlaneTrackingManager.loadAllAnchors()
            if(disposed||!roomSnapshots.accepts(request)||PlaneTrackingManager.state!=TrackingState.RUNNING)return@launch
            planes.clear();all.forEach{planes[it.anchorUUID]=it};roomAdapter.update(all.toList());traps.reconcile(room)
        }
    }
    private fun startPlanesIfPermitted(){
        if(planeStarted||!SessionConsent.accepted||ContextCompat.checkSelfPermission(context,"com.picovr.permission.SPATIAL_DATA")!=PackageManager.PERMISSION_GRANTED)return
        planeStarted=true
        val subscription=PlaneTrackingManager.subscribeAnchorUpdate{_->if(!disposed&&PlaneTrackingManager.state==TrackingState.RUNNING)refreshPlanes()}
        cancelPlane={subscription.cancel()};PlaneTrackingManager.start()
        scope.launch{while(!disposed&&PlaneTrackingManager.state!=TrackingState.RUNNING)delay(100);if(!disposed)refreshPlanes()}
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
        val toward=(t.position-head).normalized();if(!t.visible&&t.age>.7f&&toward.dot(forward)>.67f){engine.reveal(t.id);trace("REVEAL id=${t.id}");v.body.enabled=true;animate(v.body,"crawl",true);locomotion(v.body,t.species,v.depth/2.5f);soundAt("${species[t.species]}_emerge_1",t.position);v.cue=t.age}
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
            if(!near&&!v.attack.active){val movement=delta.normalized();t.position=pointOnSurface(t.position+movement*(dt*(.12f+engine.survival*.0004f)),v.surfaceOrigin,normal);v.body.t.position=t.position.vec();v.body.t.quaternion=surfaceRotation(normal,movement).quat();v.crawl.t.position=t.position.vec();locomotion(v.body,t.species,.12f+engine.survival*.0004f)}
            when(v.attack.tick(dt,near)){
                AttackEvent.PREPARE->{animate(v.body,"attack");soundAt("${species[t.species]}_windup_1",t.position);soundAt("${species[t.species]}_attack_1",t.position)}
                AttackEvent.STRIKE->{soundAt("${species[t.species]}_landing_1",t.position);if(engine.attack(t.id)){speak(if(engine.lives==1)"last_life"else"player_hurt");soundAt("player_hurt",head)}}
                AttackEvent.RECOVERED->{animate(v.body,if(near)"idle"else"crawl",true);if(!near)locomotion(v.body,t.species,.12f+engine.survival*.0004f)}
                null->Unit
            }

        }
    }
        for(p in projectiles.toList()){
            p.age+=dt;val current=p.entity.t.position.v();val travel=current-p.last
            p.motion.advance(dt,travel.length())
            if(p.tool==0)updateSilkTrail(p,(current-p.launch).length())
            var origin=p.last;var remaining=travel.length();var end=current;var removed=false
            repeat(3){
                if(removed||remaining<.00001f)return@repeat
                val hit=combatHit(world,origin,p.direction,remaining,.025f)
                if(hit==null){end=origin+p.direction*remaining;remaining=0f;return@repeat}
                if(hit.enemy!=null){eliminate(hit.enemy,p.tool);removed=true;return@repeat}
                val surface=hit.surface?:return@repeat
                val position=surface.point
                if(canRicochet(p.tool,surface.surface.kind)&&p.motion.reflect(surface.normal)){
                    effect("fx_ricochet",position,.6f);soundAt("energy_ricochet_${Random.nextInt(1,4)}",position)
                    remaining=(remaining-hit.distance).coerceAtLeast(0f)*.8f
                    origin=position+surface.normal*.036f+p.direction*.01f;end=origin
                    p.entity.t.quaternion=handRayRotation(p.direction,V3(0f,1f,0f)).quat()
                    p.entity.components.set(PhysicsVelocityComponent((p.direction*p.motion.speed).vec(),Vector3.ZERO))
                    trace("RICOCHET count=${p.motion.count} surface=${surface.surface.kind} speed=${p.motion.speed}")
                }else{
                    surfaceImpact(p.tool,surface);removed=true
                }
            }
            if(removed||p.motion.expired){p.entity.safeDestroy();projectiles.remove(p)}else{p.entity.t.position=end.vec();p.last=end}
        }
    }
    private val sweepShapeDelegate=lazy{ShapeResource.createSphere(.025f).also{it.toGlobal()}}
    private val sweepShape by sweepShapeDelegate
    private fun combatHit(world:Scene,origin:V3,direction:V3,length:Float,radius:Float=0f):CombatHit? {
        val results=if(radius>0f)world.convexCast(sweepShape,origin.vec(),Quat.identity(),direction.vec(),length,CollisionCastHitMode.ALL,CollisionGroup(),root).results
            else world.rayCast(origin.vec(),direction.vec(),length,CollisionCastHitMode.ALL,CollisionGroup(),root).results
        val enemies=results.mapNotNull{h->threats.entries.firstOrNull{it.value.body==h.entity}?.let{(id,_)->engine.threats.firstOrNull{it.id==id&&it.alive&&it.visible}?.let{id to h.distance}}}
        val analytic=room.cast(origin,direction,length)
        val native=results.mapNotNull{h->roomAdapter.surfaceFor(h.entity)?.let{s->var n=h.normal.v().normalized();if(n.dot(direction)>0)n=n*-1f;SurfaceHit(s,h.distance,h.position.v(),n)}}.minByOrNull{it.distance}
        val surface=listOfNotNull(analytic,native).minByOrNull{it.distance}
        return nearestCombatHit(enemies,surface,length)
    }
    private fun surfaceImpact(tool:Int,hit:SurfaceHit){
        val p=hit.point+hit.normal*.008f
        effect("fx_surface_impact",p,.6f);soundAt("surface_impact_${Random.nextInt(1,3)}",p)
        val mark=newModel("fx_${weapons[tool]}_impact");mark.t.position=p.vec();mark.t.quaternion=surfaceRotation(hit.normal,V3(0f,-1f,0f)).quat();animate(mark,"burst");temporary.add(mark to(now+if(tool==0)2f else .6f))
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
            val hit=scene?.let{combatHit(it,pos,direction,7f)};val distance=hit?.distance?:7f
            if(distance<=.17f){hit?.enemy?.let{eliminate(it,selected)};hit?.surface?.let{surfaceImpact(selected,it)};return}
            val e=newModel("fx_laser");e.t.position=(pos+direction*.17f).vec();e.t.quaternion=rot;e.t.scaleVector=Vector3(2f,2f,beamLengthScale(distance));animate(e,"flight",freezeAtEnd=true);temporary.add(e to(now+.32f))
            val length=.9f*beamLengthScale(distance)
            val core=checkNotNull(laserCoreTemplate?.clone(Entity.CloneOptions(recursive=true,shouldShareMaterialInstance=true)))
            root.addChild(core);core.t.position=(pos+direction*(.17f+length*.5f)).vec();core.t.quaternion=rot;core.t.scaleVector=Vector3(1f,1f,length);core.enabled=true;temporary.add(core to(now+.32f))
            trace("LASER_VISUAL coreVisible=${core.enabled} length=$length lifetimeMs=320 blockedBySurface=${hit?.surface!=null}")
            hit?.enemy?.let{eliminate(it,selected)};hit?.surface?.let{surfaceImpact(selected,it)}
        }else{
            val motion=RicochetState(direction,if(selected==0)10f else 7f)
            var launch=pos+direction*.17f
            val muzzleHit=scene?.let{combatHit(it,pos,direction,.17f,.025f)}
            if(muzzleHit!=null){
                if(muzzleHit.enemy!=null){eliminate(muzzleHit.enemy,selected);return}
                val surface=muzzleHit.surface?:return
                if(canRicochet(selected,surface.surface.kind)&&motion.reflect(surface.normal)){
                    launch=surface.point+surface.normal*.036f+motion.direction*.01f
                    effect("fx_ricochet",surface.point,.6f);soundAt("energy_ricochet_${Random.nextInt(1,4)}",surface.point)
                    motion.advance(0f,muzzleHit.distance)
                }else{surfaceImpact(selected,surface);return}
            }
            val e=Entity();root.addChild(e);e.t.position=launch.vec();e.t.quaternion=handRayRotation(motion.direction,V3(0f,1f,0f)).quat()
            val visual=newModel("fx_${weapons[selected]}",e);visual.t.position=Vector3(0f,0f,when(selected){0->.75f;2->.2f;else->.3f});animate(visual,"flight",freezeAtEnd=true)
            val core=if(selected==0)checkNotNull(silkCoreTemplate?.clone(Entity.CloneOptions(recursive=true,shouldShareMaterialInstance=true))).also{e.addChild(it);it.enabled=true}else null
            val projectile=Projectile(e,visual,core,selected,motion,launch,launch)
            if(selected==0)updateSilkTrail(projectile,0f)
            collider(e,.025f);e.components.set(PhysicsVelocityComponent((motion.direction*motion.speed).vec(),Vector3.ZERO));projectiles.add(projectile)
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
    private fun updatePlacement(blocked:Boolean){
        placementCandidate=null
        if(blocked){traps.suspendInput();trapGhost?.enabled=false;rangeGhost?.enabled=false;return}
        val activeHand=if(latestRight!=null)1 else 0
        traps.pinch(1-activeHand,null,null,room)
        for(hand in listOf(activeHand)){
            val pose=if(hand==0)latestLeft else latestRight
            val ray=stableRays[hand]
            val pinch=if(hand==0)handData?.leftPinch else handData?.rightPinch
            if(pose==null||ray==null||pinch?.isReady!=true){traps.pinch(hand,null,null,room);continue}
            val hit=room.cast(ray.origin,ray.direction,3.5f)?.takeIf{it.distance>=.30f&&room.canPlace(it)&&traps.locationFree(it.point,room)}
            // UI pinches must not also place a device through the operation panel.
            val panelHit=panel?.let{owner->
                val o=owner.convertPositionFrom(ray.origin.vec(),root).v()
                val d=(owner.convertPositionFrom((ray.origin+ray.direction).vec(),root).v()-o).normalized()
                val distance=if(abs(d.z)>.0001f)-o.z/d.z else -1f
                val p=o+d*distance
                distance>0f&&distance<(hit?.distance?:3.5f)&&abs(p.x)<.55f&&abs(p.y)<.37f
            }?:false
            val candidate=if(panelHit)null else hit
            if(placementCandidate==null)placementCandidate=candidate
            val pinched=when{pinch.pinchValue>.8f->true;pinch.pinchValue<.2f->false;else->continue}
            val placed=traps.pinch(hand,pinched,candidate,room)
            if(placed!=null){
                val e=newModel("trap_pulse");trapVisuals[placed.id]=e;animate(e,"deploy")
                soundAt("trap_place",candidate!!.point);trace("TRAP_PLACED id=${placed.id} anchor=${placed.anchorId}")
                if(traps.traps.size==2)speak("trap_limit")
            }
        }
        val hit=placementCandidate
        if(hit!=null&&traps.traps.size<2){
            val ghost=trapGhost?:newModel("trap_pulse").also{it.components.set(OpacityControllerComponent(.40f));trapGhost=it;animate(it,"idle",true)}
            ghost.enabled=true;ghost.t.position=(hit.point+hit.normal*.012f).vec();ghost.t.quaternion=surfaceRotation(hit.normal,V3(0f,0f,-1f)).quat()
            val range=rangeGhost?:newModel("fx_trap_pulse").also{it.components.set(OpacityControllerComponent(.22f));rangeGhost=it;animate(it,"burst")}
            range.enabled=true;range.t.position=ghost.t.position;range.t.scaleVector=Vector3(1.04f,1.04f,1.04f)
            animationPlayers[range]?.filter{it.valid}?.forEach{it.setTime(.70f);it.pause()}
        }else{trapGhost?.enabled=false;rangeGhost?.enabled=false}
    }
    private fun updateTrapVisuals(){
        traps.reconcile(room)
        for(id in trapVisuals.keys.toList())if(traps.traps.none{it.id==id})trapVisuals.remove(id)?.safeDestroy()
        for(t in traps.traps){
            val surface=room.surfaces[t.anchorId]?:continue;val e=trapVisuals[t.id]?:continue
            val normal=surface.rotation.rotate(t.localNormal)
            e.enabled=room.available;e.t.position=(surface.world(t.localPosition)+normal*.012f).vec();e.t.quaternion=surfaceRotation(normal,surface.rotation.rotate(V3(0f,0f,-1f))).quat()
        }
    }
    private fun clearTraps(){traps.reset();trapVisuals.values.forEach{it.safeDestroy()};trapVisuals.clear();trapGhost?.safeDestroy();trapGhost=null;rangeGhost?.safeDestroy();rangeGhost=null;placementCandidate=null}
    private fun clearBattle(){threats.values.forEach{it.body.safeDestroy();it.frame.safeDestroy();it.crawl.safeDestroy()};threats.clear();projectiles.forEach{it.entity.safeDestroy()};projectiles.clear()}
    private fun emit(){val phaseLabel=if(context.resources.configuration.locales[0].language=="zh")when(engine.phase){HuntPhase.LOGO->"启动中";HuntPhase.SAFETY->"安全提示";HuntPhase.GESTURE->"手势注册";HuntPhase.TOOLS->"工具选择";HuntPhase.PICKUP->"双手接取";HuntPhase.PLACEMENT->"布置机关";HuntPhase.RUNNING->"任务进行中";HuntPhase.RESULTS->"任务结算"}else engine.phase.name
        panel?.components?.get(AccessibilityComponent::class.java)?.let{if(it.value!=phaseLabel)it.value=phaseLabel}
        state=HuntSnapshot(engine.phase,engine.lives,engine.kills,engine.survival.toInt(),engine.grade(),gesture.progress/2.5f,selected,equipped[0],equipped[1],controllerUsed(),if(engine.phase==HuntPhase.RUNNING)combatTracking.ready else latestLeft!=null&&latestRight!=null,loading,eligiblePlanes().size,error,engine.invulnerable>1.7f,ContextCompat.checkSelfPermission(context,"com.picovr.permission.HAND_TRACKING")==PackageManager.PERMISSION_GRANTED,gesture.adjusting,(if(gesture.isArmed(0))1 else 0)+(if(gesture.isArmed(1))2 else 0),traps.traps.size,placementCandidate!=null,if(latestRight!=null)1 else 0)}
    fun dispose(){disposed=true;roomSnapshots.invalidate();eventWriter.shutdown();clearTraps();roomAdapter.close();voiceController?.stop();voiceController?.close();hands.stop();hmd.stop();controllers.removeControllerActionListener(listener);controllers.stop();cancelFrame?.invoke();cancelPlane?.invoke();if(planeStarted)PlaneTrackingManager.stop();clearBattle();root.safeDestroy();retiredPlayers.forEach{if(it.first.valid){it.first.stop();it.first.close()}};retiredPlayers.clear();activeAnimations.forEach{it.second.close()};activeAnimations.clear();retiredAnimations.forEach{it.first.close()};retiredAnimations.clear();animationPlayers.clear();if(sweepShapeDelegate.isInitialized())sweepShape.close();audio.values.forEach{it.close()};voices.values.forEach{it.close()}}
}
