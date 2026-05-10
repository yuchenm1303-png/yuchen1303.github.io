(() => {
  const CHAT_KEY = "ai-ledger-chat-v2";
  const initialChat = [
    {
      id: "welcome",
      role: "assistant",
      content: "你好，我是你的 AI 记账助手。你可以直接和我说：今天午饭28；也可以问我：这个月餐饮花了多少。",
      action: "chat",
      records: [],
      draftState: "none",
    },
  ];

  function clearConversation() {
    const ok = window.confirm("确定清空当前对话吗？账单记录不会被删除。");
    if (!ok) return;
    localStorage.setItem(CHAT_KEY, JSON.stringify(initialChat));
    window.location.reload();
  }

  window.addEventListener("DOMContentLoaded", () => {
    document.querySelector("#clearChatInlineBtn")?.addEventListener("click", clearConversation);
  });
})();
