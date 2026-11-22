/* Noyau partagé : calcul du code et du motif d'un jeton. Doit rester identique dans les deux outils. */
window.CabineCore = (() => {
  // ---------- SHA-256 synchrone (fonctionne aussi hors ligne, en fichier local) ----------
  const K = new Int32Array([0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2]);
  // Toute l'arithmétique reste en entiers 32 bits signés, additions découpées en moitiés de 16 bits :
  // aucun nombre ne dépasse jamais 2^31, ce qui évite les conversions en virgule flottante
  // (certains moteurs JavaScript ou processeurs émulés les arrondissent mal).
  const add = (x, y) => { const l = (x & 0xffff) + (y & 0xffff); return (((x >> 16) + (y >> 16) + (l >> 16)) << 16) | (l & 0xffff); };
  const r = (x, n) => (x >>> n) | (x << (32 - n));
  function sha256(str) {
    const bytes = new TextEncoder().encode(str);
    const len = bytes.length;
    const total = ((len + 9 + 63) >> 6) << 6;
    const m = new Uint8Array(total); m.set(bytes); m[len] = 0x80;
    const dv = new DataView(m.buffer);
    dv.setInt32(total - 4, len << 3); dv.setInt32(total - 8, len >>> 29);
    const H = new Int32Array([0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19]);
    const W = new Int32Array(64);
    for (let o = 0; o < total; o += 64) {
      for (let i = 0; i < 16; i++) W[i] = dv.getInt32(o + i * 4);
      for (let i = 16; i < 64; i++) {
        const x = W[i - 15], y = W[i - 2];
        const s0 = r(x, 7) ^ r(x, 18) ^ (x >>> 3);
        const s1 = r(y, 17) ^ r(y, 19) ^ (y >>> 10);
        W[i] = add(add(W[i - 16], s0), add(W[i - 7], s1));
      }
      let a = H[0], b = H[1], c = H[2], d = H[3], e = H[4], f = H[5], g = H[6], h = H[7];
      for (let i = 0; i < 64; i++) {
        const t1 = add(add(add(h, r(e, 6) ^ r(e, 11) ^ r(e, 25)), add((e & f) ^ (~e & g), K[i])), W[i]);
        const t2 = add(r(a, 2) ^ r(a, 13) ^ r(a, 22), (a & b) ^ (a & c) ^ (b & c));
        h = g; g = f; f = e; e = add(d, t1); d = c; c = b; b = a; a = add(t1, t2);
      }
      H[0] = add(H[0], a); H[1] = add(H[1], b); H[2] = add(H[2], c); H[3] = add(H[3], d);
      H[4] = add(H[4], e); H[5] = add(H[5], f); H[6] = add(H[6], g); H[7] = add(H[7], h);
    }
    const out = new Uint8Array(32); const odv = new DataView(out.buffer);
    for (let i = 0; i < 8; i++) odv.setInt32(i * 4, H[i]);
    return out;
  }

  const ALPHA = "ACDEFHJKMNPRTUVWXY34679"; // sans 0/O, 1/I/L, 5/S, 8/B, 2/Z
  const SERIES = "ABCDEFGHJKLMNPRSTUVWXYZ".split("");
  const pad = (n) => String(n).padStart(4, "0");
  const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));

  function tokenData(secret, serie, num) {
    const h = sha256(`${secret}|${serie}|${num}`);
    let code = ""; for (let i = 0; i < 5; i++) code += ALPHA[h[i] % ALPHA.length];
    const bits = []; for (let i = 0; i < 15; i++) bits.push((h[8 + (i >> 3)] >> (i & 7)) & 1);
    return { code, bits, seed: h[20] / 255, seed2: h[21] / 255 };
  }


  // Motif 5×5 symétrique (comme une empreinte visuelle)
  function motifSVG(bits, ink) {
    let rects = "";
    for (let y = 0; y < 5; y++) for (let x = 0; x < 3; x++) {
      if (bits[y * 3 + x]) {
        rects += `<rect x="${x}" y="${y}" width="1" height="1"/>`;
        if (x < 2) rects += `<rect x="${4 - x}" y="${y}" width="1" height="1"/>`;
      }
    }
    return `<svg class="motif" viewBox="-0.7 -0.7 6.4 6.4" shape-rendering="crispEdges" aria-label="Motif de contrôle"><rect x="-0.65" y="-0.65" width="6.3" height="6.3" fill="#fff" stroke="${ink}" stroke-width=".1"/><g fill="${ink}">${rects}</g></svg>`;
  }
  // Enregistrer un fichier : dans l'application Android, passe par le pont natif (par morceaux) ; sinon, téléchargement du navigateur.
  async function saveFile(name, mime, blob) {
    const A = window.GMAndroid;
    if (A && A.beginFile) {
      const buf = new Uint8Array(await blob.arrayBuffer());
      const id = A.beginFile(name, mime);
      const CH = 384 * 1024;
      for (let i = 0; i < buf.length; i += CH) {
        let bin = ""; const part = buf.subarray(i, i + CH);
        for (let j = 0; j < part.length; j += 8192) bin += String.fromCharCode.apply(null, part.subarray(j, j + 8192));
        A.appendChunk(id, btoa(bin));
      }
      A.endFile(id);
      return;
    }
    const a = document.createElement("a"); a.href = URL.createObjectURL(blob); a.download = name;
    document.body.appendChild(a); a.click(); a.remove(); setTimeout(() => URL.revokeObjectURL(a.href), 4000);
  }
  function print() { if (window.GMAndroid && window.GMAndroid.print) window.GMAndroid.print(); else window.print(); }
  return { sha256, ALPHA, SERIES, pad, esc, tokenData, motifSVG, saveFile, print };
})();
