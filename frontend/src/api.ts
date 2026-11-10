import {useEffect,useState} from 'react';

export type Row = Record<string, any>;
export type Page = {rows: Row[]; total:number};
export const money=(n:unknown)=>new Intl.NumberFormat('en-IN',{style:'currency',currency:'INR',minimumFractionDigits:2,maximumFractionDigits:2}).format(Number(n??0));
export const label=(value:unknown)=>String(value??'—').replaceAll('_',' ');
export const when=(value:unknown)=>value?new Date(String(value)).toLocaleString('en-IN',{timeZone:'Asia/Kolkata'}):'—';
export const go=(path:string)=>{window.location.hash=path};

export async function api<T=Row>(path:string,options:RequestInit={}):Promise<T> {
 const headers=new Headers(options.headers);
 if(options.method && options.method!=='GET'){
  const csrf=await fetch('/api/auth/csrf',{credentials:'same-origin'});
  if(!csrf.ok)throw new Error('Unable to establish a secure session.');
  const token=await csrf.json();headers.set(token.headerName,token.token);
 }
 if(typeof options.body==='string' && !headers.has('Content-Type'))headers.set('Content-Type','application/json');
 const response=await fetch('/api'+path,{...options,headers,credentials:'same-origin'});
 if(!response.ok){
  if(response.status===401 && path!=='/me' && path!=='/auth/login')window.dispatchEvent(new Event('session-expired'));
  const body=await response.json().catch(()=>({}));
  throw new Error(body.message || (response.status===401?'Check your username and password.':response.status===403?'Permission denied or your security token expired. Refresh and try again.': 'Request failed ('+response.status+').'));
 }
 return response.status===204?undefined as T:response.json();
}
export const post=<T=Row>(path:string,data?:unknown)=>api<T>(path,{method:'POST',body:data===undefined?undefined:JSON.stringify(data)});
export function useLoad<T=Row>(path:string){
 const[data,setData]=useState<T|null>(null),[error,setError]=useState(''),[loading,setLoading]=useState(true),[tick,setTick]=useState(0);
 useEffect(()=>{let active=true;setLoading(true);setError('');api<T>(path).then(value=>{if(active)setData(value)}).catch(e=>{if(active)setError(e.message)}).finally(()=>{if(active)setLoading(false)});return()=>{active=false}},[path,tick]);
 return {data,error,loading,refresh:()=>setTick(x=>x+1)};
}
export function useTask(){
 const[busy,setBusy]=useState(false),[error,setError]=useState(''),[message,setMessage]=useState('');
 async function run(work:()=>Promise<void>){setBusy(true);setError('');setMessage('');try{await work()}catch(e){setError(e instanceof Error?e.message:'Action failed.')}finally{setBusy(false)}}
 return{busy,error,message,setMessage,run};
}
