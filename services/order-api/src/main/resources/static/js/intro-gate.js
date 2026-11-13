// POLYMER — decide ANTES de pintar si se mostrará la intro, para que no se vea un destello de la página.
// Script clásico y síncrono en <head> (permitido por la CSP: script-src 'self'). Solo marca la clase;
// la animación la hace intro.js. Si por cualquier motivo la intro no arranca, la página se muestra igual.
(function () {
  var html = document.documentElement;
  // Transiciones entre páginas (View Transitions API, declaradas en polymer.css): si el navegador
  // cancela una (recarga, navegación programática), su promesa rechazada no debe llegar a la consola.
  // Mientras se muestra la intro no hay transición: el telón ya cumple ese papel.
  function quiet(event) {
    var vt = event.viewTransition;
    if (!vt) return;
    [vt.ready, vt.finished, vt.updateCallbackDone].forEach(function (p) { if (p && p.catch) p.catch(function () {}); });
    if (event.type === 'pagereveal' && html.classList.contains('intro-pending')) vt.skipTransition();
  }
  window.addEventListener('pageswap', quiet);
  window.addEventListener('pagereveal', quiet);
  // Algunas cancelaciones las rechaza el navegador antes de exponer la transición: solo esas se silencian.
  window.addEventListener('unhandledrejection', function (event) {
    var reason = event.reason;
    if (reason && /Transition was (aborted|skipped)/.test(String(reason.message || reason))) event.preventDefault();
  });
  try {
    var path = window.location.pathname;
    var params = new URLSearchParams(window.location.search);
    var internal = /^\/(admin|operaciones|logistica|monitoreo|arquitectura|cliente)\//.test(path) || path === '/laboratorio.html';
    var reduced = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    var seen = false;
    try { seen = window.sessionStorage.getItem('polymer.intro.seen') === '1'; } catch (e) { seen = true; }
    if (params.get('intro') === '1') seen = false; // reproducir a demanda (pie de página)
    if (internal || seen || reduced || params.get('intro') === '0') return;
    html.classList.add('intro-pending');
    // Red de seguridad: nunca bloquear la navegación si intro.js no llega a ejecutarse.
    window.setTimeout(function () {
      if (!html.classList.contains('intro-running')) html.classList.remove('intro-pending');
    }, 2500);
  } catch (e) { /* sin intro */ }
}());
