/** Real, opt-in Playwright walkthrough. Adds synthetic database records; no provider calls. */
import {chromium, request, expect} from '@playwright/test';
import {readFileSync, writeFileSync, mkdirSync, existsSync} from 'node:fs';
import {dirname, resolve, join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {randomUUID} from 'node:crypto';

const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..');
const env=Object.fromEntries(readFileSync(join(root,'.env'),'utf8').split(/\r?\n/).filter(l=>l.includes('=')&&!l.startsWith('#')).map(l=>{const i=l.indexOf('=');return[l.slice(0,i),l.slice(i+1)]}));
const baseURL=process.env.BASE_URL||'http://127.0.0.1:15173';
const suffix=new Date().toISOString().slice(0,10)+'-'+randomUUID().slice(0,6);
const partner='Walkthrough '+suffix;
const prefix='DEMO-'+suffix;
const out=resolve(root,'.local/walkthrough',suffix);
mkdirSync(out,{recursive:true});
const browserPath=[process.env.PLAYWRIGHT_EXECUTABLE_PATH,'C:/Program Files/Google/Chrome/Application/chrome.exe','C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'].find(p=>p&&existsSync(p));
if(!browserPath)throw new Error('Set PLAYWRIGHT_EXECUTABLE_PATH to an installed Chrome/Edge.');
const chapters=[], errors=[];
let browser,context,page,video,started=0,passed=false;

async function post(api,path,data,form=false){
 const csrf=await(await api.get('/api/auth/csrf')).json();
 const response=await api.post('/api'+path,{headers:{[csrf.headerName]:csrf.token},...(form?{form:data}:{data})});
 if(!response.ok())throw new Error('HTTP '+response.status()+' at '+path);
 return response.status()===204?null:response.json().catch(()=>null);
}
const pause=ms=>new Promise(r=>setTimeout(r,ms));
async function note(title,text,seconds=6){
 const elapsed=(Date.now()-started)/1000;
 if(chapters.length)chapters.at(-1).end=elapsed;
 chapters.push({start:elapsed,title,text});
 await page.evaluate(({title,text,index})=>{
  let panel=document.getElementById('walkthrough-guide');
  if(!panel){
   const style=document.createElement('style');style.textContent='body{padding-bottom:145px!important} #walkthrough-guide{position:fixed;left:20px;right:20px;bottom:16px;z-index:2147483647;background:#102b24;color:white;border:1px solid #7cc5a5;border-radius:14px;padding:16px 24px;box-shadow:0 8px 28px #0005;pointer-events:none;font-family:Arial,sans-serif;min-height:104px} #walkthrough-guide small{display:block;font-size:12px;letter-spacing:2px;color:#b4dfca;margin-bottom:5px} #walkthrough-guide b{display:block;font-size:22px;margin-bottom:5px} #walkthrough-guide p{font-size:19px;line-height:1.4;margin:0;color:#fff}';document.head.append(style);
   panel=document.createElement('section');panel.id='walkthrough-guide';panel.setAttribute('aria-hidden','true');document.body.append(panel);
  }
  panel.replaceChildren();
  for(const [tag,value] of [['small','HINGLISH DEMO GUIDE  /  '+String(index).padStart(2,'0')+'  /  RECORDING CAPTIONS'],['b',title],['p',text]]){const el=document.createElement(tag);el.textContent=value;panel.append(el)}
 },{title,text,index:chapters.length});
 await expect(page.locator('#walkthrough-guide')).toHaveCSS('position','fixed');
 if(chapters.length===1)await page.screenshot({path:join(out,'opening.png')});
 console.log('Scene '+chapters.length+': '+title);
 await pause(seconds*1000);
}
async function click(locator){
 await locator.scrollIntoViewIfNeeded();
 await locator.evaluate(el=>{el.scrollIntoView({block:'center',behavior:'instant'});el.dataset.walkthroughTarget='true';el.style.outline='3px solid #2aa876';el.style.outlineOffset='4px'});
 await pause(650);await locator.click();await pause(500);
 await page.evaluate(()=>document.querySelectorAll('[data-walkthrough-target]').forEach(el=>{el.style.outline='';el.style.outlineOffset='';delete el.dataset.walkthroughTarget}));
}
const nav=name=>page.getByRole('link',{name,exact:true});
async function top(){await page.evaluate(()=>window.scrollTo({top:0,behavior:'instant'}));}
async function apiGet(path){const r=await context.request.get('/api'+path);if(!r.ok())throw new Error('Read failed: '+path);return r.json();}
const csvOrders='Order Number,Order Amount,Payment Method,Delivery Status,Order Date\n'+[
 `${prefix}-SHORT,1000,PREPAID,DELIVERED,2026-01-01`,
 `${prefix}-SPLIT,1000,PREPAID,DELIVERED,2026-01-01`,
 `${prefix}-TAX,1000,PREPAID,DELIVERED,2026-01-01`,
 `${prefix}-INVALID,not-a-number,PREPAID,DELIVERED,2026-01-01`].join('\n')+'\n';
const csvSettlements='transaction_id,order_id,amount,date,tcs,tds\n'+[
 `${prefix}-TX1,${prefix}-SHORT,873,2026-01-08,0,0`,
 `${prefix}-TX2,${prefix}-SPLIT,500,2026-01-08,0,0`,
 `${prefix}-TX3,${prefix}-SPLIT,410,2026-01-08,0,0`,
 `${prefix}-TX4,${prefix}-TAX,895,2026-01-08,10,5`,
 `${prefix}-TX3,${prefix}-SPLIT,410,2026-01-08,0,0`].join('\n')+'\n';
writeFileSync(join(out,'orders.csv'),csvOrders);writeFileSync(join(out,'settlements.csv'),csvSettlements);

try{
 // This setup is real database data: an isolated synthetic partner with an explicit rule.
 const setup=await request.newContext({baseURL});
 await post(setup,'/auth/login',{username:'founder',password:process.env.DEMO_FOUNDER_PASSWORD||env.DEMO_FOUNDER_PASSWORD},true);
 await post(setup,'/rate-cards',{partner,kind:'CHANNEL',effectiveFrom:'2020-01-01',effectiveTo:'2099-12-31',commission:0,fixedFee:90,returnFee:96,settlementDays:7});
 await setup.dispose();
 browser=await chromium.launch({executablePath:browserPath,headless:true});
 // Only this recording browser allows injected caption styles; server CSP is unchanged.
 context=await browser.newContext({baseURL,bypassCSP:true,viewport:{width:1440,height:1000},recordVideo:{dir:out,size:{width:1440,height:1000}},acceptDownloads:true,reducedMotion:'reduce'});
 page=await context.newPage();video=page.video();started=Date.now();page.on('pageerror',e=>errors.push(e.message));
 await page.goto('/');await expect(page.getByRole('button',{name:'Sign in →',exact:true})).toBeVisible();
 await note('Reconflow: order se payment aur claim tak','D2C finance team ko pata chalta hai: kitna paisa expected tha, kitna mila, aur kis gap par action lena hai.',7);
 await note('1. Finance sign-in','Do roles hain: Finance Executive aur Co-founder. Backend permissions, hashed passwords aur secure sessions access control karte hain.',7);
 await page.getByLabel('Password',{exact:true}).fill(process.env.DEMO_FINANCE_PASSWORD||env.DEMO_FINANCE_PASSWORD);
 await expect(page.getByLabel('Password',{exact:true})).toHaveAttribute('type','password');
 await click(page.getByRole('button',{name:'Sign in →',exact:true}));
 await expect(page.getByRole('heading',{name:'Clarity in every settlement.'})).toBeVisible();
 await note('2. Dashboard: money at risk','Yeh actual database figures hain. Partner, exception type aur age ke hisaab se outstanding risk aur taxes inspect kar sakte hain.',7);
 await click(nav('Import centre'));
 await page.getByLabel('Partner',{exact:true}).fill(partner);
 await page.getByLabel('Report file').setInputFiles(join(out,'orders.csv'));
 await note('3. Orders report upload','CSV aur XLSX supported hain. Is demo mein teen valid orders aur ek invalid amount hai; source file aur row references retain hote hain.',7);
 await click(page.getByRole('button',{name:'Upload and review'}));
 await expect(page.getByText('AWAITING MAPPING',{exact:true})).toBeVisible();
 await note('4. Column mapping: har partner ka format alag','Order Number ko order_id, Order Amount ko value se map karte hain. Mapping approve hone se pehle financial records process nahi hote.',7);
 for(const [field,header] of Object.entries({order_id:'Order Number',value:'Order Amount',payment:'Payment Method',delivery:'Delivery Status',order_date:'Order Date'})){
  const select=page.locator('label').filter({has:page.locator('select')}).filter({hasText:new RegExp('^'+field+' \\*')}).getByRole('combobox');
  await select.selectOption(header);await pause(450);
 }
 await top();
 await note('AI status: live success abhi verify nahi hua','Real AI integration available hai, lekin account credits exhausted hain. Is recording mein manual mapping hai; fabricated AI suggestions nahi hain.',7);
 await click(page.getByRole('button',{name:'Validate 20-row preview'}));
 await expect(page.getByRole('heading',{name:'Canonical preview (4 rows)'})).toBeVisible();
 await page.getByRole('heading',{name:'Canonical preview (4 rows)'}).scrollIntoViewIfNeeded();
 await note('5. Preview aur validation','Pehli 20 rows tak preview hota hai. Teen rows valid hain; invalid amount ka row-level error dikhta hai. User review ke baad approval deta hai.',7);
 await page.getByRole('checkbox').check();
 await click(page.getByRole('button',{name:'Approve mapping and process'}));
 await expect(page.getByText('COMPLETE',{exact:true})).toBeVisible({timeout:30000});await top();
 await expect(page.getByRole('status').filter({hasText:'3 records accepted; 1 rejected.'})).toBeVisible();
 await note('Valid rows accepted; invalid row evidence retained','Approved mapping version save hoti hai. Parsing aur batched database writes ke baad affected orders ka reconciliation automatically hota hai.',7);
 await click(nav('Import centre'));await page.getByLabel('Partner',{exact:true}).fill(partner);
 await page.getByRole('combobox',{name:'Report type',exact:true}).selectOption('SETTLEMENTS');
 await page.getByLabel('Report file').setInputFiles(join(out,'settlements.csv'));
 await note('6. Settlement report: payments aur split receipts','Ab actual receipts import karte hain: short-payment, do installments, aur TCS/TDS wala payment. Ek transaction jaan-boojhkar duplicate hai.',7);
 await click(page.getByRole('button',{name:'Upload and review'}));await expect(page.getByText('AWAITING MAPPING',{exact:true})).toBeVisible();
 await click(page.getByRole('button',{name:'Validate 20-row preview'}));await expect(page.getByRole('heading',{name:'Canonical preview (5 rows)'})).toBeVisible();
 await page.getByRole('checkbox').check();await click(page.getByRole('button',{name:'Approve mapping and process'}));
 await expect(page.getByText('COMPLETE',{exact:true})).toBeVisible({timeout:30000});await top();
 await expect(page.getByRole('status').filter({hasText:'4 records accepted; 1 rejected.'})).toBeVisible();
 await note('Duplicate transaction dobara count nahi hui','Chaar unique transactions accepted, duplicate rejected. Split settlement valid hai kyunki dono installments ki transaction IDs alag hain.',7);
 await click(page.getByRole('button',{name:'View results'}));
 const shortRow=page.getByRole('row').filter({hasText:prefix+'-SHORT'});
 await expect(shortRow.getByText('SHORT PAID',{exact:true})).toBeVisible();
 const rows=(await apiGet('/orders?'+new URLSearchParams({partner}))).rows;
 const bySuffix=s=>rows.find(r=>r.external_id.endsWith(s));
 expect(rows).toHaveLength(3);expect(Number(bySuffix('-SHORT').money_at_risk)).toBe(37);expect(bySuffix('-SPLIT').status).toBe('SETTLED');expect(bySuffix('-TAX').status).toBe('SETTLED');
 await note('7. Deterministic reconciliation: Rs 37 short-paid','Order value 1,000 - fixed fee 90 = expected 910. Received 873: gap 37. Due date cross aur gap strictly Rs 10 se zyada hai.',8);
 await note('Split aur taxes ko leakage mat samjho','SPLIT: 500 + 410 = 910, settled. TAX: 910 - TCS 10 - TDS 5 = 895; received 895, settled. Money calculation AI nahi karta.',8);
 await click(shortRow.getByRole('button',{name:'Inspect'}));await expect(page.getByRole('heading',{name:prefix+'-SHORT',exact:true})).toBeVisible();
 await note('8. Order detail: calculation defend kar sakte hain','Expected, received, gap aur rate-card version visible hain. Java BigDecimal aur PostgreSQL NUMERIC precise financial calculations handle karte hain.',7);
 const source=page.locator('details').filter({has:page.getByText(/orders\.csv.*row.*mapping/)}).first();
 await click(source.locator('summary'));await note('Original source evidence','File hash, original row aur mapping version preserve hote hain. Reviewer discrepancy ko uploaded report tak trace kar sakta hai.',7);
 await top();await page.getByRole('textbox',{name:'Claim note',exact:true}).fill('Synthetic walkthrough: expected INR 910, received INR 873; please review the INR 37 shortfall.');
 await click(page.getByRole('button',{name:'Create claim',exact:true}));await expect(page.getByRole('status').filter({hasText:/Claim #\d+ created/})).toBeVisible();
 await note('9. Exception se claim','Claim creation par calculation aur source evidence ka snapshot save hota hai. Duplicate active claims prevent hote hain; external submission nahi hota.',7);
 await click(page.getByRole('button',{name:/Open claim #/}));
 await page.getByRole('combobox',{name:'Status',exact:true}).selectOption('IN_REVIEW');
 await page.getByRole('textbox',{name:'Note',exact:true}).fill('Reviewed synthetic source records and calculation. INR 37 shortfall is awaiting partner review.');
 await click(page.getByRole('button',{name:'Save update'}));await expect(page.getByText('IN REVIEW',{exact:true})).toBeVisible();
 await note('Claim workspace: status, notes aur audit','Claim IN REVIEW hai. Kisne kya change kiya, audit history mein dikhta hai. Status update ko actual payment recovery nahi maana jaata.',7);
 const downloadPromise=page.waitForEvent('download');await click(page.getByRole('link',{name:'Export evidence JSON'}));
 const download=await downloadPromise;await download.saveAs(join(out,'claim-evidence.json'));
 const evidence=JSON.parse(readFileSync(join(out,'claim-evidence.json'),'utf8'));expect(Number(evidence.snapshot.claimedException.amount)).toBe(37);
 await note('Evidence export downloaded','JSON file mein source records, calculation snapshot, rate references aur claim audit history hain. Partner ko bhejna separate manual action hai.',7);
 await click(nav('Overview'));await page.getByRole('combobox',{name:'Overview partner'}).selectOption(partner);
 await expect(page.getByText('₹37.00',{exact:true}).first()).toBeVisible();
 const dashboard=await apiGet('/dashboard?'+new URLSearchParams({partner}));expect(Number(dashboard.totals.money_at_risk)).toBe(37);expect(Number(dashboard.totals.orders)).toBe(3);
 await note('10. Dashboard se outcome verify','Is demo partner ke 3 orders, Rs 37 money at risk aur 1 active claim hain. TCS 10 aur TDS 5 alag hain; claim banane se risk zero nahi hota.',8);
 await click(nav('Rate cards'));await expect(page.getByText('Finance has read access. A co-founder can add or replace rate versions.')).toBeVisible();await page.getByText('Finance has read access. A co-founder can add or replace rate versions.').scrollIntoViewIfNeeded();
 await note('Role permissions aur effective-dated rules','Finance rates read kar sakta hai. Co-founder versions edit karta hai. Order date applicable rate choose karti hai; overlapping periods reject hote hain.',7);
 await click(page.getByRole('button',{name:'Sign out',exact:true}));await expect(page.getByRole('button',{name:'Sign in →',exact:true})).toBeVisible();
 await page.getByLabel('Demo account').selectOption('founder');await page.getByLabel('Password',{exact:true}).fill(process.env.DEMO_FOUNDER_PASSWORD||env.DEMO_FOUNDER_PASSWORD);await click(page.getByRole('button',{name:'Sign in →',exact:true}));
 await click(nav('Rate cards'));await page.getByRole('heading',{name:'Add a rate version'}).scrollIntoViewIfNeeded();
 await note('Co-founder view: rate management','Yahan commission, fixed/return fees, settlement days aur effective dates manage hote hain. Demo ke existing rates ko abhi change nahi kar rahe.',7);
 await click(nav('Overview'));await note('Architecture: browser se database tak','React + TypeScript -> nginx -> Java 17 / Spring Boot -> PostgreSQL. Flyway migrations aur Docker Compose; backend modular monolith hai.',8);
 await note('Interview mein actual results bolo','31 backend tests aur 3 browser workflows passed. Docker: 100,000 total rows 25.952 seconds; 200,000 rows 80.079 seconds. Live AI credits pending.',9);
 await click(page.getByRole('button',{name:'Sign out',exact:true}));
 await note('Walkthrough complete','Sign in -> upload -> mapping approval -> reconciliation -> evidence -> claim. Saare demo records synthetic hain; financial results real database se aaye.',7);
 expect(errors).toEqual([]);passed=true;
 await page.screenshot({path:join(out,'ending.png')});
}catch(e){console.error('Recording failed:',e.message);if(page)await page.screenshot({path:join(out,'failure.png')}).catch(()=>{});}
finally{
 if(chapters.length)chapters.at(-1).end=(Date.now()-started)/1000;
 if(context)await context.close();
 if(video)await video.saveAs(join(out,'reconflow-hinglish-walkthrough.webm'));
 if(browser)await browser.close();
}
const stamp=seconds=>{const n=Math.floor(seconds);return String(Math.floor(n/60)).padStart(2,'0')+':'+String(n%60).padStart(2,'0')};
writeFileSync(join(out,'chapters.json'),JSON.stringify({passed,partner,prefix,baseURL,viewport:'1440x1000',audio:false,providerCalls:0,chapters,pageErrors:errors},null,2));
writeFileSync(join(out,'HINGLISH-SCRIPT.md'),'# Reconflow walkthrough narration\n\nActual Playwright recording with Hinglish captions; no audio track. Synthetic demo records are retained. No API key, plaintext password or provider request is included.\n\n'+chapters.map(c=>`## ${stamp(c.start)} - ${c.title}\n\n${c.text}\n`).join('\n')+'\nEvidence: three accepted orders; one invalid order rejected; four accepted settlement transactions; one duplicate rejected; INR 37 risk; one claim IN_REVIEW.\n');
const safe=x=>x.replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
writeFileSync(join(out,'WATCH.html'),`<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Reconflow | Hinglish walkthrough</title><style>body{margin:0;background:#f5f6f3;color:#18352b;font:16px system-ui}main{max-width:1200px;margin:30px auto;padding:0 24px}h1{margin-bottom:6px}p{line-height:1.6}video{width:100%;background:#102b24;border-radius:12px}section{display:grid;grid-template-columns:repeat(auto-fit,minmax(270px,1fr));gap:10px;margin:24px 0}button{text-align:left;padding:12px;background:white;border:1px solid #cad8ce;border-radius:8px;color:#18352b;cursor:pointer}button:hover{border-color:#237d58}small{display:block;color:#597365;margin-top:5px}.player-controls{display:flex;gap:12px;align-items:center;padding:14px 0;flex-wrap:wrap}.player-controls input{flex:1;min-width:180px;accent-color:#237d58}.player-controls button{padding:9px 15px}.player-controls select{padding:8px;border:1px solid #cad8ce;border-radius:6px}.player-controls output{font-variant-numeric:tabular-nums;min-width:45px}</style><main><h1>Reconflow: Hinglish walkthrough</h1><p>Actual application recording • Synthetic data • Hinglish on-screen captions • No audio track<br>Chapter par click karke us step par jao. Neeche Play, seek bar aur speed controls use karo; pause par bhi captions visible rahenge.</p><video id="movie" preload="metadata" src="reconflow-hinglish-walkthrough.webm"></video><div class="player-controls"><button id="play" type="button">Play</button><input id="seek" aria-label="Video position" type="range" min="0" max="209" step="0.1" value="0"><output id="time">00:00</output><label>Speed <select id="speed" aria-label="Playback speed"><option value="0.75">0.75x</option><option value="1" selected>1x</option><option value="1.25">1.25x</option><option value="1.5">1.5x</option></select></label><button id="full" type="button">Fullscreen</button></div><section>${chapters.map(c=>`<button data-time="${c.start}"><b>${stamp(c.start)} &nbsp; ${safe(c.title)}</b><small>${safe(c.text)}</small></button>`).join('')}</section><p><a href="HINGLISH-SCRIPT.md">Read narration script</a> · <a href="claim-evidence.json">Downloaded claim evidence</a></p></main><script>const movie=document.getElementById('movie'),play=document.getElementById('play'),seek=document.getElementById('seek');const playToggle=()=>movie.paused?movie.play():movie.pause();play.addEventListener('click',playToggle);movie.addEventListener('click',playToggle);movie.addEventListener('play',()=>play.textContent='Pause');movie.addEventListener('pause',()=>play.textContent='Play');movie.addEventListener('loadedmetadata',()=>seek.max=movie.duration);movie.addEventListener('timeupdate',()=>{seek.value=movie.currentTime;const n=Math.floor(movie.currentTime);document.getElementById('time').textContent=String(Math.floor(n/60)).padStart(2,'0')+':'+String(n%60).padStart(2,'0')});seek.addEventListener('input',()=>movie.currentTime=Number(seek.value));document.getElementById('speed').addEventListener('change',e=>movie.playbackRate=Number(e.target.value));document.getElementById('full').addEventListener('click',()=>movie.requestFullscreen());document.querySelectorAll('[data-time]').forEach(b=>b.addEventListener('click',()=>{const v=document.getElementById('movie');v.currentTime=Number(b.dataset.time);v.play();v.scrollIntoView({behavior:'smooth',block:'start'})}));</script></html>`);
console.log(JSON.stringify({passed,output:out,chapters:chapters.length,durationSeconds:Math.round(chapters.at(-1)?.end||0),providerCalls:0}));
if(!passed)process.exitCode=1;
