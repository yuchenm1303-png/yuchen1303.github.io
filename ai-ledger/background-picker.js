(() => {
  const BG_KEY = "ai-ledger-bg-v1";
  const backgrounds = new Set(["aurora", "jade", "sunset", "dawn"]);
  const body = document.body;
  const picker = document.querySelector("#backgroundPicker");
  const options = [...document.querySelectorAll(".bg-option")];

  function applyBackground(name) {
    const next = backgrounds.has(name) ? name : "aurora";
    body.dataset.bg = next;
    localStorage.setItem(BG_KEY, next);
    options.forEach((option) => option.classList.toggle("active", option.dataset.bg === next));
  }

  function updateShine() {
    const root = document.scrollingElement || document.documentElement;
    const max = Math.max(root.scrollHeight - window.innerHeight, 1);
    const progress = Math.min(Math.max(window.scrollY / max, 0), 1);
    const x = `${18 + progress * 64}%`;
    const y = `${2 + progress * 14}%`;
    body.style.setProperty("--shine-x", x);
    body.style.setProperty("--shine-y", y);
  }

  applyBackground(localStorage.getItem(BG_KEY) || "aurora");
  updateShine();

  options.forEach((option) => {
    option.addEventListener("click", () => applyBackground(option.dataset.bg));
  });

  window.addEventListener("scroll", updateShine, { passive: true });
  window.addEventListener("resize", updateShine);

  if (!picker) return;
})();
