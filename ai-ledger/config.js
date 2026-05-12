window.AI_LEDGER_CONFIG = {
  aiEndpoint: "https://ai-ledger-parser.yuchenm1303.workers.dev",
  aiTimeoutMs: 30000,
  supabaseUrl: "https://nfzkphjbelyltrzgkdwt.supabase.co",
  supabasePublishableKey: "sb_publishable_tE8SeTOj-ERgmqvP4l5Hiw_arCxCJLa"
};

(() => {
  const script = document.createElement('script');
  script.src = './assistant-profile.js?v=20260512-1';
  script.defer = true;
  document.head.appendChild(script);
})();
