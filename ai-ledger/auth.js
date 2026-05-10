import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const config = window.AI_LEDGER_CONFIG || {};
const supabaseUrl = config.supabaseUrl;
const supabasePublishableKey = config.supabasePublishableKey;
const appUrl = `${window.location.origin}${window.location.pathname}`;
const authClient = supabaseUrl && supabasePublishableKey
  ? createClient(supabaseUrl, supabasePublishableKey)
  : null;

const els = {
  authStatusText: document.querySelector("#authStatusText"),
  authStatusPill: document.querySelector("#authStatusPill"),
  authHint: document.querySelector("#authHint"),
  openAuthBtn: document.querySelector("#openAuthBtn"),
  logoutBtn: document.querySelector("#logoutBtn"),
  authOverlay: document.querySelector("#authOverlay"),
  closeAuthBtn: document.querySelector("#closeAuthBtn"),
  authTabs: document.querySelectorAll("[data-auth-mode]"),
  authForm: document.querySelector("#authForm"),
  authEmail: document.querySelector("#authEmail"),
  authPassword: document.querySelector("#authPassword"),
  authSubmitBtn: document.querySelector("#authSubmitBtn"),
  authMessage: document.querySelector("#authMessage"),
};

let authMode = "login";
let hasAutoOpenedAuth = false;

function setAuthMessage(message, tone = "normal") {
  if (!els.authMessage) return;
  els.authMessage.textContent = message;
  els.authMessage.dataset.tone = tone;
}

function setAuthLoading(isLoading) {
  if (!els.authSubmitBtn) return;
  els.authSubmitBtn.disabled = isLoading;
  els.authSubmitBtn.textContent = isLoading
    ? "处理中…"
    : authMode === "register"
      ? "注册"
      : "登录";
}

function setAuthMode(mode) {
  authMode = mode === "register" ? "register" : "login";
  els.authTabs.forEach((button) => button.classList.toggle("active", button.dataset.authMode === authMode));
  if (els.authSubmitBtn) els.authSubmitBtn.textContent = authMode === "register" ? "注册" : "登录";
  setAuthMessage(authMode === "register"
    ? "注册后如果开启了邮箱验证，需要先去邮箱点击确认链接。"
    : "用邮箱和密码登录。",
    "normal");
}

function openAuth(mode = "login") {
  if (!authClient) {
    setAuthState(null, "Supabase 尚未配置完整");
    return;
  }
  setAuthMode(mode);
  els.authOverlay?.classList.add("open");
  els.authOverlay?.setAttribute("aria-hidden", "false");
  window.setTimeout(() => els.authEmail?.focus(), 120);
}

function closeAuth() {
  els.authOverlay?.classList.remove("open");
  els.authOverlay?.setAttribute("aria-hidden", "true");
}

function maybeAutoOpenAuth(user) {
  if (user || hasAutoOpenedAuth) return;
  hasAutoOpenedAuth = true;
  window.setTimeout(() => openAuth("login"), 180);
}

function setAuthState(user, message = "") {
  const loggedIn = Boolean(user);
  if (els.authStatusText) {
    els.authStatusText.textContent = loggedIn ? user.email : "未登录";
  }
  if (els.authStatusPill) {
    els.authStatusPill.textContent = loggedIn ? "已登录" : "本地模式";
  }
  if (els.authHint) {
    els.authHint.textContent = loggedIn
      ? "账号已接通。账单会自动同步到云端。"
      : message || "未登录时仍可继续本地使用；登录后会自动开启云同步。";
  }
  if (els.openAuthBtn) els.openAuthBtn.hidden = loggedIn;
  if (els.logoutBtn) els.logoutBtn.hidden = !loggedIn;
  maybeAutoOpenAuth(user);
}

async function refreshSession() {
  if (!authClient) {
    setAuthState(null, "Supabase 未配置，当前只能本地使用。");
    return;
  }
  const { data, error } = await authClient.auth.getSession();
  if (error) {
    setAuthState(null, "读取登录状态失败，但仍可本地使用。");
    return;
  }
  setAuthState(data.session?.user || null);
}

function translateAuthError(error) {
  const message = String(error?.message || "");
  if (/Invalid login credentials/i.test(message)) return "邮箱或密码不对。";
  if (/Email not confirmed/i.test(message)) return "邮箱还没验证，请先去邮箱点确认链接。";
  if (/User already registered/i.test(message)) return "这个邮箱已经注册过了，直接登录就行。";
  if (/Password should be at least/i.test(message)) return "密码太短，请至少使用 6 位。";
  return message || "操作失败，请稍后再试。";
}

async function submitAuth(event) {
  event.preventDefault();
  if (!authClient) {
    setAuthMessage("Supabase 尚未配置完整。", "error");
    return;
  }

  const email = String(els.authEmail?.value || "").trim();
  const password = String(els.authPassword?.value || "");
  if (!email || !password) {
    setAuthMessage("邮箱和密码都要填写。", "error");
    return;
  }

  setAuthLoading(true);
  setAuthMessage(authMode === "register" ? "正在注册…" : "正在登录…", "normal");

  try {
    if (authMode === "register") {
      const { data, error } = await authClient.auth.signUp({
        email,
        password,
        options: {
          emailRedirectTo: appUrl,
        },
      });
      if (error) throw error;
      if (data.session) {
        setAuthMessage("注册成功，已直接登录。", "success");
        closeAuth();
      } else {
        setAuthMessage("注册成功，验证邮件已发送；请先去邮箱确认。", "success");
      }
    } else {
      const { error } = await authClient.auth.signInWithPassword({ email, password });
      if (error) throw error;
      setAuthMessage("登录成功。", "success");
      closeAuth();
    }
  } catch (error) {
    setAuthMessage(translateAuthError(error), "error");
  } finally {
    setAuthLoading(false);
  }
}

async function logout() {
  if (!authClient) return;
  const { error } = await authClient.auth.signOut();
  if (error) {
    if (window.showToast) window.showToast("退出失败，请重试");
    return;
  }
  hasAutoOpenedAuth = false;
  if (window.showToast) window.showToast("已退出登录");
  window.setTimeout(() => openAuth("login"), 180);
}

els.openAuthBtn?.addEventListener("click", () => openAuth("login"));
els.closeAuthBtn?.addEventListener("click", closeAuth);
els.authOverlay?.addEventListener("click", (event) => {
  if (event.target === els.authOverlay) closeAuth();
});
els.authTabs.forEach((button) => button.addEventListener("click", () => setAuthMode(button.dataset.authMode)));
els.authForm?.addEventListener("submit", submitAuth);
els.logoutBtn?.addEventListener("click", logout);

if (authClient) {
  authClient.auth.onAuthStateChange((_event, session) => {
    setAuthState(session?.user || null);
  });
}

refreshSession();
window.aiLedgerAuth = { openAuth, client: authClient };
