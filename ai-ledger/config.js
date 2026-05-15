window.AI_LEDGER_CONFIG = {
  aiEndpoint: "https://ai-ledger-parser.552078638.workers.dev",
  aiTimeoutMs: 30000,
  supabaseUrl: "https://nfzkphjbelyltrzgkdwt.supabase.co",
  supabasePublishableKey: "sb_publishable_tE8SeTOj-ERgmqvP4l5Hiw_arCxCJLa"
};

(() => {
  const scripts = [
    './assistant-profile.js?v=20260515-3',
    './settings-preferences.js?v=20260515-1',
    './settings-appearance-plus.js?v=20260515-1',
    './navigation-preferences.js?v=20260515-1',
    './chat-source-badges.js?v=20260515-1',
    './tools-center.js?v=20260515-1'
  ];
  scripts.forEach((src) => {
    const script = document.createElement('script');
    script.src = src;
    script.defer = true;
    document.head.appendChild(script);
  });
})();