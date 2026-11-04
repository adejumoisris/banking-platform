import {test} from 'node:test';
import assert from 'node:assert/strict';
import {parseExactJson,money,normalizeAmount,calendarRange,permissions,RequestKeys,escapeHtml} from '../src/main/resources/static/assets/core.js';
test('money remains exact beyond the safe integer limit',()=>{
 const value=parseExactJson('{"amount":99999999999999999.99,"name":"quote \\" and 123","id":12}');
 assert.equal(value.amount,'99999999999999999.99');
 assert.equal(value.name,'quote " and 123');
 assert.equal(money(value.amount,'NGN'),'₦99,999,999,999,999,999.99');
 assert.equal(normalizeAmount('000123.4'),'123.40');
 for(const invalid of ['0','-1','1,000','1.001','1e2','100000000000000000'])assert.throws(()=>normalizeAmount(invalid));
});
test('calendar ranges include through date and reject invalid dates',()=>{
 const range=calendarRange('2026-01-31','2026-02-01');
 assert.equal((new Date(range.to)-new Date(range.from))/86400000,2);
 assert.throws(()=>calendarRange('2026-02-30','2026-03-01'));
 assert.throws(()=>calendarRange('2026-03-02','2026-03-01'));
});
test('retry keys survive uncertainty and clear after success',()=>{
 const keys=new RequestKeys(),body={amount:'100.01'},first=keys.get('/payments',body);
 assert.equal(keys.get('/payments',body),first);
 assert.notEqual(keys.get('/payments',{amount:'100.02'}),first);
 keys.complete('/payments',body);
 assert.notEqual(keys.get('/payments',body),first);
});
test('auditors can read without payment permissions and names are escaped',()=>{
 const p=permissions({roles:['AUDITOR']});
 assert.equal(p.statements,true);assert.equal(p.teller,false);assert.equal(p.credit,false);assert.equal(p.create,false);
 assert.equal(escapeHtml('<img onerror="x">'),'&lt;img onerror=&quot;x&quot;&gt;');
});
