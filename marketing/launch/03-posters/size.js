// Reads ?size=a3|a4 and ?prize=1|0 from the file URL so build.py can render
// every format from one source. Runs synchronously in <head>, before layout.
(function () {
  var q = new URLSearchParams(location.search);
  var size = (q.get('size') || 'a3').toLowerCase();
  var prize = q.get('prize');
  var root = document.documentElement;
  if (size === 'a4') { root.classList.add('a4'); }
  if (prize === '0') { root.classList.add('noprize'); }
  var page = size === 'a4' ? 'A4' : (size === 'a3' ? 'A3' : null);
  var css = page ? '@page { size: ' + page + ' portrait; margin: 0; }' : '';
  var st = document.createElement('style');
  st.textContent = css;
  document.head.appendChild(st);
})();
