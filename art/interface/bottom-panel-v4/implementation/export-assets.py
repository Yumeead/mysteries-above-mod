"""Export the approved integer Canvas artwork as native GUI sprites; never edit liquid PNGs."""
from pathlib import Path
import base64
import html
import json
import re
import subprocess
import tempfile

root = Path(__file__).resolve().parents[4]
review = root / "art/interface/bottom-panel-v4/review.html"
source = review.read_text(encoding="utf-8")
payload = re.search(r'<script id="payload" type="application/json">(.*?)</script>', source, re.S)[1]
renderer = re.search(r'<script id="renderer">(.*?)</script>', source, re.S)[1].rsplit("start().catch", 1)[0]
export = r'''
const assets={},put=(name,c)=>assets[name]=c.toDataURL('image/png');
let c,g;
put('blank',cv(64,56));
for(let size=28;size>=8;size-=2){const outer=size+8,margin=(28-size)/2,inside=1+margin;c=cv(outer,outer);g=c.getContext('2d');for(let yy=0;yy<outer;yy++)for(let xx=0;xx<outer;xx++){const d=(2*xx-outer+1)**2+(2*yy-outer+1)**2;if(d>size*size&&d<=outer*outer)r(g,xx,yy,1,1,d>(outer-2)**2?C.ink:d>(outer-4)**2?G.dark:xx+yy<outer-13?G.light:G.mid)}
 for(const [xx,yy]of [[outer/2-1,1],[1,outer/2-1],[outer-3,outer/2-1],[outer/2-1,outer-3]]){r(g,xx,yy,2,2,G.dark);r(g,xx,yy,1,1,'#E5CD91')}put('orb_frame_'+size,c);
 const halo=size+20;c=cv(halo,halo);g=c.getContext('2d');
 for(let yy=0;yy<halo;yy++)for(let xx=0;xx<halo;xx++){const distance=Math.hypot(xx-(halo-1)/2,yy-(halo-1)/2);const fade=(distance-outer/2+1)/6;if(fade>=0&&fade<1){g.globalAlpha=.85*(1-fade)**2;r(g,xx,yy,1,1,'#FFFFFF')}}put('orb_glow_'+size,c);
 c=cv(size,size);disk(c.getContext('2d'),size/2,size/2,size,'#0C171D');put('orb_backing_'+size,c);
 c=cv(size,size);g=c.getContext('2d');for(const [u,v,alpha]of [[.23,.18,.2],[.2,.21,.36],[.18,.25,.48],[.18,.29,.38],[.16,.32,.25],[.86,.39,.35]]){const xx=Math.round(size*u),yy=Math.round(size*v);if((2*xx-size+1)**2+(2*yy-size+1)**2<=size*size){g.globalAlpha=alpha;r(g,xx,yy,1,1,'#9EB5BB')}}put('orb_glass_'+size,c)}
for(const id of ['Mother','Justiciar']){c=cv(28,28);g=c.getContext('2d');g.globalAlpha=.32;mark(g,17,17,id,C.glass);put('engraving_'+id.toLowerCase(),c)}
c=cv(182,22);g=c.getContext('2d');for(let n=0;n<9;n++)goldFrame(g,1+n*20,1,20,20);put('hotbar',c);
for(const [name,offset]of [['offhand_left',1],['offhand_right',8]]){c=cv(29,24);goldFrame(c.getContext('2d'),offset,2,20,20);put(name,c)}
for(const blink of [false,true]){const old=C.glass;C.glass=blink?'#E4EDEC':old;
 for(const [name,fill,half]of [['heart_empty',0,'height'],['heart_full',1,'height'],['heart_half_height',.5,'height'],['heart_half_left',.5,'left']]){c=cv(9,9);vessel(c.getContext('2d'),'heart',0,0,fill,half);put(name+(blink?'_blinking':''),c)}C.glass=old}
for(const [name,fill]of [['food_empty',0],['food_full',1],['food_half',.5]]){c=cv(9,9);foodIcon(c.getContext('2d'),0,0,fill);put(name,c)}
c=cv(182,5);goldFrame(c.getContext('2d'),0,0,182,5);put('experience_background',c);
c=cv(182,5);r(c.getContext('2d'),2,2,178,1,C.xp);put('experience_progress',c);
document.querySelector('#out').textContent=JSON.stringify(assets);
'''
with tempfile.TemporaryDirectory(prefix="mysteries-hud04-assets-") as temporary:
    temporary = Path(temporary)
    page = temporary / "assets.html"
    page.write_text('<!doctype html><pre id="out"></pre><script id="payload" type="application/json">' +
                    payload + '</script><script>' + renderer + export + '</script>', encoding="utf-8")
    result = subprocess.run([
        "C:/Program Files/Google/Chrome/Application/chrome.exe", "--headless", "--disable-gpu",
        "--no-first-run", "--no-sandbox", "--disable-extensions", "--disable-background-networking",
        "--user-data-dir=" + str(temporary / "profile"), "--dump-dom", page.as_uri(),
    ], capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=60)
    match = re.search(r'<pre id="out">(.*?)</pre>', result.stdout, re.S)
    if not match or not match[1]:
        raise RuntimeError("Asset export failed: " + result.stderr[-1000:])
    assets = json.loads(html.unescape(match[1]))
    target = root / "mod/src/client/resources/assets/mysteries_above/textures/gui/sprites/bottom_panel"
    target.mkdir(parents=True, exist_ok=True)
    for name, data in assets.items():
        (target / (name + ".png")).write_bytes(base64.b64decode(data.split(",", 1)[1]))
    print("Exported", len(assets), "native sprites from the approved Canvas art.")
