(() => {
  const root=document.documentElement;
  const region=new URLSearchParams(location.search).get('region')||'full';
  root.dataset.hudRegion=region;

  const delegate=window.VisualHud;
  if(!delegate||typeof delegate.update!=='function')return;

  let timingSignature='';

  function number(value,fallback){
    const parsed=Number(value);
    return Number.isFinite(parsed)?parsed:fallback;
  }

  function setPx(name,value){
    root.style.setProperty(name,`${number(value,0)}px`);
  }

  function syncEdgeTiming(parameters){
    if(!parameters||typeof parameters!=='object')return;
    const flow=Math.max(.01,number(parameters.edgeFlowDuration,7.5));
    const breath=Math.max(.01,number(parameters.edgeBreathDuration,1.5));
    const signature=`${flow}|${breath}`;
    if(signature===timingSignature)return;
    timingSignature=signature;
    const seconds=Date.now()/1000;
    root.style.setProperty('--edge-flow-phase',`${-(seconds%flow)}s`);
    root.style.setProperty('--edge-breath-phase',`${-(seconds%breath)}s`);
  }

  window.VisualHud={
    update(payload){
      const source=typeof payload==='string'?JSON.parse(payload):payload;
      const data={...source};
      const screenWidth=Math.max(1,number(data.screenWidth,innerWidth));
      const screenHeight=Math.max(1,number(data.screenHeight,innerHeight));
      const viewportX=number(data.viewportX,0);
      const viewportY=number(data.viewportY,0);
      const viewportWidth=Math.max(1,number(data.viewportWidth,innerWidth));
      const viewportHeight=Math.max(1,number(data.viewportHeight,innerHeight));

      setPx('--hud-screen-width',screenWidth);
      setPx('--hud-screen-height',screenHeight);
      setPx('--hud-viewport-x',viewportX);
      setPx('--hud-viewport-y',viewportY);
      syncEdgeTiming(data.parameters);

      if(region==='pointer'){
        const globalX=Math.max(0,Math.min(1,number(data.xNorm,.5)))*screenWidth;
        const globalY=Math.max(0,Math.min(1,number(data.yNorm,.5)))*screenHeight;
        data.xNorm=(globalX-viewportX)/viewportWidth;
        data.yNorm=(globalY-viewportY)/viewportHeight;
        setPx('--hud-bubble-x',number(data.bubbleX,globalX)-viewportX);
        setPx('--hud-bubble-y',number(data.bubbleY,globalY)-viewportY);
      }

      delegate.update(data);
    },
    hide(){
      delegate.hide();
    }
  };
})();
