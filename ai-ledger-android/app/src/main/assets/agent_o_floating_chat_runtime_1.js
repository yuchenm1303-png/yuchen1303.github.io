
  const root=document.getElementById('glass-blur-motion-lab-v2');
  const nativeProduction=Boolean(window.GuiPlusNative);
  const ORB_MAX=nativeProduction?116:190;
  const spectrumPhase=44.45;
  root.style.setProperty('--spectrum-delay',`${-spectrumPhase}s`);
  const stage=root.querySelector('.blur-stage');
  const shell=root.querySelector('.glass-shell');
  const detail={textContent:''};
  const initialStageRect=stage.getBoundingClientRect();
  const stageSize={width:initialStageRect.width,height:initialStageRect.height};
  if('ResizeObserver' in window){
    const stageResizeObserver=new ResizeObserver(entries=>{
      const entry=entries[0];
      const borderSize=entry.borderBoxSize&&(entry.borderBoxSize[0]||entry.borderBoxSize);
      if(borderSize){stageSize.width=borderSize.inlineSize;stageSize.height=borderSize.blockSize;}
      else{const rect=stage.getBoundingClientRect();stageSize.width=rect.width;stageSize.height=rect.height;}
      opticalSurfaceDirty=true;
      ensureAnimationLoop();
    });
    stageResizeObserver.observe(stage,{box:'border-box'});
  }else{
    window.addEventListener('resize',()=>{const rect=stage.getBoundingClientRect();stageSize.width=rect.width;stageSize.height=rect.height;opticalSurfaceDirty=true;ensureAnimationLoop();},{passive:true});
  }
  const labels=[
    'V8.4 七彩休眠态 · 原始活体轮廓与雾核，仅色相缓慢往返',
    '七彩输入态 · 单一可调薄膜边缘光场与一层柔和外辉光',
    '七彩对话态 · 单一可调薄膜边缘光场与一层柔和外辉光'
  ];
  const parameterConfig=[
    ['飞行前收缩','shrinkScale','收缩尺寸',.56,.30,.90,.01,'×'],
    ['飞行前收缩','retreatX','蓄力水平位移',0,-80,30,1,'px'],
    ['飞行前收缩','retreatY','蓄力垂直位移',-20,-60,40,1,'px'],
    ['飞行前收缩','retreatSkew','蓄力倾斜角',0,-12,12,.2,'°'],
    ['飞行前收缩','retreatSX','蓄力横向压缩',.88,.65,1.20,.01,'×'],
    ['飞行前收缩','retreatSY','蓄力纵向鼓起',1.10,.75,1.35,.01,'×'],
    ['飞行与落点','launchDelay','起飞时刻',90,30,260,5,'ms'],
    ['飞行与落点','launchScale','飞行尺寸',.68,.35,1.10,.01,'×'],
    ['飞行与落点','launchX','飞行落点 X',0,-80,100,1,'px'],
    ['飞行与落点','launchY','飞行弧高 Y',-7,-70,60,1,'px'],
    ['飞行与落点','launchSkew','飞行倾斜角',0,-12,12,.2,'°'],
    ['飞行与落点','launchSX','飞行横向拉伸',1.20,.75,1.50,.01,'×'],
    ['飞行与落点','launchSY','飞行纵向压缩',.82,.60,1.25,.01,'×'],
    ['撞开胶囊','impactDelay','开始展开时刻',175,80,420,5,'ms'],
    ['撞开胶囊','impactScale','展开过冲尺寸',1.025,.90,1.16,.005,'×'],
    ['撞开胶囊','impactX','展开偏移 X',0,-40,50,1,'px'],
    ['撞开胶囊','impactSkew','展开倾斜角',0,-8,8,.2,'°'],
    ['撞开胶囊','impactSX','展开横向过冲',1.035,.90,1.18,.005,'×'],
    ['撞开胶囊','impactSY','展开纵向回缩',.97,.82,1.12,.005,'×'],
    ['回弹与内容','settleDelay','回正时刻',285,140,650,5,'ms'],
    ['回弹与内容','contentDelay','文字出现时刻',350,180,800,5,'ms'],
    ['回弹与内容','poseFrequency','姿态弹簧速度',16.5,6,30,.5,''],
    ['回弹与内容','poseDamping','姿态阻尼',.76,.35,1.25,.01,''],
    ['回弹与内容','scaleFrequency','缩放弹簧速度',18,6,32,.5,''],
    ['回弹与内容','scaleDamping','缩放阻尼',.72,.35,1.25,.01,''],
    ['形状弹簧','widthFrequency','宽度弹簧速度',13.8,5,28,.5,''],
    ['形状弹簧','widthDamping','宽度阻尼',.78,.35,1.25,.01,''],
    ['形状弹簧','heightFrequency','高度弹簧速度',13,5,28,.5,''],
    ['形状弹簧','heightDamping','高度阻尼',.86,.35,1.25,.01,''],
    ['形状弹簧','topRadiusFrequency','顶部圆角速度',15.8,5,30,.5,''],
    ['形状弹簧','topRadiusDamping','顶部圆角阻尼',.82,.35,1.25,.01,''],
    ['形状弹簧','bottomRadiusFrequency','底部圆角速度',11.8,5,28,.5,''],
    ['形状弹簧','bottomRadiusDamping','底部圆角阻尼',.91,.35,1.25,.01,''],
    ['形状弹簧','anchorFrequency','底部锚点速度',12.8,5,28,.5,''],
    ['形状弹簧','anchorDamping','底部锚点阻尼',.91,.35,1.25,.01,''],
    ['胶囊与面板','capsuleHeight','胶囊高度',82,58,120,1,'px'],
    ['胶囊与面板','capsuleWidth','胶囊最大宽度',520,280,680,5,'px'],
    ['胶囊与面板','panelHeight','面板最大高度',360,220,500,5,'px'],
    ['胶囊与面板','panelTopRadius','面板顶部圆角',29,12,60,1,'px'],
    ['胶囊与面板','panelBottomRadius','面板底部圆角',39,12,60,1,'px'],
    ['胶囊与面板','panelPrepressY','面板展开前下压',6,-5,20,1,'px'],
    ['胶囊与面板','panelLiftY','面板掀起高度',-9,-30,10,1,'px'],
    ['拖动反馈','dragPressScale','按下缩放',.965,.85,1,.005,'×'],
    ['拖动反馈','dragSkewMax','拖动最大倾斜',3.2,0,10,.2,'°'],
    ['拖动反馈','dragSkewGain','速度倾斜增益',2.4,0,8,.2,''],
    ['拖动反馈','dragStretchMax','拖动最大拉伸',.026,0,.12,.002,'×']
  ];
  const P=Object.fromEntries(parameterConfig.map(item=>[item[1],item[3]]));
  const defaults={...P};
  const easyConfig=[
    ['展开边缘光','edgeBrightness','边缘亮度',40,40,180,1,'柔和','高亮'],
    ['展开边缘光','edgeThickness','边缘粗细',50,50,180,1,'纤细','厚实'],
    ['展开边缘光','edgeGlow','外光强度',180,0,180,1,'关闭','明亮'],
    ['展开边缘光','edgeSpread','外光范围',170,30,170,1,'贴边','扩散'],
    ['展开边缘光','colorSpeed','光色变化速度',124,20,180,1,'舒缓','灵动'],
    ['展开边缘光','colorRichness','色彩浓度',180,20,180,1,'淡雅','鲜艳'],

    ['圆球光感','orbBrightness','圆球亮度',180,40,180,1,'幽暗','明亮'],
    ['圆球光感','orbDepth','内部深度',180,40,180,1,'轻透','深邃'],
    ['圆球光感','orbMist','内部雾感',180,0,180,1,'清澈','朦胧'],
    ['圆球光感','orbBreath','呼吸幅度',180,0,180,1,'稳定','明显'],

    ['展开手感','transitionSpeed','展开速度',160,50,160,1,'舒缓','迅速'],
    ['展开手感','stretch','拉伸幅度',0,0,180,1,'克制','明显'],
    ['展开手感','bounce','回弹力度',132,0,170,1,'稳重','灵动'],
    ['展开手感','dragFeel','拖动弹性',180,0,180,1,'稳定','有弹性']
  ];
  const E=Object.fromEntries(easyConfig.map(item=>[item[1],item[3]]));
  const easyDefaults={...E};
  const O={};
  let form=0;
  let geometryForm=0;
  let morphDuration=360;
  let transitionToken=0;
  const transitionTimers=new Set();
  let offsetX=0;
  let offsetY=0;
  let dragging=false;
  let moved=false;
  let pointerId=-1;
  let dragMaxX=0;
  let dragMaxY=0;
  let offsetDirty=true;
  let startClientX=0;
  let startClientY=0;
  let startOffsetX=0;
  let startOffsetY=0;
  let activeImageUrl='';
  let targetScale=1;
  let shellScale=1;
  let shellScaleVelocity=0;
  const pose={x:0,y:0,skew:0,stretchX:1,stretchY:1};
  const poseTarget={x:0,y:0,skew:0,stretchX:1,stretchY:1};
  const poseVelocity={x:0,y:0,skew:0,stretchX:0,stretchY:0};
  const poseKeys=Object.keys(pose);
  let lastPointerX=0;
  let lastPointerTime=0;
  let previousFrame=performance.now();
  let animationFrameId=0;
  let pageActive=!document.hidden;
  let stageInView=true;
  const geometry={width:ORB_MAX,height:ORB_MAX,topRadius:ORB_MAX*.5,bottomRadius:ORB_MAX*.5,anchorY:0};
  const geometryTarget={width:ORB_MAX,height:ORB_MAX,topRadius:ORB_MAX*.5,bottomRadius:ORB_MAX*.5,anchorY:0};
  const velocity={width:0,height:0,topRadius:0,bottomRadius:0,anchorY:0};
  const geometryKeys=Object.keys(geometry);
  const beadAura=root.querySelector('.bead-aura');
  const opticalCanvas=root.querySelector('.optical-canvas');
  const gl=opticalCanvas.getContext('webgl',{alpha:true,antialias:true,premultipliedAlpha:true});
  let opticalProgram=null;
  let opticalUniforms=null;
  let opticalParametersDirty=true;
  let opticalWasVisible=false;
  let opticalState=0;
  let opticalForm=0;
  let opticalSurfaceKey='';
  let opticalSurfaceDirty=true;
  const opticalPhaseValues=Object.freeze({idle:0,drag:.18,shrink:.30,flight:.52,impact:.76,settle:.92,'panel-press':.38,'panel-lift':.72});
  const rootStyleCache=new Map();
  let lastWidthPx='';
  let lastHeightPx='';
  let lastRadiusPx='';

  if('IntersectionObserver' in window){
    const stageObserver=new IntersectionObserver(entries=>{
      stageInView=entries[0].isIntersecting;
      if(stageInView)ensureAnimationLoop();else pauseAnimationLoop();
    },{threshold:0});
    stageObserver.observe(stage);
  }
  document.addEventListener('visibilitychange',()=>{
    pageActive=!document.hidden;
    if(pageActive)ensureAnimationLoop();else pauseAnimationLoop();
  });

  function syncOpticalSurface(){
    if(!opticalSurfaceDirty)return;
    const orbCssSize=Math.min(ORB_MAX,Math.max(1,stageSize.width-18),Math.max(1,stageSize.height-18));
    const ratio=Math.min(2,window.devicePixelRatio||1);
    const bufferSize=Math.max(1,Math.round(orbCssSize*ratio));
    const surfaceKey=`${orbCssSize.toFixed(3)}:${bufferSize}`;
    setCachedRootVariable('--orb-size',`${orbCssSize}px`);
    if(!gl||!opticalUniforms)return;
    if(surfaceKey===opticalSurfaceKey){opticalSurfaceDirty=false;return;}
    opticalSurfaceKey=surfaceKey;
    opticalSurfaceDirty=false;
    opticalCanvas.width=bufferSize;
    opticalCanvas.height=bufferSize;
    gl.viewport(0,0,bufferSize,bufferSize);
    gl.uniform2f(opticalUniforms.u_resolution,bufferSize,bufferSize);
  }

  function createOpticalProgram(){
    if(!gl){root.dataset.webgl='false';detail.textContent='当前浏览器未提供 WebGL，已启用静态光学回退';return;}
    const vertexSource=`
      attribute vec2 a_position;
      void main(){gl_Position=vec4(a_position,0.0,1.0);}
    `;
    const fragmentSource=`
      precision highp float;
      uniform vec2 u_resolution;
      uniform float u_time;
      uniform float u_state;
      uniform float u_form;
      uniform vec3 u_motion;
      uniform float u_keyLight;
      uniform float u_idleBreath;
      uniform float u_depth;
      uniform float u_mistStrength;

      float sat(float x){return clamp(x,0.0,1.0);}
      float gaussian(float x,float w){return exp(-pow(x/max(w,.0001),2.0));}
      vec2 rotate2(vec2 p,float a){float c=cos(a),s=sin(a);return mat2(c,-s,s,c)*p;}
      float hash21(vec2 p){
        p=fract(p*vec2(123.34,456.21));
        p+=dot(p,p+45.32);
        return fract(p.x*p.y);
      }
      float noise2(vec2 p){
        vec2 i=floor(p),f=fract(p);
        f=f*f*(3.0-2.0*f);
        return mix(mix(hash21(i),hash21(i+vec2(1.0,0.0)),f.x),mix(hash21(i+vec2(0.0,1.0)),hash21(i+vec2(1.0,1.0)),f.x),f.y);
      }

      void main(){
        vec2 localPx=gl_FragCoord.xy-u_resolution*.5;
        float radius=max(1.0,min(u_resolution.x,u_resolution.y)*.5);
        vec2 p=localPx/radius;
        float r2=dot(p,p);
        float rawR=sqrt(r2);
        float beadWeight=1.0-smoothstep(.02,.32,u_form);
        if(rawR>1.04||beadWeight<.002){gl_FragColor=vec4(0.0);return;}

        {
        /* V8.4 活体气泡：轮廓、膜光、雾核使用互不整除的时钟，避免机械循环感。 */
        float orbBreath=.5+.5*sin(u_time*.76+.14*sin(u_time*.27));
        /* Normalize the four simple orb controls around the V8.4 recommended value. */
        float lightControl=pow(clamp(u_keyLight/1.58,.18,2.20),1.22);
        float depthControl=clamp(u_depth/1.04,.15,2.20);
        float mistControl=clamp(u_mistStrength/.24,0.0,2.40);
        float breathControl=clamp(u_idleBreath/.54,0.0,2.40);
        float slowTide=.5+.5*sin(u_time*.29+1.15);
        float dragEnergy=min(1.0,abs(u_motion.x)*.16+abs(u_motion.y)*7.0+abs(u_motion.z)*7.0);
        float charge=smoothstep(.24,.82,u_state);
        float rawAngle=atan(p.y,p.x);
        float shapeBreath=
          sin(u_time*.69)*.58+
          sin(u_time*.31+1.7)*.27+
          sin(u_time*.17+4.2)*.15;
        float shapeLobes=
          sin(rawAngle*2.0-u_time*.205)*.017+
          sin(rawAngle*3.0+u_time*.127+1.8)*.0095+
          sin(rawAngle*5.0-u_time*.079+4.1)*.0045;
        vec2 motionVector=vec2(-u_motion.x*.09,u_motion.z-u_motion.y);
        float motionLength=length(motionVector);
        vec2 motionDirection=motionVector/max(.0001,motionLength);
        float dragBulge=dot(normalize(p+vec2(.0001)),motionDirection)*.018*dragEnergy;
        float boundary=clamp(
          .965+shapeBreath*(.004+.0108*breathControl)+shapeLobes+dragBulge-.009*charge,
          .930,.997
        );
        float shapeMask=1.0-smoothstep(boundary-.012,boundary+.002,rawR);
        if(shapeMask<.002){gl_FragColor=vec4(0.0);return;}
        vec2 bubbleP=p/max(.001,boundary);
        float orbR=length(bubbleP);
        float orbZ=sqrt(max(0.0,1.0-orbR*orbR));
        float orbAngle=atan(bubbleP.y,bubbleP.x);

        vec2 inertia=vec2(-u_motion.x*.0055,-u_motion.y*.46+u_motion.z*.22);
        vec2 chromaDrift=vec2(sin(u_time*.17),cos(u_time*.13+1.4))*vec2(.026,.020);
        vec2 orbQ=bubbleP+inertia+chromaDrift;
        float lightSway=.070*sin(u_time*.105)+.025*sin(u_time*.061+2.4);
        vec2 orbDir=normalize(rotate2(bubbleP,lightSway)+vec2(.0001));

        float edgeFlow=.5+.5*sin(orbAngle*2.0-u_time*.255+.24*sin(u_time*.097));
        float edgeWobble=
          sin(orbAngle*2.0-u_time*.14)*.0055+
          sin(orbAngle*5.0+u_time*.09+1.7)*.0028;
        float ringCenter=.980+edgeWobble+(edgeFlow-.5)*.008;
        float rimHair=gaussian(orbR-ringCenter,.013+.003*orbBreath+.002*edgeFlow);
        float rimFog=gaussian(orbR-(.852+.020*edgeFlow),.222+.020*slowTide+.038*(mistControl-1.0));
        float membraneFresnel=pow(sat(1.0-orbZ),.82);
        float innerRoll=smoothstep(.48,.96,orbR)*(1.0-.10*smoothstep(.96,1.0,orbR));

        float topPink=pow(sat(dot(orbDir,normalize(vec2(.48,.88)))*.5+.5),3.0);
        float rightViolet=pow(sat(dot(orbDir,normalize(vec2(.98,.16)))*.5+.5),3.6);
        float leftCyan=pow(sat(dot(orbDir,normalize(vec2(-.98,.18)))*.5+.5),3.3);
        float bottomWarm=pow(sat(dot(orbDir,normalize(vec2(.10,-1.0)))*.5+.5),3.25);
        float sectorPink=.88+.12*sin(u_time*.61+.5)+.035*sin(u_time*.23+2.0);
        float sectorCyan=.89+.11*sin(u_time*.47+2.2)+.030*sin(u_time*.19+.4);
        float sectorWarm=.87+.13*sin(u_time*.39+4.1)+.035*sin(u_time*.17+2.8);

        vec3 magentaTint=vec3(1.00,.27,.91);
        vec3 violetTint=vec3(.68,.32,1.00);
        vec3 cyanTint=vec3(.16,.77,1.00);
        vec3 warmTint=vec3(1.00,.58,.48);
        vec3 rimTint=(
          violetTint*.18+
          magentaTint*(.08+1.32*topPink*sectorPink)+
          violetTint*(.05+.76*rightViolet)+
          cyanTint*(1.52*leftCyan*sectorCyan)+
          warmTint*(1.04*bottomWarm*sectorWarm)
        )/(.84+topPink*.28+rightViolet*.18+leftCyan*.20+bottomWarm*.16);

        float mistA=noise2(rotate2(orbQ*1.30,.24)+vec2(u_time*.015,-u_time*.011));
        float mistB=noise2(rotate2(orbQ*2.05,-.48)+vec2(-u_time*.009,u_time*.013));
        float cloudy=smoothstep(.16,.86,mistA*.66+mistB*.34);
        vec2 liquidSway=vec2(
          .040*sin(u_time*.145)+.018*sin(u_time*.071+1.4),
          .032*cos(u_time*.117)+.014*sin(u_time*.053+3.1)
        );
        float upperHaze=gaussian(length(orbQ-vec2(-.20,.32)-liquidSway),.70);
        float lowerHaze=gaussian(length(orbQ-vec2(.12,-.36)+liquidSway*.72),.63);
        float centerShadow=gaussian(length(orbQ-vec2(.05,-.02)),.75);
        float centerVeil=gaussian(length(orbQ-vec2(-.12,.12)),1.04);
        float satin=gaussian(length(orbQ-vec2(-.38,.28)),.60)*(.42+.58*orbZ);

        vec3 interior=vec3(.132,.077,.158);
        interior+=vec3(.140,.112,.182)*upperHaze*(.24+.12*slowTide)*(.58+.42*mistControl);
        interior+=vec3(.195,.075,.175)*lowerHaze*(.13+.10*orbBreath)*(.72+.28*mistControl);
        interior+=vec3(.025,.105,.145)*satin*(.14+.11*mistA)*(.30+.70*mistControl);
        interior+=vec3(.070,.052,.082)*centerVeil*(.18+.07*slowTide)*(.65+.35*mistControl);
        interior+=vec3(.115,.070,.155)*(cloudy-.42)*.16*(.35+.65*mistControl);
        interior*=1.0-centerShadow*max(0.0,.035+.018*slowTide+.10*(depthControl-1.0));

        float bodyShade=.67+.33*pow(orbZ,.58);
        float pinkBloom=gaussian(length(bubbleP-vec2(.46,.76)),.47)*innerRoll;
        float cyanBloom=gaussian(length(bubbleP-vec2(-.76,.10)),.45)*innerRoll;
        float warmPool=gaussian(length(bubbleP-vec2(.12,-.78+.018*sin(u_time*.21))),.44)*innerRoll;
        float membranePulse=.90+.085*sin(u_time*.72)+.035*sin(u_time*.31+2.5);
        float userBreathLight=.55+.45*breathControl;
        vec3 membraneLight=rimTint*membraneFresnel*(.245+.105*orbBreath)*membranePulse*lightControl*userBreathLight;
        vec3 softRim=rimTint*rimFog*(.33+.135*orbBreath)*(.90+.20*edgeFlow)*lightControl*(.76+.24*breathControl);
        vec3 hairLight=rimTint*rimHair*(.25+.09*orbBreath+.10*dragEnergy+.11*charge)*(.86+.24*edgeFlow)*lightControl*userBreathLight;
        vec3 localized=
          magentaTint*pinkBloom*(.090+.040*sectorPink)+
          cyanTint*cyanBloom*(.078+.032*sectorCyan)+
          warmTint*warmPool*(.105+.046*sectorWarm);
        localized*=.45+.55*lightControl;
        vec3 color=interior*bodyShade+membraneLight+softRim+hairLight+localized;
        color*=.72+.28*lightControl;
        color*=1.0-(depthControl-1.0)*centerShadow*.22;
        color*=1.0+(mistControl-1.0)*centerVeil*.10;

        /* 展开前不是闪一下，而是把光膜能量收向右上方，随后随球体淡出。 */
        float gather=gaussian(length(bubbleP-vec2(.48,.48)),.34)*charge;
        color+=mix(violetTint,magentaTint,.72)*gather*(.08+.23*charge);
        color*=1.0+.055*orbBreath+.075*dragEnergy;
        color=color/(vec3(1.0)+color*.50);
        color=pow(max(color,0.0),vec3(.875));

        float alpha=.49+.055*cloudy+.17*membraneFresnel+.10*rimFog+.075*rimHair;
        alpha+=.055*(mistControl-1.0)*centerVeil+.025*(lightControl-1.0)*membraneFresnel;
        alpha*=beadWeight*shapeMask;
        alpha=sat(alpha*(.96+.045*orbBreath+.05*charge));
        gl_FragColor=vec4(color*alpha,alpha);
        return;
        }

      }
    `;
    function compile(type,source){
      const shader=gl.createShader(type);gl.shaderSource(shader,source);gl.compileShader(shader);
      if(!gl.getShaderParameter(shader,gl.COMPILE_STATUS))throw new Error(gl.getShaderInfoLog(shader));
      return shader;
    }
    try{
      opticalProgram=gl.createProgram();
      const vertexShader=compile(gl.VERTEX_SHADER,vertexSource);
      const fragmentShader=compile(gl.FRAGMENT_SHADER,fragmentSource);
      gl.attachShader(opticalProgram,vertexShader);
      gl.attachShader(opticalProgram,fragmentShader);
      gl.linkProgram(opticalProgram);
      if(!gl.getProgramParameter(opticalProgram,gl.LINK_STATUS))throw new Error(gl.getProgramInfoLog(opticalProgram));
      gl.deleteShader(vertexShader);gl.deleteShader(fragmentShader);
      root.dataset.webgl='true';
      const buffer=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,buffer);
      gl.bufferData(gl.ARRAY_BUFFER,new Float32Array([-1,-1,1,-1,-1,1,-1,1,1,-1,1,1]),gl.STATIC_DRAW);
      gl.useProgram(opticalProgram);
      const position=gl.getAttribLocation(opticalProgram,'a_position');
      gl.enableVertexAttribArray(position);gl.vertexAttribPointer(position,2,gl.FLOAT,false,0,0);
      const uniformNames=['u_resolution','u_time','u_state','u_form','u_motion','u_keyLight','u_idleBreath','u_depth','u_mistStrength'];
      opticalUniforms=Object.fromEntries(uniformNames.map(name=>[name,gl.getUniformLocation(opticalProgram,name)]));
      syncOpticalSurface();
      gl.clearColor(0,0,0,0);
      gl.enable(gl.BLEND);gl.blendFunc(gl.ONE,gl.ONE_MINUS_SRC_ALPHA);
    }catch(error){root.dataset.webgl='false';detail.textContent=`WebGL 初始化失败，已启用静态光学回退：${error.message}`;opticalProgram=null;}
  }
