#!/usr/bin/env node

const { spawn } = require("child_process");

const port = Number(process.env.GUI_PROVIDER_PORT || 9100);
const apiKey = process.env.GUI_PROVIDER_API_KEY || "test-key";
const baseUrl = `http://127.0.0.1:${port}`;

function startProvider() {
  return spawn(process.execPath, ["scripts/gui-provider-mock.js"], {
    cwd: process.cwd(),
    env: {
      ...process.env,
      GUI_PROVIDER_PORT: String(port),
      GUI_PROVIDER_API_KEY: apiKey,
    },
    stdio: ["ignore", "pipe", "pipe"],
  });
}

async function waitForHealth(timeoutMs = 5000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      const response = await fetch(baseUrl);
      if (response.ok) return response.json();
    } catch {
      await new Promise((resolve) => setTimeout(resolve, 120));
    }
  }
  throw new Error("mock provider did not become healthy");
}

async function postPlan(goal) {
  const response = await fetch(baseUrl, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      authorization: `Bearer ${apiKey}`,
    },
    body: JSON.stringify({
      goal,
      screen: {
        currentApp: "微信",
        screenshot: {
          width: 1080,
          height: 2400,
        },
        texts: ["微信", "通讯录", "发现", "我"],
        clickableNodes: [
          { text: "微信", bounds: { left: 0, top: 2260, right: 270, bottom: 2340 }, clickable: true },
          { text: "通讯录", bounds: { left: 270, top: 2260, right: 540, bottom: 2340 }, clickable: true },
          { text: "发现", bounds: { left: 540, top: 2260, right: 810, bottom: 2340 }, clickable: true },
          { text: "我", bounds: { left: 810, top: 2260, right: 1080, bottom: 2340 }, clickable: true },
        ],
      },
    }),
  });

  if (!response.ok) throw new Error(`POST failed: ${response.status}`);
  return response.json();
}

function assertTapPlan(plan) {
  if (plan.a !== "tap_xy") throw new Error(`expected tap_xy, got ${plan.a}`);
  if (plan.t !== "通讯录") throw new Error(`expected target 通讯录, got ${plan.t}`);
  if (plan.x !== 0.375 || Number(plan.y.toFixed(4)) !== 0.9583) {
    throw new Error(`expected normalized x/y 0.375/0.9583, got ${plan.x}/${plan.y}`);
  }
  if (!Array.isArray(plan.b) || plan.b.some((value) => value < 0 || value > 1)) {
    throw new Error(`expected normalized b, got ${JSON.stringify(plan.b)}`);
  }
}

function assertNeedHelp(plan) {
  if (plan.a !== "need_user_help") throw new Error(`expected need_user_help, got ${plan.a}`);
  if ("x" in plan || "y" in plan || "b" in plan) {
    throw new Error(`high risk plan must not include coordinates: ${JSON.stringify(plan)}`);
  }
}

(async () => {
  const provider = startProvider();
  provider.stdout.on("data", (chunk) => process.stdout.write(chunk));
  provider.stderr.on("data", (chunk) => process.stderr.write(chunk));

  try {
    const health = await waitForHealth();
    const tapPlan = await postPlan("进入联系人");
    const otpPlan = await postPlan("发送验证码");
    const paymentPlan = await postPlan("转账付款");

    assertTapPlan(tapPlan);
    assertNeedHelp(otpPlan);
    assertNeedHelp(paymentPlan);

    console.log("health:", JSON.stringify(health));
    console.log("tap:", JSON.stringify(tapPlan));
    console.log("highRiskOtp:", JSON.stringify(otpPlan));
    console.log("highRiskPayment:", JSON.stringify(paymentPlan));
    console.log("GUI provider mock tests passed");
  } finally {
    provider.kill();
  }
})().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
