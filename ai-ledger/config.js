window.AI_LEDGER_CONFIG = {
  aiEndpoint: "https://ai-ledger-parser.yuchenm1303.workers.dev",
  aiTimeoutMs: 30000,
  supabaseUrl: "https://nfzkphjbelyltrzgkdwt.supabase.co",
  supabasePublishableKey: "sb_publishable_tE8SeTOj-ERgmqvP4l5Hiw_arCxCJLa"
};

(() => {
  const scripts = [
    './splash-screen.js?v=20260513-1',
    './assistant-profile.js?v=20260514-1',
    './tools-center.js?v=20260514-1'
  ];
  scripts.forEach((src) => {
    const script = document.createElement('script');
    script.src = src;
    script.defer = true;
    document.head.appendChild(script);
  });
})();
