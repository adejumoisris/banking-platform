import {escapeHtml as e,shortId,money,count,permissions,createApi,normalizeAmount,isUuid,calendarRange,defaultDates,RequestKeys} from './core.js';
const root=document.querySelector('#app'), modal=document.querySelector('#modal');
const state={auth:null,user:null,config:{},route:'overview',page:0,q:'',generation:0,busy:false,statement:null};
const keys=new RequestKeys();
const api=createApi(()=>state.auth,()=>{if(state.user) logout(true)});
const names={overview:'Overview',customers:'Customers',accounts:'Accounts',transactions:'Transactions',loans:'Loans',statements:'Statements',fineract:'Fineract'};
const symbols={overview:'◫',customers:'♙',accounts:'▤',transactions:'⇄',loans:'◷',statements:'▧',fineract:'⇌'};
const button=(text,action,extra='',kind='primary')=>'<button class="btn btn-'+kind+'" data-action="'+action+'" '+extra+'>'+e(text)+'</button>';
const badge=s=>'<span class="badge '+(['VERIFIED','ACTIVE','SUCCEEDED','COMPLETED','REPAID'].includes(s)?'green':['PENDING','REVIEW','ORIGINATED'].includes(s)?'amber':'gray')+'">'+e(s)+'</span>';
const err=(container,error)=>{const box=container.querySelector('[role=alert]');if(box){box.textContent=error.message;box.hidden=false}};
const input=(name,label,type='text',value='',extra='')=>'<div class="field"><label for="'+name+'">'+e(label)+'</label><input id="'+name+'" name="'+name+'" type="'+type+'" value="'+e(value)+'" required '+extra+'></div>';
const select=(name,label,options)=>'<div class="field"><label for="'+name+'">'+e(label)+'</label><select id="'+name+'" name="'+name+'">'+options.map(x=>'<option>'+e(x)+'</option>').join('')+'</select></div>';
const date=v=>v?new Date(v).toLocaleString('en-NG'):'—';
function toast(text){const box=document.querySelector('#toast');box.textContent=text;box.classList.add('visible');setTimeout(()=>box.classList.remove('visible'),4500)}
function login(){
 root.innerHTML='<div class="signin"><section class="signin-story"><a class="wordmark" href="/">▰ Rova<span>STAFF WORKSPACE</span></a><div><span class="eyebrow">YOUR DAILY BANKING WORKSPACE</span><h1>Banking,<br>with clarity.</h1><p>One place for your customers, their accounts,<br>and every transaction that matters.</p><div class="story-art"><div>↗<small>CONNECTED OPERATIONS</small></div><div>◎<small>BUILT AROUND PEOPLE</small></div></div></div><small>Rova Banking Platform · Staff access</small></section><section class="signin-form"><div class="signin-box"><span class="eyebrow">WELCOME BACK</span><h2>Sign in to your workspace</h2><p class="muted">Use your authorized staff credentials to continue.</p><form id="login"><div role="alert" class="error" hidden></div>'+(state.config.basicAuth?input('username','Username','text','demo','autocomplete="username"')+input('password','Password','password','','autocomplete="current-password"'):input('token','Staff access token','password','','autocomplete="off"'))+'<button class="btn btn-primary" type="submit">Sign in →</button></form><p class="signin-note">Credentials stay in this tab and are cleared when you sign out.</p>'+(state.config.basicAuth?'<span class="badge sandbox">LOCAL SANDBOX</span>':'')+'</div></section></div>';
}
function logout(force=false){if(state.busy&&!force)return;state.auth=null;state.user=null;state.generation++;keys.clear();state.statement=null;modal.close();login()}
function shell(){
 state.p=permissions(state.user);
 const available=Object.keys(names).filter(k=>state.p[k]);
 if(!available.includes(state.route))state.route=available[0];
 root.innerHTML='<div class="workspace"><aside class="sidenav"><a class="wordmark" href="#/overview">▰ Rova<span>STAFF WORKSPACE</span></a><div class="nav-caption">WORKSPACE</div><nav>'+available.map(k=>'<a href="#/'+k+'" data-nav="'+k+'"><span>'+symbols[k]+'</span>'+names[k]+'</a>').join('')+'</nav><div class="nav-footer"><span class="connection-dot"></span> Banking operations<br><small>Secure staff workspace</small></div></aside><div class="workspace-main"><header class="topbar"><button class="icon-btn mobile-menu" data-action="menu" aria-label="Toggle navigation">☰</button><span class="breadcrumb">Workspace <span>/</span> <strong id="crumb"></strong></span><div class="staff-meta">'+(state.config.basicAuth?'<span class="badge sandbox">Sandbox</span>':'')+'<span class="staff-avatar">'+e(state.user.username.slice(0,1).toUpperCase())+'</span><span>'+e(state.user.username)+'</span>'+button('Sign out','logout','','ghost')+'</div></header><main id="content" tabindex="-1"></main><footer class="workspace-footer">Rova Banking Platform <span>Staff workspace · '+e(state.user.roles.join(' / '))+'</span></footer></div></div>';
 if(!available.length){document.querySelector('#content').innerHTML='<div class="empty">Your role has no dashboard modules. Contact your administrator.</div>';return}
 load();
}
function table(headers,rows){return '<div class="table-scroll"><table><thead><tr>'+headers.map(h=>'<th>'+e(h)+'</th>').join('')+'</tr></thead><tbody>'+rows.map(row=>'<tr>'+row.map(cell=>'<td>'+cell+'</td>').join('')+'</tr>').join('')+'</tbody></table></div>'+(rows.length?'':'<div class="empty">No records found. Try another search.</div>')}
function title(sub,actions=''){return '<div class="page-heading"><div><span class="eyebrow">WORKSPACE</span><h1>'+names[state.route]+'</h1><p class="muted">'+e(sub)+'</p></div><div class="actions">'+actions+'</div></div>'}
function stat(label,value,sub=''){return '<div class="metric"><span>'+e(label)+'</span><strong>'+e(value)+'</strong><small>'+e(sub)+'</small></div>'}
function panel(title,body){return '<section class="surface"><div class="surface-heading"><h2>'+e(title)+'</h2></div>'+body+'</section>'}
function rowLink(id,kind,label){return '<button class="link-button" data-action="detail" data-kind="'+kind+'" data-id="'+e(id)+'">'+e(label||shortId(id))+'</button>'}
function postingRows(rows){return table(['Transaction','Customer / account','Amount','Date'],rows.map(r=>[badge(r.kind)+'<small class="subline mono">'+e(shortId(r.id))+'</small>',e(r.destinationName||r.sourceName||shortId(r.destinationId||r.sourceId)),e(money(r.amount,r.currency)),e(date(r.createdAt))]))}
async function load(){
 const generation=++state.generation, route=state.route, content=document.querySelector('#content');if(!content)return;
 document.querySelector('#crumb').textContent=names[route]||'Workspace';
 document.querySelectorAll('[data-nav]').forEach(a=>a.classList.toggle('active',a.dataset.nav===route));
 document.querySelector('.sidenav')?.classList.remove('is-open');
 content.innerHTML=title('Loading your workspace…')+'<div class="empty">Loading…</div>';
 try{
 let data;
 if(route==='overview')data=await api('/dashboard');
 if(['customers','accounts','loans','fineract'].includes(route)){
   if(route==='loans'&&!state.p.loanRead)data={items:[],totalElements:0,totalPages:0};
   else if(route==='fineract'&&!state.config.fineractEnabled)data={items:[],totalElements:0,totalPages:0};
   else data=await api((route==='fineract'?'/integrations/fineract/commands':'/'+route)+'?'+new URLSearchParams({page:state.page,size:20,q:state.q}));
 }
 if(!state.user||generation!==state.generation)return;
 state.data=data;
 if(route==='overview')overview(content,data);
 else if(['customers','accounts','loans','fineract'].includes(route))directory(content,data);
 else if(route==='transactions')transactions(content);
 else if(route==='statements')statements(content);
 }catch(error){if(generation===state.generation&&state.user)content.innerHTML=title('The workspace could not be loaded.')+'<div class="error" role="alert">'+e(error.message)+'</div>'+button('Try again','reload')}
}
function overview(c,d){
 c.innerHTML=title('A clear view of your banking operations.',button('Refresh','reload','','secondary')+(state.p.create?button('+ Add customer','new-customer'):''))
 +'<div class="metrics-grid">'+stat('Total customers',count(d.customers),count(d.verifiedCustomers)+' verified customers')+stat('Open accounts',count(d.accounts),count(d.activeAccounts)+' active accounts')+stat('Transactions today',count(d.transactionsToday),'Recorded today · UTC')+stat('Active loans',count(d.activeLoans),count(d.loans)+' loans in total')+'</div>'
 +'<div class="overview-grid">'+panel('Account balances by currency','<div class="balance-list">'+(d.balances.map(b=>'<div><span class="currency-token">'+e(b.currency)+'</span><div><strong>'+e(money(b.balance,b.currency))+'</strong><small>'+count(b.accounts)+' accounts</small></div></div>').join('')||'<div class="empty">Open an account to get started.</div>')+'</div><p class="panel-note">Balances stay separate for each currency.</p>')
 +panel('Activity · last 7 days','<div class="activity-chart">'+d.activity.map(a=>'<div><strong>'+count(a.count)+'</strong><div class="chart-track"><div class="chart-bar" data-height="'+Math.max(4,Number(a.count)/Math.max(1,...d.activity.map(x=>Number(x.count)))*100)+'%"></div></div><small>'+e(a.date.slice(5))+'</small></div>').join('')+'</div><p class="panel-note">Posted transactions per day · UTC</p>')+'</div>'
 +panel('Recent transactions',postingRows(d.recentTransactions))+'<div class="updated">Updated '+e(date(d.generatedAt))+'</div>'; c.querySelectorAll('.chart-bar').forEach(bar=>bar.style.height=bar.dataset.height);
}
function directory(c,d){
 const route=state.route;
 let action='';
 if(route==='customers'&&state.p.create)action=button('+ Add customer','new-customer');
 if(route==='accounts'&&state.p.create)action=button('+ Open account','new-account');
 if(route==='loans')action=(state.p.credit?button('+ Create loan','new-loan'):'')+(state.p.repay?button('Record repayment','repayment','','secondary'):'');
 if(route==='fineract'&&state.p.create&&state.config.fineractEnabled)action=button('+ Queue provisioning','provision');
 const descriptions={customers:'Manage customer records and identity verification.',accounts:'View account balances and open verified customer accounts.',loans:'Manage principal-only loans, disbursements and repayments.',fineract:'Track customer and savings-account provisioning.'};
 c.innerHTML=title(descriptions[route],action)+(route==='fineract'&&!state.config.fineractEnabled?'<div class="empty">Fineract is disabled in this environment.</div>':route==='loans'&&!state.p.loanRead?'<div class="empty">Your role can record repayments using a loan ID. Loan browsing requires Credit or Auditor access.</div>':
 '<section class="surface"><form id="search" class="directory-toolbar"><label class="sr-only" for="search-q">Search '+names[route]+'</label><input id="search-q" name="q" placeholder="Search by name or ID…" value="'+e(state.q)+'"><button class="btn btn-secondary">Search</button><span class="muted">'+count(d.totalElements)+' records</span></form>'+directoryTable(route,d.items)+'<div class="pagination"><span>Page '+(state.page+1)+' of '+Math.max(1,Number(d.totalPages))+'</span><div>'+button('← Previous','prev',state.page?'':'disabled','secondary')+button('Next →','next',state.page+1<Number(d.totalPages)?'':'disabled','secondary')+'</div></div></section>');
}
function directoryTable(route,rows){
 if(route==='customers')return table(['Customer','Email','KYC status','Accounts',''],rows.map(r=>['<strong>'+e(r.fullName)+'</strong><small class="subline mono">'+e(shortId(r.id))+'</small>',e(r.email),badge(r.kycStatus),count(r.accountCount),rowLink(r.id,'customers','View →')]));
 if(route==='accounts')return table(['Account','Customer','Currency','Balance','Status',''],rows.map(r=>['<span class="mono">'+e(shortId(r.id))+'</span>',e(r.customerName),e(r.currency),'<strong>'+e(money(r.balance,r.currency))+'</strong>',badge(r.status),rowLink(r.id,'accounts','View →')]));
 if(route==='loans')return table(['Loan','Customer','Principal','Outstanding','Status',''],rows.map(r=>[e(shortId(r.id)),e(r.customerName),e(money(r.principal,r.currency)),e(money(r.outstanding,r.currency)),badge(r.status),rowLink(r.id,'loans','View →')]));
 return table(['Command','Kind','Local record','Status','Attempts',''],rows.map(r=>[e(shortId(r.id)),e(r.kind),e(shortId(r.localId)),badge(r.status),count(r.attempts),rowLink(r.id,'fineract','View →')]));
}
function transactions(c){c.innerHTML=title('Record a payment with a clear review before posting.')+'<div class="payment-options">'+((state.p.teller||state.p.transfer)?['deposit','withdrawal','transfer'].filter(k=>k==='transfer'?state.p.transfer:state.p.teller).map(k=>'<section class="surface action-card"><span class="action-symbol">'+({deposit:'↙',withdrawal:'↗',transfer:'⇄'}[k])+'</span><h2>'+({deposit:'Deposit',withdrawal:'Withdrawal',transfer:'Transfer'}[k])+'</h2><p class="muted">'+({deposit:'Add money to an account.',withdrawal:'Take money out of an account.',transfer:'Move money between accounts in the same currency.'}[k])+'</p>'+button('Start '+k,k)+'</section>').join(''):'<div class="empty">Your role has read access. Use Statements to inspect account transactions.</div>')+'</div><section class="surface explanation"><h2>Review. Confirm. Record.</h2><p>Every payment shows its account IDs and amount before you confirm. A confirmed payment receives a server reference. If the response is interrupted, retrying the same request uses the same payment key.</p></section>'}
function dialog(title,body){modal.innerHTML='<div class="modal-header"><h2 id="modal-title">'+e(title)+'</h2>'+button('×','close','','ghost')+'</div><div class="modal-content">'+body+'</div>';if(!modal.open)modal.showModal()}
function form(title,fields,type){dialog(title,'<form id="operation" data-type="'+type+'"><div role="alert" class="error" hidden></div>'+fields+'<div class="modal-actions">'+button('Cancel','close','','secondary')+'<button class="btn btn-primary">Review →</button></div></form>')}
const idField=(name,label)=>input(name,label,'text','','placeholder="Full UUID" autocomplete="off"');
function openForm(type){
 state.operation=null;
 const amount=input('amount','Amount','text','','inputmode="decimal" placeholder="0.00"');
 if(type==='new-customer')form('Add customer',input('fullName','Full name','text','','maxlength="160"')+input('email','Email address','email','','maxlength="254"'),type);
 if(type==='new-account')form('Open account','<p class="muted">The customer must have verified KYC.</p>'+idField('customerId','Customer ID')+select('currency','Currency',['NGN','USD','GBP','EUR']),type);
 if(type==='new-loan')form('Create loan','<p class="muted">Principal-only loan. Disbursement is a separate action.</p>'+idField('accountId','Account ID')+amount,type);
 if(['deposit','withdrawal'].includes(type))form(type==='deposit'?'Record deposit':'Record withdrawal',idField('accountId','Account ID')+amount,type);
 if(type==='transfer')form('Transfer money',idField('sourceId','From account ID')+idField('destinationId','To account ID')+amount,type);
 if(type==='repayment')form('Record loan repayment',idField('loanId','Loan ID')+amount,type);
 if(type==='provision')form('Queue Fineract provisioning',select('kind','Record type',['customers','accounts'])+idField('recordId','Local record ID'),type);
}
function id(value){if(!isUuid(value))throw Error('Enter a valid full record ID.');return value}
async function review(formEl){
 const generation=state.dialogGeneration; const f=Object.fromEntries(new FormData(formEl)),t=formEl.dataset.type;let op={label:modal.querySelector('h2').textContent,body:null,idempotent:false};
 if(t==='new-customer')op={...op,path:'/customers',body:{fullName:f.fullName.trim(),email:f.email.trim()}};
 if(t==='new-account')op={...op,path:'/accounts',body:{customerId:id(f.customerId),currency:f.currency}};
 if(t==='new-loan')op={...op,path:'/loans',body:{accountId:id(f.accountId),principal:normalizeAmount(f.amount)}};
 if(['deposit','withdrawal'].includes(t))op={...op,path:'/accounts/'+id(f.accountId)+'/'+(t==='deposit'?'deposits':'withdrawals'),body:{amount:normalizeAmount(f.amount)},idempotent:true};
 if(t==='transfer'){if(f.sourceId===f.destinationId)throw Error('Choose two different accounts.');op={...op,path:'/transfers',body:{sourceId:id(f.sourceId),destinationId:id(f.destinationId),amount:normalizeAmount(f.amount)},idempotent:true}}
 if(t==='repayment')op={...op,path:'/loans/'+id(f.loanId)+'/repayments',body:{amount:normalizeAmount(f.amount)},idempotent:true};
 if(t==='provision')op={...op,path:'/integrations/fineract/'+f.kind+'/'+id(f.recordId),idempotent:true};
 const accountId=f.accountId||f.sourceId;
 if(['deposit','withdrawal','transfer'].includes(t)&&state.p.accounts){
   const account=await api('/accounts/'+accountId);op.currency=account.currency;
   if(t==='transfer'){const destination=await api('/accounts/'+f.destinationId);if(account.currency!==destination.currency)throw Error('Both accounts must use the same currency.')}
 }
 op.summary={...f};delete op.summary.kind;
 if(op.currency)op.summary.currency=op.currency;
 if(state.user&&modal.open&&generation===state.dialogGeneration)confirm(op);
}
function confirm(op){
 state.operation=op;
 dialog('Review & confirm','<p class="muted">'+e(op.label)+'. Check the details before continuing.</p><dl class="review-list">'+Object.entries(op.summary||{}).map(([k,v])=>'<div><dt>'+e(k.replace(/([A-Z])/g,' $1'))+'</dt><dd class="mono">'+e(v)+'</dd></div>').join('')+'</dl><div role="alert" class="error" hidden></div><div class="modal-actions">'+button('Cancel','close','','secondary')+button('Confirm '+op.label.toLowerCase(),'confirm')+'</div>');
}
async function execute(){
 if(state.busy||!state.operation)return;
 const op=state.operation;state.busy=true;modal.querySelectorAll('button').forEach(b=>b.disabled=true);
 try{
 const result=await api(op.path,{method:'POST',body:op.body===null?undefined:op.body,key:op.idempotent?keys.get(op.path,op.body):undefined});
 if(!state.user)return;
 if(op.idempotent)keys.complete(op.path,op.body);
 dialog('Successfully recorded','<div class="receipt-check">✓</div><h3>'+e(op.label)+' completed</h3><p>The server confirmed this request.</p>'+(result?.amount?'<strong class="receipt-amount">'+e(money(result.amount,result.currency))+'</strong>':'')+'<p class="muted">Reference</p><p class="mono wrap">'+e(result?.id||'Confirmed')+'</p>'+button('Done','done'));
 state.operation=null;
 }catch(error){
 if(state.user){err(modal,error);const b=modal.querySelector('[data-action=confirm]');if(error.uncertain){modal.querySelector('[role=alert]').textContent+=(op.idempotent?' Retry uses the same request key.':' The outcome is unknown. Check the record list before submitting another request.');if(b)b.disabled=!op.idempotent} }
 }finally{state.busy=false;modal.querySelectorAll('button').forEach(b=>{if(!(b.dataset.action==='confirm'&&modal.querySelector('[role=alert]')?.textContent.includes('outcome is unknown')))b.disabled=false})}
}
async function detail(kind,recordId){
 dialog('Record details','<p>Loading…</p>');const generation=++state.dialogGeneration;
 try{
 const row=await api((kind==='fineract'?'/integrations/fineract/commands':'/'+kind)+'/'+recordId);
 if(!state.user||!modal.open||generation!==state.dialogGeneration)return;
 state.detail={kind,row};
 let actions='';
 if(kind==='customers'&&state.p.verify&&row.kycStatus==='PENDING')actions+=button('Verify KYC','verify');
 if(kind==='accounts'&&state.p.statements)actions+=button('View statement','account-statement');
 if(kind==='loans'&&state.p.credit&&row.status==='PENDING')actions+=button('Disburse loan','disburse');
 if(kind==='fineract'&&state.p.create){if(row.status==='REVIEW')actions+=button('Reconcile','reconcile');if(['PENDING','RETRY'].includes(row.status))actions+=button('Dispatch now','dispatch')}
 dialog(names[kind]+' · '+shortId(row.id),'<dl class="review-list">'+Object.entries(row).map(([k,v])=>'<div><dt>'+e(k.replace(/([A-Z])/g,' $1'))+'</dt><dd class="wrap">'+e(v??'—')+'</dd></div>').join('')+'</dl><div class="modal-actions">'+actions+button('Close','close','','secondary')+'</div>');
 }catch(error){if(modal.open)dialog('Unable to load record','<div class="error" role="alert">'+e(error.message)+'</div>'+button('Close','close'))}
}
function statements(c){
 const dates=defaultDates();state.statement=null;
 c.innerHTML=title('Account history, balances and a downloadable statement.')+'<section class="surface"><form id="statement" class="statement-form"><div role="alert" class="error" hidden></div>'+idField('accountId','Account ID')+input('from','From date','date',dates.from)+input('through','Through date','date',dates.through)+'<button class="btn btn-primary">Generate statement</button><small class="muted">Dates include the whole day in your browser’s timezone.</small></form></section><div id="statement-result"></div>';
}
async function generateStatement(page=0){
 const f=Object.fromEntries(new FormData(document.querySelector('#statement')));
 id(f.accountId);const range=calendarRange(f.from,f.through), query=new URLSearchParams({...range,page,size:25}),generation=++state.statementGeneration;
 const result=await api('/accounts/'+f.accountId+'/statement?'+query);
 if(!state.user||state.route!=='statements'||generation!==state.statementGeneration)return;
 state.statement={result,f,range,page};renderStatement();
}
function renderStatement(){
 const {result:d,page}=state.statement;
 document.querySelector('#statement-result').innerHTML='<div class="metrics-grid">'+stat('Opening balance',money(d.openingBalance,d.currency))+stat('Money in',money(d.totalMoneyIn,d.currency))+stat('Money out',money(d.totalMoneyOut,d.currency))+stat('Closing balance',money(d.closingBalance,d.currency))+'</div>'+panel('Statement · '+shortId(d.accountId),'<div class="statement-toolbar">'+button('Download CSV','csv','','secondary')+'<span class="muted">'+count(d.totalElements)+' transactions</span></div>'+table(['Date','Type','Reference','Money in','Money out'],d.transactions.map(r=>[e(date(r.occurredAt)),e(r.type),e(shortId(r.reference)),e(money(r.moneyIn,d.currency)),e(money(r.moneyOut,d.currency))]))+'<div class="pagination">'+button('← Previous','statement-prev',page?'':'disabled','secondary')+'<span>Page '+(page+1)+' of '+Math.max(1,Number(d.totalPages))+'</span>'+button('Next →','statement-next',page+1<Number(d.totalPages)?'':'disabled','secondary')+'</div>');
}
async function csv(){
 const s=state.statement;if(!s)return;
 const response=await api('/accounts/'+s.f.accountId+'/statement.csv?'+new URLSearchParams(s.range),{download:true});
 const url=URL.createObjectURL(response.blob),a=document.createElement('a');a.href=url;a.download='statement-'+s.f.accountId+'.csv';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);toast('Statement downloaded');
}
state.dialogGeneration=0;state.statementGeneration=0;
async function action(event){
 const target=event.target.closest('[data-action]');if(!target)return;const a=target.dataset.action;
 if(state.busy)return;
 try{
 if(a==='logout')logout();else if(a==='menu')document.querySelector('.sidenav').classList.toggle('is-open');
 else if(a==='close'){state.dialogGeneration++;modal.close();state.operation=null}
 else if(a==='done'){modal.close();load()}
 else if(a==='reload')load();
 else if(a==='next'||a==='prev'){state.page+=a==='next'?1:-1;load()}
 else if(a==='detail')await detail(target.dataset.kind,target.dataset.id);
 else if(a==='confirm')await execute();
 else if(a==='csv')await csv();
 else if(a==='statement-prev'||a==='statement-next')await generateStatement(state.statement.page+(a==='statement-next'?1:-1));
 else if(a==='account-statement'){const accountId=state.detail.row.id;modal.close();state.route='statements';await load();document.querySelector('#accountId').value=accountId;location.hash='/statements'}
 else if(['verify','disburse','dispatch','reconcile'].includes(a)){
 const {row}=state.detail;const path=a==='verify'?'/customers/'+row.id+'/verify':a==='disburse'?'/loans/'+row.id+'/disburse':'/integrations/fineract/commands/'+row.id+'/'+a;
 confirm({label:{verify:'Verify KYC',disburse:'Disburse loan',dispatch:'Dispatch command',reconcile:'Reconcile command'}[a],path,body:null,idempotent:true,summary:a==='disburse'?{loanId:row.id,accountId:row.accountId,principal:row.principal}:{recordId:row.id}});
 }else openForm(a);
 }catch(error){if(modal.open){if(!modal.querySelector('[role=alert]'))modal.querySelector('.modal-content').insertAdjacentHTML('afterbegin','<div class="error" role="alert"></div>');err(modal,error)}else toast(error.message)}
}
root.addEventListener('click',action);modal.addEventListener('click',action);
root.addEventListener('submit',async event=>{
 event.preventDefault();const f=event.target;
 try{
 if(f.id==='login'){
 const b=f.querySelector('button');b.disabled=true;
 const values=Object.fromEntries(new FormData(f));
 state.auth=state.config.basicAuth?'Basic '+btoa(String.fromCharCode(...new TextEncoder().encode(values.username+':'+values.password))):'Bearer '+values.token.trim();
 try{state.user=await api('/session');state.route=location.hash.slice(2).split('?')[0]||'overview';shell()}catch(error){state.auth=null;err(f,error)}finally{b.disabled=false}
 }else if(f.id==='search'){state.q=String(new FormData(f).get('q')).trim();state.page=0;await load()}
 else if(f.id==='statement'){await generateStatement()}
 }catch(error){err(f,error)}
});
modal.addEventListener('submit',async event=>{event.preventDefault();if(state.busy)return;const f=event.target;f.querySelectorAll('button').forEach(b=>b.disabled=true);try{await review(f)}catch(error){err(f,error)}finally{f.querySelectorAll('button').forEach(b=>b.disabled=false)}});
modal.addEventListener('cancel',event=>{if(state.busy)event.preventDefault();else state.dialogGeneration++});
root.addEventListener('input',event=>{if(event.target.closest('#statement')){state.statementGeneration++;state.statement=null;document.querySelector('#statement-result').innerHTML=''}});
window.addEventListener('hashchange',()=>{const route=location.hash.slice(2).split('?')[0];if(state.user&&state.p[route]&&route!==state.route){state.route=route;state.page=0;state.q='';load()}});
window.addEventListener('beforeunload',event=>{if(state.busy){event.preventDefault();event.returnValue=''}});
try{state.config=await api('/ui/config');login()}catch(error){root.innerHTML='<div class="empty"><h1>Workspace unavailable</h1><p>'+e(error.message)+'</p><a href="/">Try again</a></div>'}
