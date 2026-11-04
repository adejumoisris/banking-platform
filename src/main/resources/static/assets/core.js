export const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
export const shortId = value => value ? String(value).slice(0,8).toUpperCase() : '—';
export const isUuid = value => /^[\da-f]{8}-[\da-f]{4}-[\da-f]{4}-[\da-f]{4}-[\da-f]{12}$/i.test(String(value));

// Preserve JSON numeric tokens as strings, including money beyond Number.MAX_SAFE_INTEGER.
export function parseExactJson(text) {
  let output='', string=false, escaped=false;
  for (let index=0; index<text.length; index++) {
    const char=text[index];
    if (string) {
      output+=char;
      if (escaped) escaped=false; else if (char==='\\') escaped=true; else if (char==='"') string=false;
    } else if (char==='"') { string=true; output+=char; }
    else if (char==='-' || /\d/.test(char)) {
      const token=text.slice(index).match(/^-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?/);
      if (!token) throw new SyntaxError('Invalid JSON number');
      output+=JSON.stringify(token[0]); index+=token[0].length-1;
    } else output+=char;
  }
  return JSON.parse(output);
}

export function normalizeAmount(value) {
  const text=String(value).trim();
  if (!/^\d{1,17}(?:\.\d{1,2})?$/.test(text)) throw new Error('Enter a positive amount with no more than 2 decimal places and no commas.');
  const [whole,fraction='']=text.split('.');
  const result=whole.replace(/^0+(?=\d)/,'')+'.'+fraction.padEnd(2,'0');
  if (BigInt(result.replace('.',''))===0n) throw new Error('The amount must be greater than zero.');
  return result;
}
export function money(value, currency='NGN') {
  const text=String(value ?? '0');
  const parts=text.match(/^(-?)(\d+)(?:\.(\d+))?$/);
  if (!parts) return '—';
  const fraction=(parts[3]??'').padEnd(2,'0');
  if (fraction.length>2 && /[1-9]/.test(fraction.slice(2))) return '—';
  const whole=parts[2].replace(/^0+(?=\d)/,'').replace(/\B(?=(\d{3})+(?!\d))/g,',');
  const symbol={NGN:'₦',USD:'$',GBP:'£',EUR:'€'}[currency] ?? (currency ? currency+' ' : '');
  return parts[1]+symbol+whole+'.'+fraction.slice(0,2);
}
export const count = value => Number(value??0).toLocaleString('en-NG');
export const hasRole = (session,...roles) => roles.some(role => session?.roles?.includes(role));
export function permissions(session) {
  const p={overview:hasRole(session,'OPERATIONS','AUDITOR'), customers:hasRole(session,'OPERATIONS','COMPLIANCE','AUDITOR'),
    accounts:hasRole(session,'OPERATIONS','AUDITOR'), transactions:hasRole(session,'OPERATIONS','TELLER','AUDITOR'),
    loans:hasRole(session,'CREDIT','AUDITOR','OPERATIONS'), loanRead:hasRole(session,'CREDIT','AUDITOR'),
    statements:hasRole(session,'OPERATIONS','AUDITOR'), fineract:hasRole(session,'OPERATIONS','AUDITOR'),
    create:hasRole(session,'OPERATIONS'), verify:hasRole(session,'COMPLIANCE'), teller:hasRole(session,'TELLER'),
    transfer:hasRole(session,'OPERATIONS'), credit:hasRole(session,'CREDIT'), repay:hasRole(session,'OPERATIONS')};
  return p;
}
export function calendarRange(from, through) {
  const parse=value => {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) throw new Error('Choose both statement dates.');
    const date=new Date(value+'T00:00:00');
    const [year,month,day]=value.split('-').map(Number);
    if (date.getFullYear()!==year || date.getMonth()+1!==month || date.getDate()!==day) throw new Error('Choose a valid date.');
    return date;
  };
  const start=parse(from), end=parse(through);
  if (start>end) throw new Error('The start date must not be after the through date.');
  end.setDate(end.getDate()+1);
  return {from:start.toISOString(),to:end.toISOString()};
}
export function defaultDates(now=new Date()) {
  const date=d => `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
  return {from:date(new Date(now.getFullYear(),now.getMonth(),1)),through:date(now)};
}
export class ApiError extends Error {
  constructor(message,status=0,uncertain=false) { super(message); this.status=status; this.uncertain=uncertain; }
}
export function createApi(getAuthorization,onUnauthorized=()=>{}) {
  return async function request(path,{method='GET',body,key,download=false}={}) {
    if (!path.startsWith('/') || path.startsWith('//')) throw new Error('Invalid API path');
    const controller=new AbortController();
    const timeout=setTimeout(()=>controller.abort(),30000);
    try {
      const headers={'Accept':download?'text/csv':'application/json','X-Requested-With':'XMLHttpRequest'};
      const authorization=getAuthorization(); if (authorization) headers.Authorization=authorization;
      if (body!==undefined) headers['Content-Type']='application/json';
      if (key) headers['Idempotency-Key']=key;
      const response=await fetch('/api/v1'+path,{method,headers,body:body===undefined?undefined:JSON.stringify(body),
        credentials:'omit',mode:'same-origin',redirect:'error',cache:'no-store',signal:controller.signal});
      if (!response.ok) {
        let problem={}; try { problem=parseExactJson(await response.text()); } catch { }
        if (response.status===401) onUnauthorized();
        throw new ApiError(problem.detail || (response.status===401?'Sign-in failed. Check your credentials.':response.status===403?'Your staff role does not permit this action.':'The request could not be completed.'),response.status,method!=='GET' && (response.status>=500 || response.status===408));
      }
      if (download) return {blob:await response.blob(),headers:response.headers};
      const text=await response.text(); return text?parseExactJson(text):null;
    } catch (error) {
      if (error instanceof ApiError) throw error;
      throw new ApiError('We could not confirm the server response. Check your connection and try again.',0,method!=='GET');
    } finally { clearTimeout(timeout); }
  };
}

export class RequestKeys {
  constructor() { this.keys=new Map(); }
  get(path,body) {
    const fingerprint=path+'\n'+JSON.stringify(body??null);
    if (!this.keys.has(fingerprint)) this.keys.set(fingerprint,crypto.randomUUID());
    return this.keys.get(fingerprint);
  }
  complete(path,body) { this.keys.delete(path+'\n'+JSON.stringify(body??null)); }
  clear() { this.keys.clear(); }
}
