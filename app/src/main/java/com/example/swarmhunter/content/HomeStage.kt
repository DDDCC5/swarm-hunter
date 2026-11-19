package com.example.swarmhunter.content

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.swarmhunter.game.*
import com.pico.spatial.core.ecs.AccessibilityComponent
import com.pico.spatial.core.ecs.event.SceneEvents
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.content.panelSize
import com.pico.spatial.ui.foundation.content.setAccessibilityActions
import kotlinx.coroutines.launch

private val Ink=Color(0xFF2C1E15);private val Ivory=Color(0xFFF2E8D8);private val Copper=Color(0xFFDD8950);private val Brown=Color(0xFF241912);private val Muted=Color(0xFF6D5949)
@Composable private fun words(zh:String,en:String):String=if(LocalContext.current.resources.configuration.locales[0].language=="zh")zh else en
@Composable private fun Label(text:String,size:Int=22,color:Color=Ink,bold:Boolean=false,modifier:Modifier=Modifier){BasicText(text,modifier,style=TextStyle(color=color,fontSize=size.sp,fontWeight=if(bold)FontWeight.Bold else FontWeight.Normal,textAlign=TextAlign.Center))}
@Composable private fun Art(file:String,modifier:Modifier=Modifier){val context=LocalContext.current;val bitmap=remember(file){context.assets.open("ui/$file.png").use{BitmapFactory.decodeStream(it).asImageBitmap()}};Image(bitmap,null,modifier,contentScale=ContentScale.FillBounds)}
@Composable private fun Action(text:String,enabled:Boolean=true,onClick:()->Unit){Box(Modifier.width(310.dp).height(72.dp).semantics{role=Role.Button}.clickable(enabled=enabled,onClick=onClick),contentAlignment=Alignment.Center){Art(if(enabled)"button_primary_normal"else"button_primary_disabled",Modifier.fillMaxSize());Label(text,22,if(enabled)Ivory else Muted,true)}}
@Composable private fun Panel(title:String,subtitle:String,content:@Composable ColumnScope.()->Unit){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Art("gesture_panel",Modifier.fillMaxSize());Column(Modifier.padding(44.dp).fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(22.dp,Alignment.CenterVertically)){Label(title,36,bold=true);Label(subtitle,21,Muted);content()}}}

@Composable fun HomeStage(){
    val context=LocalContext.current;val scope=rememberCoroutineScope();val runtime=remember{HuntRuntime(context,scope)};val s=runtime.state;val lifecycle=LocalLifecycleOwner.current;val size=panelSize(1000.dp,660.dp)
    DisposableEffect(runtime,lifecycle){val observer=LifecycleEventObserver{_,event->runtime.appActive=event!=Lifecycle.Event.ON_PAUSE&&event!=Lifecycle.Event.ON_STOP};lifecycle.lifecycle.addObserver(observer);onDispose{lifecycle.lifecycle.removeObserver(observer);runtime.dispose()}}
    LaunchedEffect(runtime){runtime.hands.dataFlow.collect{runtime.receiveHands(it)}}
    SpatialView(Modifier.fillMaxSize(),attachments={
        AttachmentPanel("hunt_panel",size=size){
            Box(Modifier.fillMaxSize()){
                when {
                    s.error!=null->Panel(words("加载遇到问题","Loading failed"),s.error){Action(words("退出","Exit")){com.example.swarmhunter.platform.LaunchActivity.current.get()?.finish()}}
                    s.loading||s.phase==HuntPhase.LOGO->Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Art("app_icon_512",Modifier.size(220.dp));Spacer(Modifier.height(24.dp));Label(words("虫潮猎手","Swarm Hunter"),48,Ivory,bold=true);Label(words("听声辨位 · 抬手出击","Listen. Locate. Strike."),22,Copper);if(s.loading)Label(words("正在准备猎具…","Preparing your gear…"),18,Muted)}
                    s.controller->Panel(words("请放下手柄","Put down the controllers"),words("全程使用双手手势。将双手举到面前，识别后自动继续。","Use your hands throughout. Bring both hands into view to continue.")){Label("!",76,Copper,true)}
                    s.phase==HuntPhase.SAFETY->Panel(words("确认你的游戏空间","Check your play space"),words("开始之前","Before the hunt")){
                        Label(words("请在安全空间内游戏，注意周围障碍物。","Play in a safe space. Watch for nearby obstacles."),25)
                        Label(words("请放下手柄，使用双手手势进行游戏。","Put down the controllers. Use both hands to play."),25)
                        Label(words("缓慢转身，留意身后；感到不适请立即停止。","Turn slowly and check behind you. Stop if you feel unwell."),20,Muted)
                        Row(horizontalArrangement=Arrangement.spacedBy(20.dp)){Action(words("退出","Exit")){com.example.swarmhunter.platform.LaunchActivity.current.get()?.finish()};Action(words("同意","Agree")){runtime.consent()}}
                    }
                    s.phase==HuntPhase.GESTURE->Panel(words("定义你的发射手势","Define your firing pose"),words("做一个你喜欢的静态手势，让左右手保持一致。","Choose any static pose. Match it with both hands.")){
                        Row(horizontalArrangement=Arrangement.spacedBy(35.dp),verticalAlignment=Alignment.CenterVertically){Art("pose_capture_${if(s.tracking)"capturing"else"waiting"}",Modifier.size(150.dp,185.dp));Label("＝",44,Copper);Art("pose_capture_${if(s.tracking)"capturing"else"waiting"}",Modifier.size(150.dp,185.dp))}
                        Box(Modifier.width(650.dp).height(14.dp).background(Brown,RoundedCornerShape(7.dp))){Box(Modifier.fillMaxHeight().fillMaxWidth(s.progress.coerceIn(0f,1f)).background(Copper,RoundedCornerShape(7.dp)))}
                        Label(if(s.tracking&&s.registrationAdjusting)words("稍作调整，两手保持一致；进度已保留。","Adjust both hands to match. Your progress is saved.")else if(s.tracking)words("保持动作 ${(s.progress*2.5f).toString().take(3)} / 2.5 秒","Hold steady ${(s.progress*2.5f).toString().take(3)} / 2.5 s")else if(s.handPermission)words("权限已允许。请将双手放到面前，避免手指被遮挡。","Permission granted. Bring both hands into view and keep your fingers visible.")else words("请允许手部追踪权限，再将双手放到面前。","Allow hand tracking, then bring both hands into view."),22)
                        if(!s.tracking&&!s.handPermission)Action(words("检查权限","Check permissions")){runtime.retryPermissions()}
                    }
                    s.phase==HuntPhase.TOOLS->Panel(words("选好工具，准备出猎","Choose your gear"),words("左右挥手切换，也可用手势点击卡片。","Swipe left or right, or tap a card with your hand.")){
                        Row(horizontalArrangement=Arrangement.spacedBy(14.dp)){for(i in 0..3){val names=listOf(words("蛛丝","Web"),words("激光","Laser"),words("能量弹","Energy"),words("脉冲","Pulse"));Column(Modifier.width(195.dp).border(if(i==s.selected)3.dp else 1.dp,if(i==s.selected)Copper else Muted,RoundedCornerShape(20.dp)).clickable{runtime.chooseTool(i)}.padding(10.dp),horizontalAlignment=Alignment.CenterHorizontally){Art(listOf("thumb_wp01_web","thumb_wp02_laser","thumb_wp03_energy","thumb_wp04_pulse")[i],Modifier.size(165.dp));Label(names[i],23)}}}
                        Row(horizontalArrangement=Arrangement.spacedBy(20.dp)){
                            Action(words("重新注册手势","Re-register pose")){runtime.reregisterGesture()}
                            Action(words("装备这组工具","Equip this pair")){runtime.confirmTools()}
                        }
                    }
                    s.phase==HuntPhase.PICKUP->Panel(words("把双手放上去","Place your hands into the tools"),words("任意手型，手掌靠近对应工具即可自动装备。","Any hand shape works. Move each palm close to its tool to equip.")){
                        Row(horizontalArrangement=Arrangement.spacedBy(70.dp)){Label(if(s.equippedLeft)words("左手已就绪","Left ready")else words("等待左手","Waiting for left hand"),26,if(s.equippedLeft)Copper else Muted);Label(if(s.equippedRight)words("右手已就绪","Right ready")else words("等待右手","Waiting for right hand"),26,if(s.equippedRight)Copper else Muted)}
                        Label(words("无需做发射动作。装备后工具跟手，开始任务后才可发射。","No firing pose needed. Tools follow your hands once equipped; fire after starting."),22)
                        Action(words("开始任务","Start mission"),s.equippedLeft&&s.equippedRight){runtime.start()}
                    }
                    s.phase==HuntPhase.RUNNING->Column(Modifier.fillMaxWidth().padding(14.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(14.dp)){
                        Row(Modifier.background(Brown.copy(alpha=.90f),RoundedCornerShape(22.dp)).padding(20.dp),horizontalArrangement=Arrangement.spacedBy(32.dp),verticalAlignment=Alignment.CenterVertically){Row{for(i in 0..2)Art(if(i<s.lives)"life_full"else"life_empty",Modifier.size(42.dp))};Label(words("消灭 ${s.kills}","Eliminated ${s.kills}"),24,Ivory,bold=true);Label(words("存活 ","Survival ")+"%02d:%02d".format(s.seconds/60,s.seconds%60),24,Ivory,bold=true)}
                        if(!s.tracking)Label(words("双手回到视野，稳住再来。任务已暂停。","Bring both hands into view. Mission paused."),22,Copper)
                        else if(s.planes==0){Label(words("任务已暂停：请允许空间权限，并环视扫描墙面与地面。","Mission paused: allow spatial access, then scan walls and floors."),22,Copper);Action(words("检查空间权限","Check spatial permission")){runtime.retryPermissions()}}
                        else if(s.seconds<8)Label(words("听声寻找目标；先换成不同手型，再做已记录的发射动作。","Locate the sound. Change your hand shape, then make your saved firing pose."),20,Ivory)
                        if(s.tracking)Label(if(s.armedHands==0)words("先换成不同手型，再回到记录动作发射。","Change your hand shape, then return to your saved pose to fire.")else words("发射已就绪：对准目标，做记录动作。","Ready: aim and make your saved pose."),20,Ivory,modifier=Modifier.background(Brown.copy(alpha=.9f),RoundedCornerShape(12.dp)).padding(10.dp))
                        if(s.hurt)Label(words("受伤！稳住，还能打。","Hit! Stay sharp. You're still in this."),28,Copper,true)
                    }
                    s.phase==HuntPhase.RESULTS->Panel(words("任务结算","Mission complete"),words("调整呼吸，下一轮更准。","Take a breath. Make the next one count.")){
                        Label(s.grade,100,Copper,true)
                        Row(horizontalArrangement=Arrangement.spacedBy(70.dp)){Column(horizontalAlignment=Alignment.CenterHorizontally){Label("${s.kills}",48,bold=true);Label(words("消灭数量","Eliminated"),22,Muted)};Column(horizontalAlignment=Alignment.CenterHorizontally){Label("%02d:%02d".format(s.seconds/60,s.seconds%60),48,bold=true);Label(words("存活时长","Survival time"),22,Muted)}}
                        Action(words("再来一次","Hunt again")){runtime.retry()}
                    }
                }
            }
        }
    },initial={content,attachments->
        val panel=attachments.entity("hunt_panel")
        if(panel!=null){
            panel.components.set(AccessibilityComponent(label=if(context.resources.configuration.locales[0].language=="zh")"虫潮猎手操作面板"else"Swarm Hunter controls",value="").apply{isAccessibilityElement=true})
            content.setAccessibilityActions(panel,"hunt.controls") {}
        }
        runtime.initialize(content,panel)
        val subscription=content.subscribe(SceneEvents.Update::class.java){event->runtime.frame(event.deltaTime,event.scene)}
        runtime.cancelFrame={subscription.cancel()}
    })
}
