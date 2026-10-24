import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {api,url,uid,nick,rows,canEdit,images} from '../src/api.ts';
const storage=new Map();globalThis.localStorage={getItem:k=>storage.get(k)||null,setItem:(k,v)=>storage.set(k,v),removeItem:k=>storage.delete(k)};
const events=[];globalThis.window={dispatchEvent:e=>events.push(e.type)};

test('query encoding preserves zero, false, repeated types and existing query',()=>{
 assert.equal(url('/x?keyword=',{status:0,enabled:false,type:['pet','activity'],empty:'',none:null}),'/x?keyword=&status=0&enabled=false&type=pet&type=activity');
 assert.equal(url('/x',{keyword:'猫 & 狗'}),'/x?keyword=%E7%8C%AB+%26+%E7%8B%97');
});
test('both UserSimpleDto and current-user DTO resolve the same identity',()=>{
 assert.equal(uid({userId:12}),uid({id:12}));assert.equal(nick({nickName:'小橘'}),'小橘');assert.equal(nick({nickname:'小橘'}),'小橘');
});
test('completed and deleted contents cannot re-enter edit, active content can',()=>{
 assert.equal(canEdit({status:2}),false);assert.equal(canEdit({status:-1}),false);assert.equal(canEdit({status:1,deleted:1}),false);assert.equal(canEdit({status:1,auditStatus:1}),true);assert.equal(canEdit({status:0,auditStatus:0}),true);
});
test('page records and plain lists retain server data without fabricated items',()=>{
 const items=[{id:1}];assert.equal(rows({records:items,total:1}),items);assert.equal(rows(items),items);assert.deepEqual(rows(null),[]);
});
test('image list rejects non-HTTP script URLs',()=>{
 assert.deepEqual(images(['javascript:alert(1)','https://example.com/a.jpg','/images/a.jpg',null]),['https://example.com/a.jpg','/images/a.jpg']);
});
test('GET uses existing bearer token exactly once and returns data',async()=>{
 storage.set('token','Bearer existing-token');let sent;globalThis.fetch=async(p,o)=>{sent={p,o};return new Response(JSON.stringify({code:200,data:{total:4,records:[]}}));};
 const d=await api('/pet/list?pageNum=1');assert.equal(d.total,4);assert.equal(sent.p,'/pet-system/pet/list?pageNum=1');assert.equal(sent.o.headers.Authorization,'Bearer existing-token');assert.equal(sent.o.body,undefined);assert.ok(sent.o.signal);
});
test('write request sends JSON and does not invent success on rejection',async()=>{
 let sent;globalThis.fetch=async(p,o)=>{sent=o;return new Response(JSON.stringify({code:400,message:'已完成不能修改'}));};
 await assert.rejects(api('/pet/update','POST',{id:1}),/已完成不能修改/);assert.deepEqual(JSON.parse(sent.body),{id:1});assert.equal(sent.headers['Content-Type'],'application/json');
});
test('multipart upload leaves content type boundary to browser',async()=>{
 let sent;globalThis.fetch=async(p,o)=>{sent=o;return new Response(JSON.stringify({code:200,data:'https://example.com/image.jpg'}));};const f=new FormData();f.append('file',new Blob(['image']),'pet.jpg');await api('/pet/upload','POST',f);assert.equal(sent.body,f);assert.equal(sent.headers['Content-Type'],undefined);
});
test('expired session clears local auth and dispatches logout',async()=>{
 storage.set('token','expired');storage.set('userInfo','{}');globalThis.fetch=async()=>new Response(JSON.stringify({code:401,message:'登录已过期'}),{status:401});await assert.rejects(api('/user/info'),/登录已过期/);assert.equal(storage.has('token'),false);assert.equal(storage.has('userInfo'),false);assert.ok(events.includes('auth-cleared'));
});
test('malformed response rejects with readable error',async()=>{
 globalThis.fetch=async()=>new Response('<html>error</html>',{status:502});await assert.rejects(api('/pet/list'),/服务返回格式异常/);
});
