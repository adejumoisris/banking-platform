import {chromium} from '@playwright/test';
import fs from 'node:fs/promises';
const config=Object.fromEntries((await fs.readFile('.env.fineract-sandbox','utf8')).split(/\r?\n/).filter(x=>/^[A-Z_]+=/.test(x)).map(x=>{const i=x.indexOf('=');return [x.slice(0,i),x.slice(i+1)]}));
const base='http://localhost:8080',username=config.LOCAL_DEMO_USERNAME||'demo';
if(!config.LOCAL_DEMO_PASSWORD)throw Error('Local sandbox credentials are not configured');
const auth='Basic '+Buffer.from(username+':'+config.LOCAL_DEMO_PASSWORD).toString('base64');
const browser=await chromium.launch({headless:true});
try{
 const page=await browser.newPage({viewport:{width:1440,height:1000},timezoneId:'Africa/Lagos'});
 const errors=[];page.on('pageerror',error=>errors.push(error.message));
 await page.goto(base);
 await page.getByLabel('Username',{exact:true}).fill(username);
 await page.getByLabel('Password',{exact:true}).fill(config.LOCAL_DEMO_PASSWORD);
 await page.getByRole('button',{name:'Sign in',exact:false}).click();
 await page.getByRole('heading',{name:'Overview',exact:true}).waitFor();
 await page.getByText('Total customers',{exact:true}).waitFor(); await fs.mkdir('.sandbox',{recursive:true});
 await page.screenshot({path:'.sandbox/staff-live-overview.png',fullPage:true});
 for(const name of ['Customers','Accounts','Loans','Fineract']){
  await page.getByRole('link',{name,exact:false}).click();
  await page.getByRole('heading',{name,exact:true}).waitFor();
  await page.locator('.directory-toolbar').waitFor();
  if(await page.getByRole('alert').count())throw Error('A live directory failed: '+name);
 }
 const response=await fetch(base+'/api/v1/accounts?size=1',{headers:{Authorization:auth}});
 if(!response.ok)throw Error('Account directory failed: '+response.status);
 const accounts=await response.json();
 if(accounts.items.length){
  await page.getByRole('link',{name:'Statements',exact:false}).click();
  await page.getByLabel('Account ID',{exact:true}).fill(accounts.items[0].id);
  await page.getByRole('button',{name:'Generate statement'}).click();
  await page.getByRole('button',{name:'Download CSV'}).waitFor();
  const download=page.waitForEvent('download');
  await page.getByRole('button',{name:'Download CSV'}).click();
  if(!(await download).suggestedFilename().endsWith('.csv'))throw Error('Statement download failed');
 }
 await page.getByRole('button',{name:'Sign out',exact:true}).click();
 await page.getByRole('heading',{name:'Sign in to your workspace'}).waitFor();
 if(errors.length)throw Error('Browser errors: '+errors.join('; '));
 console.log('Live dashboard passed: sign-in, overview, directories, statement/CSV where an account exists, sign-out.');
}finally{await browser.close()}
