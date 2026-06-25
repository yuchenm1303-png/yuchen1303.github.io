const defaults = Object.freeze({
  state: 'running',
  asymmetry: 38,
  tilt: -11,
  pinch: 62,
  thickness: 155,
  glow: 100,
  speed: 110,
  trail: 8
});

const stateNames = {
  off: '关闭',
  standby: '待命',
  running: '执行中',
  paused: '接管暂停',
  error: '异常脉冲'
};

const stateSpeed = {
  off: 0,
  standby: 0.42,
  running: 1,
  paused: 0,
  error: 0.2
};

const model = { ...defaults };
const instances = [];

function cloneSwitch(mount) {
  const fragment = document.querySelector('#switchTemplate').content.cloneNode(true);
  const button = fragment.querySelector('.agent-switch');
  mount.appendChild(fragment);
  const svg = button.querySelector('.infinity-svg');
  const instanceId = `agentOptics${instances.length + 1}`;
  const idMap = new Map();
  svg.querySelectorAll('[id]').forEach(node => {
    const oldId = node.id;
    const newId = `${oldId}-${instanceId}`;
    idMap.set(oldId, newId);
    node.id = newId;
  });
  svg.querySelectorAll('*').forEach(node => {
    for (const attribute of [...node.attributes]) {
      let value = attribute.value;
      idMap.forEach((newId, oldId) => {
        value = value.replaceAll(`url(#${oldId})`, `url(#${newId})`);
        value = value.replaceAll(`#${oldId}`, `#${newId}`);
      });
      if (value !== attribute.value) node.setAttribute(attribute.name, value);
    }
  });
  const instance = {
    button,
    svg,
    paths: [...button.querySelectorAll('.orbit, .infinity-layer')],
    motionPath: button.querySelector('.motion-path'),
    optics: button.querySelector('.optics-group'),
    primaryHead: button.querySelector('.comet-head-primary'),
    secondaryHead: button.querySelector('.comet-head-secondary'),
    primaryTrail: button.querySelector('.comet-trail-primary'),
    secondaryTrail: button.querySelector('.comet-trail-secondary'),
    centerStar: button.querySelector('.center-star'),
    rays: {
      h: button.querySelector('.ray-h'),
      v: button.querySelector('.ray-v'),
      d1: button.querySelector('.ray-d1'),
      d2: button.querySelector('.ray-d2')
    },
    phase: Math.random() * 0.08
  };
  button.addEventListener('click', () => {
    setState(model.state === 'off' ? 'standby' : 'off');
  });
  instances.push(instance);
  return instance;
}

cloneSwitch(document.querySelector('#liveSwitchMount'));
cloneSwitch(document.querySelector('#actualSwitchMount'));
cloneSwitch(document.querySelector('#inspectionSwitchMount'));

function buildPath() {
  const a = model.asymmetry / 100;
  const p = model.pinch / 100;

  const centerX = 120;
  const centerY = 50;
  const leftOuterX = 43 + a * 5;
  const rightOuterX = 194 + a * 14;
  const leftTopY = 25 + a * 4;
  const leftBottomY = 72 + a * 8;
  const rightTopY = 19 - a * 10;
  const rightBottomY = 74 + a * 3;
  const crossUpperY = centerY - (8 + p * 4);
  const crossLowerY = centerY + (2 + p * 6);
  const leftControlX = 92 - a * 8;

  return [
    `M ${centerX} ${centerY}`,
    `C ${leftControlX} ${28 - a * 4}, ${72 - a * 6} ${13 + a * 4}, ${leftOuterX} ${leftTopY}`,
    `C ${18 - a * 5} ${34 + a * 3}, ${20 + a * 3} ${62 + a * 4}, ${47 + a * 4} ${leftBottomY}`,
    `C ${73 + a * 8} ${84 + a * 2}, ${102 - a * 4} ${61 + p * 3}, ${centerX} ${crossUpperY}`,
    `C ${142 + a * 4} ${28 - p * 3}, ${163 + a * 7} ${8 - a * 4}, ${rightOuterX} ${rightTopY}`,
    `C ${225 + a * 4} ${30 - a * 2}, ${224 + a * 7} ${62 + a * 2}, ${194 + a * 9} ${rightBottomY}`,
    `C ${164 + a * 5} ${86 + a * 3}, ${141 + a * 4} ${67 + p * 2}, ${centerX} ${crossLowerY}`,
    `C ${116 - p * 2} ${55 + p}, ${118 - p} ${52 + p * 0.4}, ${centerX} ${centerY}`
  ].join(' ');
}

function buildOrbitPath(index) {
  const a = model.asymmetry / 100;
  if (index === 0) {
    return `M 48 ${62 + a * 4} C 70 ${16 - a * 3}, 178 ${6 - a * 7}, ${205 + a * 4} ${43 - a * 5} C 184 ${75 + a * 3}, 82 ${86 + a * 4}, 48 ${62 + a * 4}`;
  }
  return `M ${54 - a * 3} ${31 + a * 2} C 101 ${85 + a * 6}, 176 ${88 + a * 3}, ${203 + a * 5} ${49 - a * 2} C 162 ${21 - a * 5}, 87 ${7 + a * 4}, ${54 - a * 3} ${31 + a * 2}`;
}

function updateGeometry() {
  const pathData = buildPath();
  instances.forEach(instance => {
    const orbitPaths = instance.paths.filter(path => path.classList.contains('orbit'));
    orbitPaths.forEach((path, index) => path.setAttribute('d', buildOrbitPath(index)));
    instance.paths.filter(path => !path.classList.contains('orbit')).forEach(path => path.setAttribute('d', pathData));
    instance.optics.style.transform = `rotate(${model.tilt}deg)`;
    const core = instance.button.querySelector('.energy-core');
    const body = instance.button.querySelector('.glass-body');
    const mid = instance.button.querySelector('.halo-mid');
    core.style.strokeWidth = (model.thickness / 100).toFixed(2);
    body.style.strokeWidth = (model.thickness / 100 * 1.92).toFixed(2);
    mid.style.strokeWidth = (model.thickness / 100 * 2.64).toFixed(2);
  });
}

function createTrail(group, count, scale = 1) {
  group.textContent = '';
  for (let i = 0; i < count; i += 1) {
    const circle = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
    circle.setAttribute('r', Math.max(0.42, (1.9 - i * 0.12) * scale).toFixed(2));
    circle.style.opacity = Math.max(0.02, 0.5 * (1 - i / count)).toFixed(3);
    group.appendChild(circle);
  }
}

function updateTrails() {
  instances.forEach(instance => {
    createTrail(instance.primaryTrail, model.trail, 1);
    createTrail(instance.secondaryTrail, Math.max(3, model.trail - 2), 0.82);
  });
}

function setState(state) {
  model.state = state;
  document.querySelectorAll('.state-button').forEach(button => {
    button.classList.toggle('active', button.dataset.state === state);
  });
  document.querySelector('#stateLabel').textContent = stateNames[state];
  document.querySelector('.state-dot').style.background = state === 'paused' ? 'var(--warm)' : state === 'error' ? 'var(--danger)' : state === 'off' ? '#68728e' : 'var(--cyan)';
  document.querySelector('.state-dot').style.boxShadow = state === 'off' ? 'none' : '';
  instances.forEach(instance => {
    instance.button.className = `agent-switch state-${state}`;
    instance.button.setAttribute('aria-checked', String(state !== 'off'));
  });
  updateConfigBox();
}

function updateVisualStrength() {
  document.documentElement.style.setProperty('--glow-strength', model.glow / 100);
  instances.forEach(instance => {
    const blurLarge = instance.svg.querySelector('[id^="glowLarge-"] feGaussianBlur');
    const blurMedium = instance.svg.querySelector('[id^="glowMedium-"] feGaussianBlur');
    blurLarge.setAttribute('stdDeviation', (8.2 * model.glow / 100).toFixed(2));
    blurMedium.setAttribute('stdDeviation', (3.8 * model.glow / 100).toFixed(2));
  });
}

function updateConfigBox() {
  const config = {
    state: model.state,
    asymmetry: `${model.asymmetry}%`,
    tiltDegrees: model.tilt,
    centerPinch: `${model.pinch}%`,
    coreStrokePx: (model.thickness / 100).toFixed(2),
    glow: `${model.glow}%`,
    loopSpeed: `${(model.speed / 100).toFixed(2)}x`,
    trailSamples: model.trail
  };
  document.querySelector('#configBox').textContent = JSON.stringify(config, null, 2);
}

const controls = [
  ['asymmetry', value => `${value}%`, true],
  ['tilt', value => `${value < 0 ? '−' + Math.abs(value) : value}°`, true],
  ['pinch', value => `${value}%`, true],
  ['thickness', value => `${(value / 100).toFixed(2)} px`, true],
  ['glow', value => `${value}%`, false],
  ['speed', value => `${(value / 100).toFixed(2)}×`, false],
  ['trail', value => String(value), false]
];

controls.forEach(([id, formatter, geometry]) => {
  const input = document.querySelector(`#${id}`);
  const output = document.querySelector(`#${id}Value`);
  input.addEventListener('input', () => {
    model[id] = Number(input.value);
    output.textContent = formatter(model[id]);
    if (geometry) updateGeometry();
    if (id === 'trail') updateTrails();
    if (id === 'glow') updateVisualStrength();
    updateConfigBox();
  });
});

document.querySelectorAll('.state-button').forEach(button => {
  button.addEventListener('click', () => setState(button.dataset.state));
});

document.querySelector('#resetConfig').addEventListener('click', () => {
  Object.assign(model, defaults);
  controls.forEach(([id, formatter]) => {
    const input = document.querySelector(`#${id}`);
    input.value = model[id];
    document.querySelector(`#${id}Value`).textContent = formatter(model[id]);
  });
  updateGeometry();
  updateTrails();
  updateVisualStrength();
  setState(defaults.state);
});

document.querySelector('#copyConfig').addEventListener('click', async event => {
  const text = document.querySelector('#configBox').textContent;
  try {
    await navigator.clipboard.writeText(text);
    event.currentTarget.textContent = '已复制';
  } catch {
    event.currentTarget.textContent = '复制失败';
  }
  setTimeout(() => { event.currentTarget.textContent = '复制参数'; }, 1200);
});

function placeCircle(circle, point) {
  circle.setAttribute('cx', point.x.toFixed(2));
  circle.setAttribute('cy', point.y.toFixed(2));
}

function wrapped(value) {
  return ((value % 1) + 1) % 1;
}

function updateParticle(instance, progress, head, trailGroup, trailScale = 1) {
  const path = instance.motionPath;
  const length = path.getTotalLength();
  const headPoint = path.getPointAtLength(wrapped(progress) * length);
  placeCircle(head, headPoint);
  [...trailGroup.children].forEach((circle, index, list) => {
    const behind = wrapped(progress - (index + 1) * (0.011 + 0.0025 * trailScale));
    placeCircle(circle, path.getPointAtLength(behind * length));
    circle.style.opacity = Math.max(0.018, (0.48 - index / Math.max(1, list.length) * 0.44) * trailScale).toFixed(3);
  });
}

function updateStar(instance, pulse) {
  const cx = 120;
  const cy = 50;
  const ray = 6 + pulse * 15;
  const diag = 4 + pulse * 8;
  instance.centerStar.setAttribute('cx', cx);
  instance.centerStar.setAttribute('cy', cy);
  instance.centerStar.setAttribute('r', (1.1 + pulse * 1.9).toFixed(2));
  instance.rays.h.setAttribute('x1', cx - ray); instance.rays.h.setAttribute('x2', cx + ray);
  instance.rays.h.setAttribute('y1', cy); instance.rays.h.setAttribute('y2', cy);
  instance.rays.v.setAttribute('x1', cx); instance.rays.v.setAttribute('x2', cx);
  instance.rays.v.setAttribute('y1', cy - ray * 0.72); instance.rays.v.setAttribute('y2', cy + ray * 0.72);
  instance.rays.d1.setAttribute('x1', cx - diag); instance.rays.d1.setAttribute('x2', cx + diag);
  instance.rays.d1.setAttribute('y1', cy - diag); instance.rays.d1.setAttribute('y2', cy + diag);
  instance.rays.d2.setAttribute('x1', cx - diag); instance.rays.d2.setAttribute('x2', cx + diag);
  instance.rays.d2.setAttribute('y1', cy + diag); instance.rays.d2.setAttribute('y2', cy - diag);
  instance.button.style.setProperty('--pulse', pulse.toFixed(3));
}

let previous = performance.now();
let time = 0;
function frame(now) {
  const delta = Math.min(34, now - previous) / 1000;
  previous = now;
  time += delta;

  const baseSpeed = model.speed / 100;
  const stateMultiplier = stateSpeed[model.state];
  const phaseVelocity = 0.29 * baseSpeed * stateMultiplier;

  instances.forEach(instance => {
    if (model.state === 'off') {
      instance.primaryHead.style.opacity = 0;
      instance.secondaryHead.style.opacity = 0;
      instance.primaryTrail.style.opacity = 0;
      instance.secondaryTrail.style.opacity = 0;
      updateStar(instance, 0);
      return;
    }

    if (model.state === 'paused') {
      instance.primaryHead.style.opacity = 0.92;
      instance.secondaryHead.style.opacity = 0;
      instance.primaryTrail.style.opacity = 0.16;
      instance.secondaryTrail.style.opacity = 0;
      updateParticle(instance, 0.008, instance.primaryHead, instance.primaryTrail, 0.45);
      const pulse = 0.34 + 0.34 * (0.5 + 0.5 * Math.sin(time * 2.2));
      updateStar(instance, pulse);
      return;
    }

    if (model.state === 'error') {
      instance.phase = wrapped(instance.phase + delta * phaseVelocity);
      instance.primaryHead.style.opacity = 0.86;
      instance.secondaryHead.style.opacity = 0;
      instance.primaryTrail.style.opacity = 0.48;
      instance.secondaryTrail.style.opacity = 0;
      updateParticle(instance, instance.phase, instance.primaryHead, instance.primaryTrail, 0.7);
      const beat = Math.pow(Math.max(0, Math.sin(time * 2.7)), 10);
      updateStar(instance, 0.18 + beat * 0.9);
      return;
    }

    instance.phase = wrapped(instance.phase + delta * phaseVelocity);
    const secondaryVisible = model.state === 'running';
    instance.primaryHead.style.opacity = model.state === 'standby' ? 0.84 : 1;
    instance.secondaryHead.style.opacity = secondaryVisible ? 0.84 : 0;
    instance.primaryTrail.style.opacity = model.state === 'standby' ? 0.58 : 1;
    instance.secondaryTrail.style.opacity = secondaryVisible ? 0.72 : 0;
    updateParticle(instance, instance.phase, instance.primaryHead, instance.primaryTrail, model.state === 'standby' ? 0.7 : 1);
    if (secondaryVisible) updateParticle(instance, wrapped(instance.phase + 0.51), instance.secondaryHead, instance.secondaryTrail, 0.74);

    const crossing = Math.min(
      Math.abs(wrapped(instance.phase) - 0),
      Math.abs(wrapped(instance.phase) - 0.5),
      Math.abs(wrapped(instance.phase) - 1)
    );
    const pulse = Math.max(0.08, Math.exp(-Math.pow(crossing / 0.045, 2)) * (model.state === 'running' ? 0.96 : 0.5));
    updateStar(instance, pulse);
  });

  requestAnimationFrame(frame);
}

updateGeometry();
updateTrails();
updateVisualStrength();
setState(defaults.state);
requestAnimationFrame(frame);
