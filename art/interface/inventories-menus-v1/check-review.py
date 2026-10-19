"""Small offline browser check for the design gallery; no game or server starts."""
from pathlib import Path
from tempfile import TemporaryDirectory, NamedTemporaryFile
import json
import re
import subprocess

OUT = Path(__file__).resolve().parent
CHROME = Path('C:/Program Files/Google/Chrome/Application/chrome.exe')
CHECK = r'''
<pre id="browser-check"></pre><script>
(async()=>{
 const errors=[],checked=[];
 function assert(v,m){if(!v)errors.push(m)}
 async function loaded(src){await new Promise(resolve=>{const img=new Image();img.onload=()=>resolve();img.onerror=()=>{errors.push(src);resolve()};img.src=src});checked.push(src)}
 assert(window.REVIEW_READY,'Gallery failed to initialize');
 assert(document.querySelector('#screen').options.length===13,'Screen count');
 assert(DATA.screens.every(s=>!s.id.startsWith('B-')),'B mockups must be absent');
 for(const item of DATA.layers)await loaded('layers/'+item.screen+'-gs'+item.scale+'.png');
 for(const screen of DATA.screens)for(const bg of ['day','cave']){
  await loaded('details/'+screen.id+'-'+bg+'-x4.png');
  for(const scale of [2,3])await loaded('screens/'+screen.id+'-'+bg+'-gs'+scale+'.png');
 }
 for(const bg of ['day','cave'])await loaded('background-'+bg+'.png');
 document.querySelector('#screen').value='inventory';document.querySelector('#background').value='cave';document.querySelector('#scale').value='2';render();
 assert(document.querySelector('#ui').getAttribute('src')==='layers/inventory-gs2.png','Scale/background controls');
 document.querySelector('#next').click();assert(document.querySelector('#screen').value==='chest','Next control');
 document.querySelector('#screen').value='kit';document.querySelector('#scale').value='4';render();
 assert(document.querySelector('#warning').textContent.length>0,'Scale 4 fit warning');
 document.querySelector('#browser-check').textContent=JSON.stringify({passed:errors.length===0,errors,imagesChecked:checked.length,screenOptions:13});
})();
</script>
'''

if __name__ == '__main__':
    html = (OUT / 'review.html').read_text(encoding='utf-8')
    with NamedTemporaryFile(mode='w', suffix='.html', prefix='browser-check-', dir=OUT, encoding='utf-8', delete=False) as page:
        temporary_page = Path(page.name)
        page.write(html.replace('</html>', CHECK + '</html>'))
    try:
        with TemporaryDirectory(prefix='mysteries-menu-review-') as profile:
            result = subprocess.run([str(CHROME), '--headless', '--disable-gpu', '--no-first-run',
                '--no-sandbox', '--disable-extensions', '--disable-background-networking',
                '--user-data-dir=' + profile, '--virtual-time-budget=15000', '--dump-dom', temporary_page.as_uri()],
                capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=45)
            match = re.search(r'<pre id="browser-check">(.*?)</pre>', result.stdout, re.S)
            assert match and match[1], 'No browser result: ' + result.stderr[-600:]
            report = json.loads(match[1])
            assert report['passed'], report
            (OUT / 'browser-verification.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
            print(json.dumps(report))
    finally:
        temporary_page.unlink(missing_ok=True)
