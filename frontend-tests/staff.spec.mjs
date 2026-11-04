import {test,expect} from '@playwright/test';
const customer='11111111-1111-4111-8111-111111111111',account='22222222-2222-4222-8222-222222222222',destination='33333333-3333-4333-8333-333333333333';
const c={id:customer,fullName:'Ada Okafor',email:'ada@example.test',kycStatus:'VERIFIED',createdAt:'2026-11-04T09:00:00Z',accountCount:2};
const a={id:account,customerId:customer,customerName:c.fullName,currency:'NGN',balance:'120000.50',status:'ACTIVE'};
const dashboard={customers:24,verifiedCustomers:20,pendingCustomers:4,accounts:31,activeAccounts:31,loans:5,activeLoans:3,transactionsToday:12,balances:[{currency:'NGN',balance:'1250400.50',accounts:25},{currency:'USD',balance:'2300.00',accounts:6}],activity:Array.from({length:7},(_,i)=>({date:'2026-11-0'+(i+1),count:i*3})),recentTransactions:[{id:account,kind:'DEPOSIT',amount:'25000.00',currency:'NGN',createdAt:'2026-11-04T09:00:00Z',destinationName:c.fullName,destinationId:account}],generatedAt:'2026-11-04T10:00:00Z'};
async function setup(page,roles=['OPERATIONS','COMPLIANCE','TELLER','CREDIT','AUDITOR']){
 const writes=[];
 await page.route('**/api/v1/**',async route=>{
 const req=route.request(),url=new URL(req.url()),path=url.pathname.replace('/api/v1','');
 let data;
 if(path==='/ui/config')data={name:'Rova',basicAuth:true,fineractEnabled:true};
 else if(path==='/session')data={username:'demo',roles};
 else if(path==='/dashboard')data=dashboard;
 else if(req.method()==='POST'){writes.push({path,body:req.postDataJSON(),key:req.headers()['idempotency-key']});data={id:account,amount:'100.01',currency:'NGN'}}
 else if(path.endsWith('/statement.csv'))return route.fulfill({contentType:'text/csv',body:'record_type,account_id,currency\nSUMMARY,'+account+',NGN'});
 else if(path.endsWith('/statement'))data={accountId:account,currency:'NGN',openingBalance:'100.00',totalMoneyIn:'20.00',totalMoneyOut:'0.00',closingBalance:'120.00',totalElements:1,totalPages:1,transactions:[{reference:destination,occurredAt:'2026-11-04T09:00:00Z',type:'DEPOSIT',moneyIn:'20.00',moneyOut:'0.00'}]};
 else if(path==='/customers')data={items:[c],page:0,size:20,totalElements:1,totalPages:1};
 else if(path==='/accounts')data={items:[a],page:0,size:20,totalElements:1,totalPages:1};
 else if(path.startsWith('/accounts/'))data={...a,id:path.split('/')[2]};
 else if(path.startsWith('/customers/'))data=c;
 else data={items:[],page:0,size:20,totalElements:0,totalPages:0};
 await route.fulfill({json:data});
 });
 await page.goto('/');return writes;
}
async function signIn(page){await page.getByLabel('Password',{exact:true}).fill('test-only');await page.getByRole('button',{name:'Sign in',exact:false}).click();await expect(page.getByRole('heading',{name:'Overview',exact:true})).toBeVisible()}
test('dashboard, directory, statement and CSV work together',async({page})=>{
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await setup(page);await page.screenshot({path:'.sandbox/ui-login.png',fullPage:true});await signIn(page);
 await expect(page.getByText('₦1,250,400.50',{exact:true})).toBeVisible();
 await page.screenshot({path:'.sandbox/ui-dashboard.png',fullPage:true});
 await page.getByRole('link',{name:'Customers',exact:false}).click();await expect(page.getByText(c.fullName,{exact:true})).toBeVisible();
 await page.getByRole('button',{name:'View →'}).click();await expect(page.getByText(c.email,{exact:true}).last()).toBeVisible();await page.getByRole('button',{name:'Close',exact:true}).click();
 await page.getByRole('link',{name:'Statements',exact:false}).click();
 await page.getByLabel('Account ID',{exact:true}).fill(account);
 await page.getByRole('button',{name:'Generate statement'}).click();
 await expect(page.getByText('₦120.00',{exact:true})).toBeVisible();
 const download=page.waitForEvent('download');await page.getByRole('button',{name:'Download CSV'}).click();expect((await download).suggestedFilename()).toContain(account);
 expect(await page.evaluate(()=>localStorage.length+sessionStorage.length)).toBe(0);
 expect(errors).toEqual([]);
});
test('transfer requires review and uncertain retries reuse exact key and amount',async({page})=>{
 const writes=await setup(page);await signIn(page);
 await page.getByRole('link',{name:'Transactions',exact:false}).click();await page.getByRole('button',{name:'Start transfer'}).click();
 await page.getByLabel('From account ID').fill(account);await page.getByLabel('To account ID').fill(destination);await page.getByLabel('Amount',{exact:true}).fill('100.01');
 await page.getByRole('button',{name:'Review →'}).click();expect(writes).toHaveLength(0);
 let attempts=[];
 await page.route('**/api/v1/transfers',async route=>{attempts.push({key:route.request().headers()['idempotency-key'],body:route.request().postDataJSON()});if(attempts.length===1)await route.abort('failed');else await route.fulfill({json:{id:account,amount:'100.01',currency:'NGN'}})});
 await page.getByRole('button',{name:'Confirm transfer money'}).click();await expect(page.getByRole('alert')).toContainText('same request key');
 await page.getByRole('button',{name:'Confirm transfer money'}).click();await expect(page.getByRole('heading',{name:'Successfully recorded'})).toBeVisible();
 expect(attempts).toHaveLength(2);expect(attempts[0]).toEqual(attempts[1]);expect(attempts[0].body.amount).toBe('100.01');
});
test('auditor view has no financial write controls',async({page})=>{
 await setup(page,['AUDITOR']);await signIn(page);await expect(page.getByRole('button',{name:'+ Add customer'})).toHaveCount(0);
 await page.getByRole('link',{name:'Transactions',exact:false}).click();await expect(page.getByRole('button',{name:'Start transfer'})).toHaveCount(0);
});
test('mobile workspace fits and navigation opens',async({page})=>{
 await page.setViewportSize({width:390,height:844});await setup(page);await signIn(page);
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
 await page.screenshot({path:'.sandbox/ui-mobile.png',fullPage:true});
 await page.getByRole('button',{name:'Toggle navigation'}).click();await page.getByRole('link',{name:'Accounts',exact:false}).click();await expect(page.getByRole('heading',{name:'Accounts',exact:true})).toBeVisible();
});

test('customer and account creation show review before posting',async({page})=>{
 const writes=await setup(page);await signIn(page);
 await page.getByRole('button',{name:'+ Add customer'}).click();
 await page.getByLabel('Full name').fill('New Customer');await page.getByLabel('Email address').fill('new@example.test');
 await page.getByRole('button',{name:'Review →'}).click();expect(writes).toHaveLength(0);
 await page.getByRole('button',{name:'Confirm add customer'}).click();await expect(page.getByRole('heading',{name:'Successfully recorded'})).toBeVisible();
 expect(writes[0].body).toEqual({fullName:'New Customer',email:'new@example.test'});
 await page.getByRole('button',{name:'Done',exact:true}).click();
 await page.getByRole('link',{name:'Accounts',exact:false}).click();await page.getByRole('button',{name:'+ Open account'}).click();
 await page.getByLabel('Customer ID',{exact:true}).fill(customer);await page.getByLabel('Currency',{exact:true}).selectOption('USD');
 await page.getByRole('button',{name:'Review →'}).click();await page.getByRole('button',{name:'Confirm open account'}).click();
 await expect(page.getByRole('heading',{name:'Successfully recorded'})).toBeVisible();expect(writes[1].body).toEqual({customerId:customer,currency:'USD'});
});
test('strict CSP chart renders proportional bars without violations',async({page})=>{
 const violations=[];page.on('console',message=>{if(message.type()==='error')violations.push(message.text())});
 await setup(page);await signIn(page);
 const bars=await page.locator('.chart-bar').evaluateAll(nodes=>nodes.map(n=>n.getBoundingClientRect().height));
 expect(bars.at(-1)).toBeGreaterThan(bars[1]);expect(violations).toEqual([]);
 await page.screenshot({path:'.sandbox/ui-dashboard.png',fullPage:true});
});
test('expired credentials clear the workspace even during a payment',async({page})=>{
 await setup(page);await signIn(page);
 await page.getByRole('link',{name:'Transactions',exact:false}).click();await page.getByRole('button',{name:'Start deposit'}).click();
 await page.getByLabel('Account ID',{exact:true}).fill(account);await page.getByLabel('Amount',{exact:true}).fill('100');
 await page.getByRole('button',{name:'Review →'}).click();
 await page.route('**/api/v1/accounts/*/deposits',route=>route.fulfill({status:401,json:{detail:'Expired'}}));
 await page.getByRole('button',{name:'Confirm record deposit'}).click();
 await expect(page.getByRole('heading',{name:'Sign in to your workspace'})).toBeVisible();
 await expect(page.getByRole('dialog')).toHaveCount(0);
});
