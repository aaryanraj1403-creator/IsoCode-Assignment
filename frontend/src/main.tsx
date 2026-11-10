import React,{useEffect,useState} from 'react';
import {createRoot} from 'react-dom/client';
import {api,go,post,Row,useTask} from './api';
import {Notice} from './UI';
import Dashboard from './Dashboard';
import ImportCentre from './ImportCentre';
import MappingReview from './MappingReview';
import {ReconciliationView,OrderDetail} from './ReconciliationView';
import ClaimsWorkspace from './ClaimsWorkspace';
import RateCards from './RateCards';
import './style.css';

function App(){
 const[me,setMe]=useState<Row|null>(null),[checking,setChecking]=useState(true),[route,setRoute]=useState(window.location.hash.slice(1)||'dashboard'),task=useTask();
 useEffect(()=>{api('/me').then(setMe).catch(()=>{}).finally(()=>setChecking(false));const hash=()=>setRoute(window.location.hash.slice(1)||'dashboard');const expired=()=>setMe(null);window.addEventListener('hashchange',hash);window.addEventListener('session-expired',expired);return()=>{window.removeEventListener('hashchange',hash);window.removeEventListener('session-expired',expired)}},[]);
 if(checking)return <main className="login" role="status"><div className="brand">◈ reconflow</div><p>Opening your workspace…</p></main>;
 if(!me)return <SignIn onLogin={user=>{setMe(user);go('dashboard')}}/>;
 const[path,query='']=route.split('?'),[screen,id]=path.split('/'),founder=me.roles.includes('ROLE_FOUNDER');
 const nav=[['dashboard','Overview'],['imports','Import centre'],['orders','Reconciliation'],['claims','Claims'],['rates','Rate cards']];
 let view;
 switch(screen){
  case 'dashboard':view=<Dashboard/>;break;
  case 'imports':view=<ImportCentre/>;break;
  case 'mapping':view=id?<MappingReview key={id} id={id} founder={founder}/>:<ImportCentre/>;break;
  case 'orders':view=id?<OrderDetail key={id} id={id}/>:<ReconciliationView key={query} initialPartner={new URLSearchParams(query).get('partner')??''}/>;break;
  case 'claims':view=<ClaimsWorkspace key={id??query} id={id} initialPartner={new URLSearchParams(query).get('partner')??''}/>;break;
  case 'rates':view=<RateCards founder={founder}/>;break;
  default:view=<div className="panel"><h1>Page not found</h1><button onClick={()=>go('dashboard')}>Go to overview</button></div>;
 }
 return <div className="shell"><a className="skip" href="#main-content" onClick={e=>{e.preventDefault();document.getElementById('main-content')?.focus()}}>Skip to content</a><aside><a className="brand" href="#dashboard">◈ reconflow</a><p className="eyebrow">FINANCIAL OPERATIONS</p><nav aria-label="Main navigation">{nav.map(([key,title])=><a key={key} className={(screen===key || screen==='mapping'&&key==='imports')?'active':''} href={'#'+key}>{title}</a>)}</nav><div className="asideBottom"><b>Demo brand workspace</b><small>Synthetic records · India</small><button className="secondary" disabled={task.busy} onClick={()=>task.run(async()=>{await post('/auth/logout');setMe(null)})}>Sign out</button></div></aside><div className="content"><header><span>WORKSPACE / {screen.toUpperCase()}</span><span>{founder?'Co-founder':'Finance executive'}</span></header><main id="main-content" tabIndex={-1}><Notice {...task}/>{view}</main><footer>Reconflow · INR · Asia/Kolkata · Demo workspace</footer></div></div>
}
function SignIn({onLogin}:{onLogin:(user:Row)=>void}){
 const[user,setUser]=useState('finance'),[password,setPassword]=useState(''),task=useTask();
 return <main className="login"><div className="brand">◈ reconflow</div><p className="eyebrow">FINANCIAL OPERATIONS</p><h1>Every order.<br/>Every rupee.</h1><p className="muted">Trace settlements and uncover the differences.</p><form onSubmit={e=>{e.preventDefault();task.run(async()=>{await api('/auth/login',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({username:user,password})});setPassword('');onLogin(await api('/me'))})}}><label>Demo account<select value={user} onChange={e=>setUser(e.target.value)}><option value="finance">Finance executive</option><option value="founder">Co-founder</option></select></label><label>Password<input required type="password" autoComplete="current-password" value={password} onChange={e=>setPassword(e.target.value)}/></label><Notice {...task}/><button disabled={task.busy}>{task.busy?'Signing in…':'Sign in →'}</button></form><small>Use the demo password configured in your local environment.<br/>Reports and figures are stored in the connected database.</small></main>
}
createRoot(document.getElementById('root')!).render(<App/>);
