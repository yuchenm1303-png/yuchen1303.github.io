const assert = require("node:assert/strict");
const test = require("node:test");

const {
  buildVisualAgentDirectGuiMessages,
  extractVisualAgentMobileUseToolCalls,
  handleVisualAgentStepRequest,
  isVisualAgentStepRequest,
  mapMobileUseArgsToAgentStep,
} = require("../app/backend/aliyun-cn-server-web-data-v1.js");

const body = {
  action: "visual_agent_step",
  visualAgentDirect: true,
  goal: "open QQ",
  currentPackage: "com.yuchen.ailedger",
  recentActions: ["one", "two"],
  screenshot: {
    mimeType: "image/jpeg",
    base64Data: "ZmFrZQ==",
    width: 1080,
    height: 2400,
    displayWidth: 1080,
    displayHeight: 2400,
  },
};

function tool(args) {
  return `<tool_call>${JSON.stringify({ name: "mobile_use", arguments: args })}</tool_call>`;
}

test("visual_agent_step routing predicate", () => {
  assert.equal(isVisualAgentStepRequest({ action: "visual_agent_step" }), true);
  assert.equal(isVisualAgentStepRequest({ visualAgentDirect: true }), true);
  assert.equal(isVisualAgentStepRequest({ action: "agent_step" }), false);
});

test("direct GUI request uploads only current screenshot and bounded recent actions on first turn", () => {
  const messages = buildVisualAgentDirectGuiMessages("goal", "pkg", {
    mimeType: "image/jpeg",
    base64: "current",
  }, ["a", "b", "c", "d", "e", "f", "g"]);
  assert.equal(messages[0].role, "system");
  const content = messages[1].content;
  assert.equal(content.filter((part) => part.type === "image_url").length, 1);
  assert.match(content[1].image_url.url, /current$/);
  assert.match(content[0].text, /Recent actions: b \| c \| d \| e \| f \| g/);
});

test("direct GUI request follows official multi-turn visual history format", () => {
  const messages = buildVisualAgentDirectGuiMessages("goal", "pkg", {
    mimeType: "image/jpeg",
    base64: "current",
  }, ["last"], [{
    screenshot: {
      mimeType: "image/jpeg",
      base64Data: "history1",
      width: 100,
      height: 200,
    },
    assistantOutput: "Action: tap\n<tool_call>{\"name\":\"mobile_use\",\"arguments\":{\"action\":\"click\",\"coordinate\":[500,500]}}</tool_call>",
    executionResult: "tap_xy|ok",
  }]);
  assert.equal(messages.map((item) => item.role).join(","), "system,user,assistant,user");
  assert.match(messages[1].content[1].image_url.url, /history1$/);
  assert.match(messages[2].content, /mobile_use/);
  assert.match(messages[3].content[0].image_url.url, /current$/);
});

test("official mobile_use calls are extracted strictly", () => {
  const calls = extractVisualAgentMobileUseToolCalls(tool({ action: "click", coordinate: [500, 600] }));
  assert.equal(calls.length, 1);
  assert.equal(calls[0].action, "click");
});

test("mobile_use action mapping", () => {
  assert.deepEqual(mapMobileUseArgsToAgentStep({ action: "open", text: "QQ" }).type, "open_app");
  assert.equal(mapMobileUseArgsToAgentStep({ action: "open", text: "QQ" }).appName, "QQ");

  const click = mapMobileUseArgsToAgentStep({ action: "click", coordinate: [250, 750] });
  assert.equal(click.type, "tap_xy");
  assert.equal(click.x, 0.25);
  assert.equal(click.y, 0.75);

  const input = mapMobileUseArgsToAgentStep({ action: "type", text: "hello" });
  assert.equal(input.type, "input_text");
  assert.equal(input.requiresInputNode, false);

  assert.equal(mapMobileUseArgsToAgentStep({ action: "swipe", coordinate: [500, 800], coordinate2: [500, 200] }).direction, "up");
  assert.equal(mapMobileUseArgsToAgentStep({ action: "back" }).type, "back");
  assert.equal(mapMobileUseArgsToAgentStep({ action: "home" }).type, "home");
  assert.equal(mapMobileUseArgsToAgentStep({ action: "system_button", button: "Back" }).type, "back");
  assert.equal(mapMobileUseArgsToAgentStep({ action: "wait", time: 1 }).durationMs, 1000);
  assert.equal(mapMobileUseArgsToAgentStep({ action: "terminate", status: "success", text: "done" }).type, "finish");
  assert.equal(mapMobileUseArgsToAgentStep({ action: "terminate", status: "failure", text: "blocked" }).type, "need_user_help");
  assert.equal(mapMobileUseArgsToAgentStep({ action: "answer", text: "done" }).type, "need_user_help");
  assert.equal(mapMobileUseArgsToAgentStep({ action: "interact", text: "blocked" }).type, "need_user_help");
});

test("direct handler calls GUI Plus once and returns one action", async () => {
  let guiCalls = 0;
  const result = await handleVisualAgentStepRequest(body, body.goal, {
    callGuiPlus: async () => {
      guiCalls += 1;
      return tool({ action: "open", text: "QQ" });
    },
  });
  assert.equal(guiCalls, 1);
  assert.equal(result.ok, true);
  assert.equal(result.agentStep.type, "open_app");
  assert.equal(result.agentSteps.length, 1);
  assert.equal(result.debug.guiPlusCalls, 1);
  assert.equal(result.debug.visualCalled, true);
  assert.match(result.rawModelOutput, /mobile_use/);
});

test("direct handler verifies terminate success before finishing", async () => {
  const verified = await handleVisualAgentStepRequest(body, body.goal, {
    callGuiPlus: async () => tool({ action: "terminate", status: "success", text: "done" }),
    verifyCompletion: async () => ({ complete: true, reason: "target page is visible" }),
  });
  assert.equal(verified.agentStep.type, "finish");
  assert.equal(verified.debug.completionVerified, true);

  const rejected = await handleVisualAgentStepRequest(body, body.goal, {
    callGuiPlus: async () => tool({ action: "terminate", status: "success", text: "done" }),
    verifyCompletion: async () => ({ complete: false, reason: "wrong subpage" }),
  });
  assert.equal(rejected.agentStep.type, "need_user_help");
  assert.equal(rejected.debug.completionVerified, false);
});

test("direct handler safe-stops invalid and failed GUI results", async () => {
  const noTool = await handleVisualAgentStepRequest(body, body.goal, { callGuiPlus: async () => "no tool here" });
  assert.equal(noTool.agentStep.type, "need_user_help");

  const multi = await handleVisualAgentStepRequest(body, body.goal, {
    callGuiPlus: async () => tool({ action: "back" }) + "\n" + tool({ action: "home" }),
  });
  assert.equal(multi.agentStep.type, "need_user_help");

  const timeout = await handleVisualAgentStepRequest(body, body.goal, { callGuiPlus: async () => { throw new Error("timeout"); } });
  assert.equal(timeout.ok, true);
  assert.equal(timeout.agentStep.type, "need_user_help");

  const answer = await handleVisualAgentStepRequest(body, body.goal, { callGuiPlus: async () => tool({ action: "answer", text: "done" }) });
  assert.equal(answer.ok, true);
  assert.equal(answer.agentStep.type, "need_user_help");

  const invalid = await handleVisualAgentStepRequest({ goal: "x", visualAgentDirect: true }, "x", { callGuiPlus: async () => tool({ action: "back" }) });
  assert.equal(invalid.ok, false);
  assert.equal(invalid.code, "empty_screenshot");
});
