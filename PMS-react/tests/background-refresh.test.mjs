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
const {useData,DataState,AppContext}=await server.ssrLoadModule('/src/core.tsx');
const {Detail,Editor}=await server.ssrLoadModule('/src/content.tsx');
let root,host,calls,current,mounts,unmounts;
function Child(){React.useEffect(()=>{mounts++;return()=>{unmounts++;};},[]);return React.createElement('input',{'aria-label':'draft',defaultValue:'unfinished draft'});}
function Harness({path='/pet/1',revision=0}){const request=useData(path);current=request;return React.createElement(DataState,{request},React.createElement('div',null,React.createElement('span',{'data-value':true},request.data?.title||''),React.createElement(Child)));}
const context=revision=>({user:{userId:1},revision,refresh:()=>{},refreshUser:async()=>{},notify:()=>{}});
async function render(path='/pet/1',revision=0){await act(async()=>{root.render(React.createElement(AppContext.Provider,{value:context(revision)},React.createElement(Harness,{path,revision})));});}
async function reply(index,data,code=200){await act(async()=>{calls[index].resolve(new Response(JSON.stringify({code,data,message:code===200?'ok':'unavailable'})));await new Promise(setImmediate);});}
beforeEach(()=>{localStorage.clear();localStorage.setItem('token','session-a');calls=[];mounts=0;unmounts=0;host=document.createElement('div');document.body.appendChild(host);root=createRoot(host);globalThis.fetch=(path,options)=>new Promise(resolve=>calls.push({path,options,resolve}));});
afterEach(async()=>{await act(async()=>root.unmount());host.remove();});
after(async()=>{await server.close();dom.window.close();});

test('background refresh keeps the same content node, draft and mounted children',async()=>{
 await render();assert.equal(calls.length,1);await reply(0,{title:'old'});const input=host.querySelector('input');input.value='typing now';
 await act(async()=>current.reload());assert.equal(calls.length,2);assert.equal(current.loading,false);assert.equal(current.refreshing,true);assert.equal(host.querySelector('input'),input);assert.equal(input.value,'typing now');assert.equal(unmounts,0);
 await reply(1,{title:'updated'});assert.equal(host.querySelector('[data-value]').textContent,'updated');assert.equal(host.querySelector('input'),input);assert.equal(mounts,1);
});
test('returning to a resource fetches fresh data without a cross-page snapshot',async()=>{
 await render('/pet/1');await reply(0,{title:'first'});await render('/pet/2');assert.equal(host.textContent.includes('first'),false);await reply(1,{title:'second'});await render('/pet/1');assert.equal(calls.length,3);assert.equal(host.textContent.includes('first'),false);assert.equal(current.loading,true);await reply(2,{title:'new first'});assert.equal(host.querySelector('[data-value]').textContent,'new first');
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

test('first request keeps the actual content container and input mounted while data fills in',async()=>{
 await render();const input=host.querySelector('input');const container=host.querySelector('.data-content');assert.ok(input);assert.ok(container.hasAttribute('inert'));assert.equal(host.querySelector('.page-skeleton'),null);assert.equal(host.querySelector('[role="status"]').textContent,'正在获取内容…');input.value='draft before response';
 await reply(0,{title:'loaded content'});assert.equal(host.querySelector('.data-content'),container);assert.equal(host.querySelector('input'),input);assert.equal(input.value,'draft before response');assert.equal(container.hasAttribute('inert'),false);assert.equal(host.querySelector('[data-value]').textContent,'loaded content');assert.equal(mounts,1);
});

test('real detail layout, static labels, sidebar and comment draft exist before data and retain DOM identity',async()=>{
 await act(async()=>root.render(React.createElement(AppContext.Provider,{value:context(0)},React.createElement(Detail,{id:'1'}))));
 const layout=host.querySelector('.detail-layout'),article=layout.querySelector('article'),sidebar=layout.querySelector('aside'),draft=host.querySelector('textarea');assert.ok(layout);assert.ok(article);assert.ok(sidebar);assert.ok(draft);assert.ok(host.textContent.includes('宠物名字'));assert.ok(host.textContent.includes('把陪伴带回家'));assert.ok(host.querySelector('.data-value[data-pending]'));assert.equal(host.querySelector('.page-skeleton'),null);
 const like=[...host.querySelectorAll('button')].find(b=>b.textContent.includes('点赞'));assert.equal(like.disabled,true);
 await act(async()=>{Object.getOwnPropertyDescriptor(dom.window.HTMLTextAreaElement.prototype,'value').set.call(draft,'preserved draft');draft.dispatchEvent(new dom.window.Event('input',{bubbles:true}));});const detailIndex=calls.findIndex(c=>c.path.endsWith('/pet/1'));assert.ok(detailIndex>=0);await reply(detailIndex,{id:1,title:'小猫找家',petName:'小橘',status:1,content:'real description',images:[],user:{id:2,nickName:'宠友'}});
 assert.equal(host.querySelector('.detail-layout'),layout);assert.equal(layout.querySelector('article'),article);assert.equal(layout.querySelector('aside'),sidebar);assert.equal(host.querySelector('textarea'),draft);assert.equal(draft.value,'preserved draft');assert.ok(host.textContent.includes('小猫找家'));assert.ok(host.textContent.includes('小橘'));assert.equal(like.disabled,false);
});

test('request failure and retry retain the same content container',async()=>{
 await render();const container=host.querySelector('.data-content'),input=host.querySelector('input');await reply(0,null,500);assert.equal(host.querySelector('.data-content'),container);assert.equal(container.hidden,true);await act(async()=>current.reload());assert.equal(host.querySelector('input'),input);assert.equal(container.hidden,false);await reply(1,{title:'recovered'});assert.equal(host.querySelector('input'),input);
});

test('edit form renders its real fields immediately and fills the same inputs after fetching',async()=>{
 await act(async()=>root.render(React.createElement(AppContext.Provider,{value:context(0)},React.createElement(Editor,{id:'1'}))));
 const form=host.querySelector('form'),title=host.querySelector('input[required]');assert.ok(form);assert.ok(title);assert.ok(host.textContent.includes('编辑领养 / 救助信息'));assert.ok(form.closest('[inert]'));assert.equal(title.value,'');
 await reply(0,{id:1,title:'editable title',status:1,content:'description',images:[]});assert.equal(host.querySelector('form'),form);assert.equal(host.querySelector('input[required]'),title);assert.equal(title.value,'editable title');assert.equal(form.closest('[inert]'),null);
});
