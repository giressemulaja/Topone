/* Écran de connexion commun aux deux outils.
   Le mot de passe n'est pas stocké en clair : seule son empreinte (SHA-256 répété 20 000 fois, avec sel) est ici.
   Pour changer l'identifiant ou le mot de passe, recalculez AUTH_HASH (voir README) puis relancez build.py. */
window.CabineAuth = (() => {
  const SALT = "cabine-jetons-v1";
  const ROUNDS = 20000;
  const AUTH_HASH = "cc033ef738699541490480ec640154f0fd220e01a0e447165ac3c837381a58cc";
  const SESSION_KEY = "cabine-auth";
  const FAIL_KEY = "cabine-auth-fails";
  const IDLE_MS = 15 * 60 * 1000; // verrouillage après 15 min sans activité

  const hex = (b) => Array.from(b, (x) => x.toString(16).padStart(2, "0")).join("");
  function digest(user, pass) {
    let h = `${SALT}|${user}|${pass}`;
    for (let i = 0; i < ROUNDS; i++) h = hex(window.CabineCore.sha256(SALT + h));
    return h;
  }
  const store = {
    get(k) { try { return sessionStorage.getItem(k); } catch (e) { return null; } },
    set(k, v) { try { sessionStorage.setItem(k, v); } catch (e) {} },
    del(k) { try { sessionStorage.removeItem(k); } catch (e) {} },
  };
  const lget = (k) => { try { return JSON.parse(localStorage.getItem(k) || "null"); } catch (e) { return null; } };
  const lset = (k, v) => { try { localStorage.setItem(k, JSON.stringify(v)); } catch (e) {} };

  let onUnlock = null, idleTimer = 0, appName = "";

  function css() {
    if (document.getElementById("gate-css")) return;
    const st = document.createElement("style"); st.id = "gate-css";
    st.textContent = `
      html.locked body > :not(.gate) { display: none !important; }
      .gate { position: fixed; inset: 0; z-index: 1000; background: var(--bg); color: var(--fg); display: grid; place-items: center;
        padding: max(16px, env(safe-area-inset-top, 0px)) max(16px, env(safe-area-inset-right, 0px)) max(16px, env(safe-area-inset-bottom, 0px)) max(16px, env(safe-area-inset-left, 0px)); overflow-y: auto; }
      .gate form { width: 100%; max-width: 360px; background: var(--panel); border: 1px solid var(--line); border-radius: 14px; padding: 22px; display: grid; gap: 14px; }
      .gate .lock { width: 44px; height: 44px; border-radius: 12px; background: var(--accent); color: var(--accent-fg); display: grid; place-items: center; }
      .gate .lock svg { width: 24px; height: 24px; }
      .gate h2 { margin: 0; font-size: 1.25rem; }
      .gate p { margin: 0; color: var(--muted); font-size: .9rem; }
      .gate label { display: grid; gap: 4px; font-size: .75rem; font-weight: 700; letter-spacing: .06em; text-transform: uppercase; color: var(--muted); }
      .gate input { font-family: inherit; font-weight: 600; font-size: 1.05rem; color: var(--fg); background: var(--bg); border: 1px solid var(--line); border-radius: 8px; padding: 11px 12px; width: 100%; text-transform: none; letter-spacing: normal; }
      .gate .pw { position: relative; }
      .gate .pw button { position: absolute; right: 6px; top: 50%; transform: translateY(-50%); background: none; border: 0; color: var(--accent); font-family: inherit; font-weight: 600; font-size: .8rem; cursor: pointer; padding: 6px; }
      .gate .go { font-family: inherit; font-weight: 700; font-size: 1rem; border-radius: 10px; padding: 13px; border: 0; background: var(--accent); color: var(--accent-fg); cursor: pointer; }
      .gate .go:disabled { opacity: .55; cursor: progress; }
      .gate .cr { text-align: center; font-size: .75rem; line-height: 1.5; }
      .gate .err { color: var(--bad, #b3261e); font-weight: 600; font-size: .9rem; min-height: 1.2em; }
      .gate input:focus-visible, .gate button:focus-visible { outline: 3px solid var(--accent); outline-offset: 2px; }`;
    document.head.appendChild(st);
  }

  function lock(reason) {
    store.del(SESSION_KEY);
    document.documentElement.classList.add("locked");
    css();
    document.querySelector(".gate")?.remove();
    const g = document.createElement("div"); g.className = "gate";
    g.innerHTML = `<form autocomplete="off" novalidate>
      <div class="lock"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="4" y="10" width="16" height="11" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/></svg></div>
      <div><h2>${appName}</h2><p>${reason || "Connectez-vous pour continuer."}</p></div>
      <label for="g-user">Identifiant<input id="g-user" inputmode="numeric" autocomplete="username" autocapitalize="off" spellcheck="false" required></label>
      <label for="g-pass">Mot de passe<span class="pw"><input id="g-pass" type="password" autocomplete="current-password" required><button type="button" id="g-eye">Afficher</button></span></label>
      <div class="err" id="g-err" role="alert"></div>
      <button class="go" type="submit" id="g-go">Se connecter</button>
      <p class="cr">© 2026 Giresse Mulaja · Tél. +243 810 000 421<br>giressemulaja8@gmail.com</p>
    </form>`;
    document.body.appendChild(g);
    const user = g.querySelector("#g-user"), pass = g.querySelector("#g-pass"), err = g.querySelector("#g-err"), go = g.querySelector("#g-go");
    g.querySelector("#g-eye").onclick = (e) => {
      const show = pass.type === "password"; pass.type = show ? "text" : "password"; e.target.textContent = show ? "Masquer" : "Afficher";
    };
    setTimeout(() => user.focus(), 50);
    g.querySelector("form").addEventListener("submit", (e) => {
      e.preventDefault();
      const f = lget(FAIL_KEY) || { n: 0, until: 0 };
      if (Date.now() < f.until) { err.textContent = `Trop d'essais. Réessayez dans ${Math.ceil((f.until - Date.now()) / 1000)} s.`; return; }
      if (!user.value.trim() || !pass.value) { err.textContent = "Entrez l'identifiant et le mot de passe."; return; }
      go.disabled = true; go.textContent = "Vérification…"; err.textContent = "";
      setTimeout(() => {
        const ok = digest(user.value.trim().replace(/\s+/g, ""), pass.value) === AUTH_HASH;
        go.disabled = false; go.textContent = "Se connecter";
        if (ok) { lset(FAIL_KEY, { n: 0, until: 0 }); unlock(); return; }
        f.n += 1;
        if (f.n >= 5) { f.until = Date.now() + 60 * 1000 * Math.min(10, f.n - 4); }
        lset(FAIL_KEY, f);
        pass.value = "";
        err.textContent = f.until > Date.now() ? "Trop d'essais. Connexion bloquée pendant quelques minutes." : "Identifiant ou mot de passe incorrect.";
      }, 30);
    });
  }

  function touch() {
    if (document.documentElement.classList.contains("locked")) return;
    store.set(SESSION_KEY, String(Date.now() + IDLE_MS));
    clearTimeout(idleTimer);
    idleTimer = setTimeout(() => lock("Session verrouillée après 15 minutes d'inactivité."), IDLE_MS);
  }

  function unlock() {
    document.querySelector(".gate")?.remove();
    document.documentElement.classList.remove("locked");
    touch();
    if (onUnlock) { const f = onUnlock; onUnlock = null; f(); }
  }

  ["pointerdown", "keydown", "touchstart"].forEach((ev) => document.addEventListener(ev, touch, { passive: true }));
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible" && !document.documentElement.classList.contains("locked")) {
      const exp = parseInt(store.get(SESSION_KEY) || "0", 10);
      if (Date.now() > exp) lock("Session verrouillée après 15 minutes d'inactivité.");
    }
  });

  return {
    // Appelle start() une seule fois, après connexion réussie (ou tout de suite si la session est encore valide).
    require(name, start) {
      appName = name; onUnlock = start;
      const exp = parseInt(store.get(SESSION_KEY) || "0", 10);
      if (Date.now() < exp) unlock(); else lock();
    },
    logout() { clearTimeout(idleTimer); lock("Vous êtes déconnecté."); },
  };
})();
