/** Local playback/seek verification; no application or provider mutations. */
import {chromium} from '@playwright/test';
import {readFileSync,writeFileSync,existsSync} from 'node:fs';
import {resolve,join} from 'node:path';
import {pathToFileURL} from 'node:url';

const folder=resolve(process.argv[2]||'');
const data=JSON.parse(readFileSync(join(folder,'chapters.json'),'utf8'));
if(!data.passed)throw new Error('Recording did not complete successfully.');
const executablePath=[process.env.PLAYWRIGHT_EXECUTABLE_PATH,'C:/Program Files/Google/Chrome/Application/chrome.exe','C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'].find(p=>p&&existsSync(p));
const browser=await chromium.launch({executablePath,headless:true});
try{
 const page=await browser.newPage({viewport:{width:1500,height:1200}});
 await page.goto(pathToFileURL(join(folder,'WATCH.html')).href);
 await page.waitForFunction(()=>Number.isFinite(document.querySelector('video')?.duration));
 const metadata=await page.locator('video').evaluate(v=>({durationSeconds:v.duration,width:v.videoWidth,height:v.videoHeight,error:v.error?.message??null}));
 if(metadata.error||metadata.width!==1440||metadata.height!==1000||metadata.durationSeconds<data.chapters.at(-1).end-3)throw new Error('Video metadata is incomplete.');
 const captures=[];
 for(const [name,match] of [['preview-mapping.png','Preview aur validation'],['preview-results.png','Deterministic reconciliation'],['preview-claim.png','Claim workspace']]){
  const chapter=data.chapters.find(c=>c.title.includes(match));
  await page.locator('video').evaluate((v,t)=>new Promise((done,reject)=>{v.pause();v.addEventListener('seeked',()=>done(),{once:true});v.addEventListener('error',()=>reject(new Error('Video decoding failed')),{once:true});v.currentTime=t}),chapter.start+3);
  await page.waitForFunction(()=>document.querySelector('video').readyState>=2);
  await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
  await page.locator('video').screenshot({path:join(folder,name)});
  captures.push({file:name,time:chapter.start+3});
 }
 const chapterButton=page.locator('[data-time]').filter({hasText:'Order detail: calculation defend'});
 await chapterButton.click();
 const playing=await page.locator('video').evaluate(v=>!v.paused);
 if(!playing)throw new Error('Chapter playback button did not start video.');
 await page.getByRole('button',{name:'Pause',exact:true}).click();
 if(!await page.locator('video').evaluate(v=>v.paused))throw new Error('Pause control failed.');
 await page.getByLabel('Playback speed').selectOption('1.25');
 if(await page.locator('video').evaluate(v=>v.playbackRate)!==1.25)throw new Error('Speed control failed.');
 await page.getByRole('button',{name:'Play',exact:true}).click();
 if(await page.locator('video').evaluate(v=>v.paused))throw new Error('Play control failed.');
 const summary={passed:true,...metadata,chapterButtons:await page.locator('[data-time]').count(),chapterPlaybackPassed:playing,playPauseSpeedPassed:true,captures};
 writeFileSync(join(folder,'playback-verification.json'),JSON.stringify(summary,null,2));
 console.log(JSON.stringify(summary,null,2));
}finally{await browser.close()}
