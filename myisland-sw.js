/* 我的小島：離線用的背景服務程式（Service Worker）
   - 網頁本身：先上網拿最新版，沒網路才用手機裡存的那份
   - 3D 引擎、字型、圖示：存一份在手機裡，下次直接用
   - version.json：永遠上網拿，不存 */
const CACHE='my-island-v2';
const CORE=['myisland.html','myisland.webmanifest','icons/island-192.png','icons/island-512.png','icons/apple-touch-icon.png'];
const CDN=['https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js','https://cdn.jsdelivr.net/npm/three@0.128.0/examples/js/controls/OrbitControls.js'];
self.addEventListener('install',e=>{e.waitUntil(caches.open(CACHE).then(c=>Promise.allSettled(CORE.map(u=>c.add(u)).concat(CDN.map(u=>c.add(new Request(u,{mode:'cors'})))))).then(()=>self.skipWaiting()))});
self.addEventListener('activate',e=>{e.waitUntil(caches.keys().then(ks=>Promise.all(ks.filter(k=>k.startsWith('my-island-')&&k!==CACHE).map(k=>caches.delete(k)))).then(()=>self.clients.claim()))});
self.addEventListener('fetch',e=>{const r=e.request;if(r.method!=='GET')return;const u=new URL(r.url);
  if(u.pathname.endsWith('/version.json'))return;
  if(u.origin===location.origin&&u.pathname.endsWith('/myisland.html')){
    e.respondWith(fetch(r).then(res=>{if(res.ok){const cp=res.clone();caches.open(CACHE).then(c=>c.put('myisland.html',cp))}return res}).catch(()=>caches.match('myisland.html',{ignoreSearch:true})));return}
  const cdn=u.hostname==='cdnjs.cloudflare.com'||u.hostname==='cdn.jsdelivr.net'||u.hostname==='fonts.googleapis.com'||u.hostname==='fonts.gstatic.com';
  if(cdn||u.origin===location.origin){
    e.respondWith(caches.match(r,{ignoreSearch:!cdn}).then(hit=>{const net=fetch(r).then(res=>{if(res&&(res.ok||res.type==='opaque')){const cp=res.clone();caches.open(CACHE).then(c=>c.put(r,cp))}return res}).catch(()=>hit);return hit||net}))}});
