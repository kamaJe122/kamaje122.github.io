/* 我的小島：離線用的背景服務程式（Service Worker）
   - 網頁本身：先上網拿最新版，沒網路才用手機裡存的那份
   - 3D 引擎、字型、圖示：存一份在手機裡，下次直接用
   - version.json：永遠上網拿，不存 */
const CACHE='my-island-v3';
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

/* 新版本通知：Android／電腦的 Chrome 安裝版可以在背景大約每 12 小時檢查一次（iPhone 不支援背景檢查） */
function cmpVer(a,b){a=String(a).split('.').map(Number);b=String(b).split('.').map(Number);for(let i=0;i<3;i++){const d=(a[i]||0)-(b[i]||0);if(d)return d}return 0}
async function readMeta(){try{const c=await caches.open('my-island-meta'),r=await c.match('/__island_meta');return r?await r.json():null}catch(e){return null}}
async function checkAndNotify(){const meta=await readMeta();if(!meta||!meta.notif)return;
  const r=await fetch('version.json?t='+Date.now(),{cache:'no-store'});if(!r.ok)return;const v=await r.json();
  if(!v||!v.version||cmpVer(v.version,meta.app)<=0||meta.notified===v.version)return;
  meta.notified=v.version;const c=await caches.open('my-island-meta');await c.put('/__island_meta',new Response(JSON.stringify(meta),{headers:{'Content-Type':'application/json'}}));
  await self.registration.showNotification('🎉 我的小島有新版本 '+v.version,{body:(Array.isArray(v.notes)&&v.notes[0])||'打開小島就能更新到最新版。',tag:'island-update',icon:'icons/island-192.png',badge:'icons/island-192.png',data:{url:'./myisland.html?source=notify'}})}
self.addEventListener('periodicsync',e=>{if(e.tag==='island-version')e.waitUntil(checkAndNotify().catch(()=>{}))});
self.addEventListener('notificationclick',e=>{e.notification.close();const url=(e.notification.data&&e.notification.data.url)||'./myisland.html';
  e.waitUntil(self.clients.matchAll({type:'window',includeUncontrolled:true}).then(list=>{for(const c of list){if(c.url.includes('/myisland.html')&&'focus' in c)return c.focus()}return self.clients.openWindow(url)}))});
