(function () {
  "use strict";
  var root = document.documentElement;
  try { var s = localStorage.getItem("velo-theme"); if (s) root.setAttribute("data-theme", s); } catch (e) {}

  function toggleTheme() {
    var cur = root.getAttribute("data-theme");
    if (!cur) cur = window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
    var next = cur === "dark" ? "light" : "dark";
    root.setAttribute("data-theme", next);
    try { localStorage.setItem("velo-theme", next); } catch (e) {}
  }
  function openSidebar(open) {
    var sb = document.getElementById("sidebar"), sc = document.getElementById("scrim");
    if (sb) sb.classList.toggle("open", open);
    if (sc) sc.classList.toggle("show", open);
  }

  // Reveal the active chapter in the sidebar (it may sit below the fold).
  (function () {
    var act = document.querySelector(".side-nav a.active"), sb = document.getElementById("sidebar");
    if (!act || !sb) return;
    var r = act.getBoundingClientRect(), sr = sb.getBoundingClientRect();
    if (r.top < sr.top + 8 || r.bottom > sr.bottom - 8) {
      sb.scrollTop += (r.top - sr.top) - (sb.clientHeight - act.offsetHeight) / 2;
    }
  })();

  document.addEventListener("click", function (e) {
    if (e.target.closest(".theme-btn")) { toggleTheme(); return; }
    if (e.target.closest(".menu-btn")) { openSidebar(true); return; }
    if (e.target.id === "scrim") { openSidebar(false); return; }
    var copy = e.target.closest(".copy");
    if (copy) {
      var code = copy.parentElement.querySelector("code");
      if (code && navigator.clipboard) {
        navigator.clipboard.writeText(code.innerText);
        var old = copy.textContent; copy.textContent = "Copied";
        setTimeout(function () { copy.textContent = old; }, 1200);
      }
    }
  });

  // ---- Search --------------------------------------------------------------
  var q = document.getElementById("q"), results = document.getElementById("results");
  var idx = window.VELO_SEARCH || [];
  function score(e, n) {
    var t = e.t.toLowerCase();
    if (t.indexOf(n) === 0) return 100;
    if (t.indexOf(n) >= 0) return 60;
    for (var i = 0; i < e.h.length; i++) if (e.h[i][0].toLowerCase().indexOf(n) >= 0) return 40;
    if ((e.x || "").toLowerCase().indexOf(n) >= 0) return 20;
    return 0;
  }
  function esc(x){ return x.replace(/[&<>]/g, function(c){ return {"&":"&amp;","<":"&lt;",">":"&gt;"}[c]; }); }
  function search() {
    var n = (q.value || "").trim().toLowerCase();
    if (n.length < 2) { results.classList.remove("show"); results.innerHTML = ""; return; }
    var hits = [];
    for (var i = 0; i < idx.length; i++) { var sc = score(idx[i], n); if (sc > 0) hits.push({ e: idx[i], s: sc }); }
    hits.sort(function (a, b) { return b.s - a.s; });
    if (!hits.length) { results.innerHTML = '<div class="no-hits">No matches.</div>'; results.classList.add("show"); return; }
    var out = "";
    hits.slice(0, 8).forEach(function (h) {
      var e = h.e, sub = e.s;
      for (var j = 0; j < e.h.length; j++) if (e.h[j][0].toLowerCase().indexOf(n) >= 0) { sub = e.h[j][0]; break; }
      out += '<a class="hit" href="' + e.u + '"><span class="hit-title">' + esc(e.t) +
             '</span><span class="hit-sub">' + esc(sub) + '</span></a>';
    });
    results.innerHTML = out; results.classList.add("show");
  }
  if (q) {
    q.addEventListener("input", search);
    q.addEventListener("focus", search);
    q.addEventListener("keydown", function (e) { if (e.key === "Escape") { results.classList.remove("show"); q.blur(); } });
    document.addEventListener("click", function (e) { if (!e.target.closest(".search")) results.classList.remove("show"); });
    document.addEventListener("keydown", function (e) {
      if (e.key === "/" && document.activeElement !== q && !/^(INPUT|TEXTAREA)$/.test(document.activeElement.tagName)) { e.preventDefault(); q.focus(); }
    });
  }

  // ---- On-this-page scrollspy (position-based; no rAF so it works in any tab)
  var tocLinks = [].slice.call(document.querySelectorAll(".toc a"));
  if (tocLinks.length) {
    var map = {};
    tocLinks.forEach(function (a) { map[decodeURIComponent(a.getAttribute("href").slice(1))] = a; });
    var heads = [].slice.call(document.querySelectorAll(".content h2, .content h3")).filter(function (h) { return h.id && map[h.id]; });
    var offsets = [];
    function measure() { offsets = heads.map(function (h) { return h.getBoundingClientRect().top + window.scrollY; }); }
    function spy() {
      var y = window.scrollY + 100, idx = 0;
      for (var i = 0; i < offsets.length; i++) { if (offsets[i] <= y) idx = i; else break; }
      if (window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 4) idx = heads.length - 1;
      tocLinks.forEach(function (a) { a.classList.remove("active"); });
      if (heads[idx] && map[heads[idx].id]) map[heads[idx].id].classList.add("active");
    }
    measure(); spy();
    window.addEventListener("scroll", spy, { passive: true });
    window.addEventListener("resize", function () { measure(); spy(); });
    window.addEventListener("load", function () { measure(); spy(); });
  }
})();
