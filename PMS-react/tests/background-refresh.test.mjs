import {test, before, after, beforeEach, afterEach} from 'node:test';
import assert from 'node:assert/strict';
import {JSDOM} from 'jsdom';
import {fileURLToPath} from 'node:url';
import React, {act} from 'react';
import {createServer} from 'vite';
import react from '@vitejs/plugin-react';
const dom=new JSDOM('<div id="root"></div>',{url:'http://localhost/'});
for(const name of ['window','document','localStorage','HTMLElement','Event'])Object.defineProperty(globalThis,name,{value:dom.window[name],configurable:true});
globalThis.IS_REACT_ACT_ENVIRONMENT=true;
const {createRoot}=await import('react-dom/client');
const server=await createServer({configFile:false,root:fileURLToPath(new URL('..',import.meta.url)),plugins:[react()],server:{middlewareMode:true,hmr:false}});
const {useData,DataState,AppContext,PageSkeleton}=await server.ssrLoadModule('/src/core.tsx');
const {queryCache}=await server.ssrLoadModule('/src/queryCache.ts');
let root,host,calls,current,mounts,unmounts;
function Child(){React.useEffect(()=>{mounts++;return()=>{unmounts++;};},[]);return React.createElement('input',{'aria-label':'draft',defaultValue:'unfinished draft'});}
function Harness({path='/pet/1',revision=0}){const request=useData(path);current=request;return React.createElement(DataState,{request},React.createElement('div',null,React.createElement('span',{'data-value':true},request.data?.title||''),React.createElement(Child)));}
const context=revision=>({user:{userId:1},revision,refresh:()=>{},refreshUser:async()=>{},notify:()=>{}});
async function render(path='/pet/1',revision=0){await act(async()=>{root.render(React.createElement(AppContext.Provider,{value:context(revision)},React.createElement(Harness,{path,revision})));});}
async function reply(index,data,code=200){await act(async()=>{calls[index].resolve(new Response(JSON.stringify({code,data,message:code===200?'ok':'unavailable'})));await new Promise(setImmediate);});}
beforeEach(()=>{queryCache.clear();localStorage.clear();localStorage.setItem('token','session-a');calls=[];mounts=0;unmounts=0;host=document.createElement('div');document.body.appendChild(host);root=createRoot(host);globalThis.fetch=(path,options)=>new Promise(resolve=>calls.push({path,options,resolve}));});
afterEach(async()=>{await act(async()=>root.unmount());host.remove();});
after(async()=>{await server.close();dom.window.close();});

test('background refresh keeps the same content node, draft and mounted children',async()=>{
 await render();assert.equal(calls.length,1);await reply(0,{title:'old'});const input=host.querySelector('input');input.value='typing now';
 await act(async()=>current.reload());assert.equal(calls.length,2);assert.equal(current.loading,false);assert.equal(current.refreshing,true);assert.equal(host.querySelector('input'),input);assert.equal(input.value,'typing now');assert.equal(unmounts,0);
 await reply(1,{title:'updated'});assert.equal(host.querySelector('[data-value]').textContent,'updated');assert.equal(host.querySelector('input'),input);assert.equal(mounts,1);
});
test('return to a recently visited resource is immediate and does not refetch',async()=>{
 await render('/pet/1');await reply(0,{title:'first'});await render('/pet/2');assert.equal(host.textContent.includes('first'),false);await reply(1,{title:'second'});await render('/pet/1');assert.equal(calls.length,2);assert.equal(host.querySelector('[data-value]').textContent,'first');assert.equal(current.loading,false);
});
test('out-of-order response never overwrites another detail',async()=>{
 await render('/pet/1');await render('/pet/2');await reply(1,{title:'second'});await reply(0,{title:'first'});assert.equal(host.querySelector('[data-value]').textContent,'second');
});
test('message-only refresh does not request or unmount pet detail',async()=>{
 await render('/pet/1',0);await reply(0,{title:'detail'});const input=host.querySelector('input');await render('/pet/1',1);assert.equal(calls.length,1);assert.equal(host.querySelector('input'),input);
});
test('successful local update changes counters without another GET or child unmount',async()=>{
 await render();await reply(0,{title:'not liked',isLiked:false,likeCount:2});const input=host.querySelector('input');await act(async()=>current.update(old=>({...old,title:'liked',isLiked:true,likeCount:3})));assert.equal(calls.length,1);assert.equal(current.data.likeCount,3);assert.equal(host.querySelector('input'),input);assert.equal(unmounts,0);
});
test('background failure retains existing content and draft with retry feedback',async()=>{
 await render();await reply(0,{title:'kept'});const input=host.querySelector('input');await act(async()=>current.reload());await reply(1,null,500);assert.equal(host.querySelector('input'),input);assert.ok(host.textContent.includes('kept'));assert.ok(host.textContent.includes('更新失败'));
});
test('a different login never receives another session snapshot',async()=>{
 await render();await reply(0,{title:'private a'});localStorage.setItem('token','session-b');await render();assert.equal(host.textContent.includes('private a'),false);assert.equal(current.loading,true);assert.equal(calls.length,2);
});

test('uncached page shows accessible skeleton then fills with actual content',async()=>{
 await render();assert.ok(host.querySelector('.page-skeleton[aria-busy="true"]'));assert.equal(host.querySelector('[role="status"]').getAttribute('aria-label'),'正在加载页面内容');assert.equal(host.textContent.includes('正在加载，请稍候'),false);assert.equal(host.querySelector('input'),null);
 await reply(0,{title:'loaded content'});assert.equal(host.querySelector('.page-skeleton'),null);assert.equal(host.querySelector('[data-value]').textContent,'loaded content');
});

test('layout skeletons reserve cards, detail columns and chat structure without interactive controls',async()=>{
 for(const [kind,selector,count] of [['cards','.pet-card',6],['detail','.detail-layout',1],['chat','.skeleton-bubble',3],['table','.skeleton-list-item',7]]){
  await act(async()=>root.render(React.createElement(PageSkeleton,{kind})));assert.equal(host.querySelectorAll(selector).length,count);assert.equal(host.querySelectorAll('button,input,a').length,0);assert.ok(host.querySelector('[aria-hidden="true"]'));
 }
});
