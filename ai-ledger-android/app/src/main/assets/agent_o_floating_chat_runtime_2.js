

  function drawOptical(now){
    if(!gl||!opticalProgram||!opticalUniforms)return;
    if(opticalSurfaceDirty)syncOpticalSurface();
    const target=opticalPhaseValues[root.dataset.phase]||0;
    const opticalTarget=geometryForm===0?0:1;
    opticalState+=(target-opticalState)*.16;
    opticalForm+=(opticalTarget-opticalForm)*(opticalTarget===0?.16:.08);
    /* V8.4 shader is mathematically transparent above this form threshold. */
    if(opticalForm>=.32){
      if(opticalWasVisible){gl.clear(gl.COLOR_BUFFER_BIT);opticalWasVisible=false;}
      return;
    }
    opticalWasVisible=true;
    gl.uniform1f(opticalUniforms.u_time,now*.001);
    gl.uniform1f(opticalUniforms.u_state,opticalState);
    gl.uniform1f(opticalUniforms.u_form,opticalForm);
    gl.uniform3f(opticalUniforms.u_motion,pose.skew,pose.stretchX-1.0,pose.stretchY-1.0);
    if(opticalParametersDirty){
      gl.uniform1f(opticalUniforms.u_keyLight,O.keyLight/100);
      gl.uniform1f(opticalUniforms.u_idleBreath,O.idleBreath/100);
      gl.uniform1f(opticalUniforms.u_depth,O.depth/100);
      gl.uniform1f(opticalUniforms.u_mistStrength,O.mistStrength/100);
      opticalParametersDirty=false;
    }
    gl.clear(gl.COLOR_BUFFER_BIT);
    gl.drawArrays(gl.TRIANGLES,0,6);
  }


  function applyEasyParameters(){
    /* Every simple motion control owns one perceptual axis and stays positive across its range. */
    P.shrinkScale=.35;
    P.retreatX=0;P.launchX=0;P.impactX=0;
    P.retreatSkew=0;P.launchSkew=0;P.impactSkew=0;
    P.retreatY=-60;P.launchY=-21;
    const speedFactor=.45+.55*(E.transitionSpeed/100);
    morphDuration=Math.max(240,Math.min(520,Math.round(360/speedFactor)));
    P.launchDelay=Math.round(morphDuration*.25);
    P.impactDelay=Math.round(morphDuration*.49);
    P.settleDelay=Math.round(morphDuration*.79);
    P.contentDelay=P.settleDelay;
    setCachedRootVariable('--motion-duration',`${morphDuration}ms`);
    setCachedRootVariable('--content-duration',`${Math.round(morphDuration*.58)}ms`);

    const stretchFactor=E.stretch/100;
    P.retreatSX=1-.12*stretchFactor;P.retreatSY=1+.10*stretchFactor;
    P.launchSX=1+.20*stretchFactor;P.launchSY=1-.18*stretchFactor;
    P.impactSX=1+.035*stretchFactor;P.impactSY=1-.030*stretchFactor;

    const bounceFactor=Math.max(0,Math.min(1,E.bounce/170));
    const damping=1.10-.68*bounceFactor;
    P.poseDamping=damping;
    P.scaleDamping=Math.max(.40,damping-.04);
    P.widthDamping=damping+.06;P.heightDamping=damping+.09;
    P.topRadiusDamping=damping+.04;P.bottomRadiusDamping=damping+.08;P.anchorDamping=damping+.10;
    const frequency=21.01;
    P.poseFrequency=frequency;P.widthFrequency=frequency*.84;P.heightFrequency=frequency*.79;
    P.panelPrepressY=12;P.panelLiftY=-24;
    const dragFactor=E.dragFeel/100;
    P.dragPressScale=1-.035*dragFactor;
    P.dragSkewMax=3.2*dragFactor;P.dragSkewGain=2.4*dragFactor;P.dragStretchMax=.026*dragFactor;

    const edgeFactor=E.edgeBrightness/100;
    const colorSpeed=Math.max(.1,E.colorSpeed/100);
    setCachedRootVariable('--edge-flow-opacity',String(Math.min(1,.55+.45*edgeFactor)));
    setCachedRootVariable('--edge-brightness',String(.76+.50*edgeFactor));
    setCachedRootVariable('--edge-thickness',`${(.75+1.45*E.edgeThickness/100).toFixed(2)}px`);
    setCachedRootVariable('--outer-glow-strength',String(Math.max(0,E.edgeGlow/100)));
    setCachedRootVariable('--outer-glow-spread',String(Math.max(.05,E.edgeSpread/100)));
    setCachedRootVariable('--edge-flow-duration',`${(7.2/colorSpeed).toFixed(3)}s`);
    setCachedRootVariable('--spectrum-duration',`${(56/colorSpeed).toFixed(3)}s`);
    setCachedRootVariable('--edge-flow-saturation',String(.45+.55*E.colorRichness/100));

    const orbBrightness=E.orbBrightness/100;
    const orbDepth=E.orbDepth/100;
    const orbMist=E.orbMist/100;
    const orbBreath=E.orbBreath/100;
    const nextKeyLight=158*orbBrightness;
    const nextIdleBreath=54*orbBreath;
    const nextDepth=104*orbDepth;
    const nextMistStrength=24*orbMist;
    if(O.keyLight!==nextKeyLight||O.idleBreath!==nextIdleBreath||O.depth!==nextDepth||O.mistStrength!==nextMistStrength){
      O.keyLight=nextKeyLight;O.idleBreath=nextIdleBreath;O.depth=nextDepth;O.mistStrength=nextMistStrength;
      opticalParametersDirty=true;
    }
    setCachedRootVariable('--bead-aura-strength',String(1.48*orbBrightness));
    setCachedRootVariable('--bead-aura-spread','1.08');
    setCachedRootVariable('--bead-aura-breath',String(.54*orbBreath));
    setCachedRootVariable('--bead-hotspot-size','1.176');
  }

  function setCachedRootVariable(name,value){
    if(rootStyleCache.get(name)===value)return;
    rootStyleCache.set(name,value);
    root.style.setProperty(name,value);
  }

  function applyOffset(){
    if(!offsetDirty)return;
    setCachedRootVariable('--glass-x',`${offsetX}px`);
    setCachedRootVariable('--glass-y',`${offsetY}px`);
    offsetDirty=false;
  }

  function updateDesiredGeometry(value,target=geometryTarget){
    const bead=Math.min(ORB_MAX,Math.max(1,stageSize.width-18),Math.max(1,stageSize.height-18));
    const inputHeight=P.capsuleHeight;
    const inputWidth=Math.min(P.capsuleWidth,stageSize.width-34);
    const panelInset=nativeProduction?74:28;
    const panelVerticalInset=nativeProduction?58:30;
    const panelWidth=Math.min(500,stageSize.width-panelInset);
    const panelHeight=Math.min(stageSize.width<540?Math.max(P.panelHeight,360):P.panelHeight,stageSize.height-panelVerticalInset);
    if(value===0){
      target.width=bead;target.height=bead;target.topRadius=bead*.5;target.bottomRadius=bead*.5;target.anchorY=0;
    }else if(value===1){
      target.width=inputWidth;target.height=inputHeight;target.topRadius=inputHeight*.5;target.bottomRadius=inputHeight*.5;target.anchorY=0;
    }else{
      target.width=panelWidth;target.height=panelHeight;target.topRadius=P.panelTopRadius;target.bottomRadius=P.panelBottomRadius;target.anchorY=-(panelHeight-inputHeight)*.5;
    }
    return target;
  }

  function springProperty(values,velocities,key,target,omega,damping,delta){
    const nextVelocity=velocities[key]+(omega*omega*(target-values[key])-2*damping*omega*velocities[key])*delta;
    velocities[key]=nextVelocity;
    values[key]+=nextVelocity*delta;
  }

  function propertiesSettled(values,velocities,targets,keys,epsilon){
    for(let index=0;index<keys.length;index++){
      const key=keys[index];
      if(Math.abs(values[key]-targets[key])>=epsilon||Math.abs(velocities[key])>=epsilon)return false;
    }
    return true;
  }

  function ensureAnimationLoop(){
    if(animationFrameId||!pageActive||!stageInView)return;
    previousFrame=performance.now();
    animationFrameId=requestAnimationFrame(renderGeometry);
  }

  function pauseAnimationLoop(){
    if(!animationFrameId)return;
    cancelAnimationFrame(animationFrameId);
    animationFrameId=0;
  }

  function renderGeometry(now){
    animationFrameId=0;
    const delta=Math.min(.032,(now-previousFrame)/1000||.016);
    previousFrame=now;
    const target=updateDesiredGeometry(geometryForm);
    const speed=360/Math.max(160,morphDuration);
    const collapsingToOrb=form===0&&geometryForm===0;
    const geometrySpeed=speed*(collapsingToOrb?.88:1);
    const collapseDamping=collapsingToOrb?.32:0;
    springProperty(geometry,velocity,'width',target.width,P.widthFrequency*geometrySpeed,P.widthDamping+collapseDamping,delta);
    springProperty(geometry,velocity,'height',target.height,P.heightFrequency*geometrySpeed,P.heightDamping+collapseDamping,delta);
    springProperty(geometry,velocity,'topRadius',target.topRadius,P.topRadiusFrequency*geometrySpeed,P.topRadiusDamping+collapseDamping,delta);
    springProperty(geometry,velocity,'bottomRadius',target.bottomRadius,P.bottomRadiusFrequency*geometrySpeed,P.bottomRadiusDamping+collapseDamping,delta);
    springProperty(geometry,velocity,'anchorY',target.anchorY,P.anchorFrequency*geometrySpeed,P.anchorDamping+collapseDamping,delta);
    shellScaleVelocity+=(P.scaleFrequency*P.scaleFrequency*speed*speed*(targetScale-shellScale)-2*P.scaleDamping*P.scaleFrequency*speed*shellScaleVelocity)*delta;
    shellScale+=shellScaleVelocity*delta;
    for(const key of poseKeys){
      springProperty(pose,poseVelocity,key,poseTarget[key],P.poseFrequency*speed,P.poseDamping,delta);
    }
    const widthPx=`${Math.max(1,geometry.width)}px`;
    const heightPx=`${Math.max(1,geometry.height)}px`;
    const radiusPx=`${Math.max(0,geometry.topRadius)}px ${Math.max(0,geometry.topRadius)}px ${Math.max(0,geometry.bottomRadius)}px ${Math.max(0,geometry.bottomRadius)}px`;
    if(widthPx!==lastWidthPx){shell.style.width=widthPx;if(beadAura)beadAura.style.width=widthPx;lastWidthPx=widthPx;}
    if(heightPx!==lastHeightPx){shell.style.height=heightPx;if(beadAura)beadAura.style.height=heightPx;lastHeightPx=heightPx;}
    if(radiusPx!==lastRadiusPx){shell.style.borderRadius=radiusPx;if(beadAura)beadAura.style.borderRadius=radiusPx;lastRadiusPx=radiusPx;}
    const livingWave=
      Math.sin(now*.00069)*.58+
      Math.sin(now*.00031+1.7)*.27+
      Math.sin(now*.00017+4.2)*.15;
    const livingAmplitude=.002+.00886*Math.min(2,O.idleBreath/54);
    const bubbleBreath=geometryForm===0 ? 1+livingWave*livingAmplitude : 1;
    applyOffset();
    setCachedRootVariable('--anchor-y',`${geometry.anchorY}px`);
    setCachedRootVariable('--shell-scale',String(shellScale));
    setCachedRootVariable('--bubble-breath',String(bubbleBreath));
    setCachedRootVariable('--choreo-x',`${pose.x}px`);
    setCachedRootVariable('--choreo-y',`${pose.y}px`);
    setCachedRootVariable('--shell-skew',`${pose.skew}deg`);
    setCachedRootVariable('--stretch-x',String(pose.stretchX));
    setCachedRootVariable('--stretch-y',String(pose.stretchY));
    drawOptical(now);
    const epsilon=1e-5;
    const geometrySettled=propertiesSettled(geometry,velocity,target,geometryKeys,epsilon);
    const poseSettled=propertiesSettled(pose,poseVelocity,poseTarget,poseKeys,epsilon);
    const canSleep=form>0&&geometryForm===form&&!dragging&&root.dataset.phase==='idle'&&
      geometrySettled&&poseSettled&&Math.abs(shellScale-targetScale)<epsilon&&Math.abs(shellScaleVelocity)<epsilon&&
      Math.abs(opticalForm-(geometryForm===0?0:1))<epsilon&&Math.abs(opticalState)<epsilon;
    if(!canSleep&&pageActive&&stageInView)animationFrameId=requestAnimationFrame(renderGeometry);
  }

  function updateSelection(){
    shell.setAttribute('aria-pressed',String(form>0));
    shell.setAttribute('role',form===0?'button':'group');
    shell.setAttribute('aria-label',form===0?'可拖动玻璃浮窗；点击展开对话':'GUI Plus 悬浮对话面板');
    chatCopy.setAttribute('aria-hidden',String(form!==2));
    if(form!==2)closeQuickPanels();
    detail.textContent=labels[form];
    root.querySelectorAll('.form-btn').forEach(button=>{
      const selected=Number(button.dataset.form)===form;
      button.classList.toggle('is-selected',selected);
      button.setAttribute('aria-pressed',String(selected));
    });
  }
