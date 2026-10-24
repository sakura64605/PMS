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
const {AtomicRoutes,useRouteLocation}=await server.ssrLoadModule('/src/routeTransition.tsx');
const {PrivateChat}=await server.ssrLoadModule('/src/chat.tsx');
dom.window.HTMLElement.prototype.scrollIntoView=()=>{};
const {Detail,Editor}=await server.ssrLoadModule('/src/content.tsx');
let root,host,calls,current,mounts,unmounts;
let frames=new Map(),frameSequence=0,scrolls=0;
globalThis.requestAnimationFrame=callback=>{const id=++frameSequence;frames.set(id,callback);return id;};
globalThis.cancelAnimationFrame=id=>frames.delete(id);
window.scrollTo=()=>{scrolls++;};
async function flushFrames(){for(let i=0;i<6;i++)await act(async()=>{const callbacks=[...frames.values()];frames.clear();callbacks.forEach(fn=>fn(0));});}
function visibleStage(){return host.querySelector('.route-stage:not([hidden])');}
async function renderAtomic(path,renderPage=p=>React.createElement(Harness,{key:p,path:p})){
 await act(async()=>root.render(React.createElement(AppContext.Provider,{value:context(0)},React.createElement(AtomicRoutes,{path},renderPage))));
}
function Child(){React.useEffect(()=>{mounts++;return()=>{unmounts++;};},[]);return React.createElement('input',{'aria-label':'draft',defaultValue:'unfinished draft'});}
function Harness({path='/pet/1',revision=0}){const request=useData(path);current=request;return React.createElement(DataState,{request},React.createElement('div',null,React.createElement('span',{'data-value':true},request.data?.title||''),React.createElement(Child)));}
const context=revision=>({user:{userId:1},revision,refresh:()=>{},refreshUser:async()=>{},notify:()=>{}});
async function render(path='/pet/1',revision=0){await act(async()=>{root.render(React.createElement(AppContext.Provider,{value:context(revision)},React.createElement(Harness,{path,revision})));});}
async function reply(index,data,code=200){await act(async()=>{calls[index].resolve(new Response(JSON.stringify({code,data,message:code===200?'ok':'unavailable'})));await new Promise(setImmediate);});}
beforeEach(()=>{frames.clear();scrolls=0;localStorage.clear();localStorage.setItem('token','session-a');calls=[];mounts=0;unmounts=0;host=document.createElement('div');document.body.appendChild(host);root=createRoot(host);globalThis.fetch=(path,options)=>new Promise(resolve=>calls.push({path,options,resolve}));});
afterEach(async()=>{await act(async()=>root.unmount());host.remove();});
after(async()=>{await server.close();dom.window.close();});

test('background refresh keeps the same content node, draft and mounted children',async()=>{
 await render();assert.equal(calls.length,1);await reply(0,{title:'old'});const input=host.querySelector('input');input.value='typing now';
 await act(async()=>current.reload());assert.equal(calls.length,2);assert.equal(current.loading,false);assert.equal(current.refreshing,true);assert.equal(host.querySelector('input'),input);assert.equal(input.value,'typing now');assert.equal(unmounts,0);
 await reply(1,{title:'updated'});assert.equal(host.querySelector('[data-value]').textContent,'updated');assert.equal(host.querySelector('input'),input);assert.equal(mounts,1);
});
test('same mounted region retains prior results during a filter request without a cross-page cache',async()=>{
 await render('/pet/1');await reply(0,{title:'first'});const input=host.querySelector('input');await render('/pet/2');assert.equal(current.loading,false);assert.equal(host.querySelector('[data-value]').textContent,'first');assert.equal(host.querySelector('input'),input);await reply(1,{title:'second'});await render('/pet/1');assert.equal(calls.length,3);assert.equal(host.querySelector('[data-value]').textContent,'second');assert.equal(current.loading,false);await reply(2,{title:'new first'});assert.equal(host.querySelector('[data-value]').textContent,'new first');
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

test('navigation retains old visible page and draft until the fully loaded target replaces it once',async()=>{
 await renderAtomic('/pet/1');await reply(0,{title:'old page'});await flushFrames();const old=visibleStage(),draft=old.querySelector('input');draft.value='keep me';const initialScrolls=scrolls;
 await renderAtomic('/pet/2');await flushFrames();assert.equal(visibleStage(),old);assert.equal(draft.value,'keep me');assert.equal(scrolls,initialScrolls);assert.ok(old.textContent.includes('old page'));assert.equal(old.textContent.includes('正在获取内容'),false);
 await reply(1,{title:'new page'});await flushFrames();assert.notEqual(visibleStage(),old);assert.ok(visibleStage().textContent.includes('new page'));assert.equal(host.querySelectorAll('.route-stage').length,1);assert.equal(visibleStage().textContent.includes('正在获取内容'),false);assert.equal(scrolls,initialScrolls+1);
});

test('rapid navigation discards abandoned target and ignores its late response',async()=>{
 await renderAtomic('/pet/1');await reply(0,{title:'old'});await flushFrames();const old=visibleStage();await renderAtomic('/pet/2');await renderAtomic('/pet/3');assert.equal(calls[1].options.signal.aborted,true);await reply(1,{title:'abandoned'});await flushFrames();assert.equal(visibleStage(),old);await reply(2,{title:'latest'});await flushFrames();assert.ok(visibleStage().textContent.includes('latest'));assert.equal(host.textContent.includes('abandoned'),false);
});

test('lazy component download cannot reveal an intermediate page',async()=>{
 await renderAtomic('/pet/1');await reply(0,{title:'old'});await flushFrames();const old=visibleStage();let load;
 const Lazy=React.lazy(()=>new Promise(resolve=>{load=resolve;}));const page=p=>p==='/slow'?React.createElement(Lazy):React.createElement(Harness,{key:p,path:p});
 await renderAtomic('/slow',page);await flushFrames();assert.equal(visibleStage(),old);await act(async()=>load({default:()=>React.createElement(Harness,{path:'/pet/slow'})}));await flushFrames();assert.equal(visibleStage(),old);await reply(1,{title:'downloaded and loaded'});await flushFrames();assert.ok(visibleStage().textContent.includes('downloaded and loaded'));
});

test('requests started by data-dependent effects complete before target reveal',async()=>{
 await renderAtomic('/pet/1');await reply(0,{title:'old'});await flushFrames();const old=visibleStage();
 function Dependent(){const first=useData('/primary');const [secondary,setSecondary]=React.useState(null);React.useEffect(()=>{if(first.data)setSecondary('/secondary');},[first.data]);const second=useData(secondary);return React.createElement('article',null,first.data?.title,' ',second.data?.title);}
 await renderAtomic('/dependent',p=>p==='/dependent'?React.createElement(Dependent):React.createElement(Harness,{key:p,path:p}));await reply(1,{title:'primary'});await flushFrames();assert.equal(visibleStage(),old);assert.equal(calls.length,3);await reply(2,{title:'secondary'});await flushFrames();assert.ok(visibleStage().textContent.includes('primary secondary'));
});

test('nested admin-style sections hold outer navigation until their data is ready',async()=>{
 await renderAtomic('/pet/1');await reply(0,{title:'old'});await flushFrames();const old=visibleStage(),previousScrolls=scrolls;
 const page=p=>p==='/admin'?React.createElement(AtomicRoutes,{path:'users'},()=>React.createElement(Harness,{path:'/users'})):React.createElement(Harness,{key:p,path:p});
 await renderAtomic('/admin',page);await flushFrames();assert.equal(visibleStage(),old);await reply(1,{title:'admin users'});
 await act(async()=>{const callbacks=[...frames.values()];frames.clear();callbacks.forEach(fn=>fn(0));});assert.equal(scrolls,previousScrolls);
 await flushFrames();assert.ok(visibleStage().textContent.includes('admin users'));assert.equal(visibleStage().textContent.includes('正在获取内容'),false);
});

test('failed target request reveals a terminal error with retry instead of waiting forever',async()=>{
 await renderAtomic('/pet/1');await reply(0,{title:'old'});await flushFrames();await renderAtomic('/pet/2');await reply(1,null,500);await flushFrames();assert.ok(visibleStage().querySelector('[role="alert"]'));assert.ok(visibleStage().textContent.includes('重试'));
});

test('each prepared route reads its own query parameters while browser URL has moved on',async()=>{
 function Params(){const route=useRouteLocation();useData(route);return React.createElement('p',null,route);}
 await renderAtomic('/pet/1?type=old',()=>React.createElement(Params));await reply(0,{});await flushFrames();await renderAtomic('/pet/2?type=new',()=>React.createElement(Params));assert.equal(visibleStage().textContent,'/pet/1?type=old');await reply(1,{});await flushFrames();assert.equal(visibleStage().textContent,'/pet/2?type=new');
});

test('private conversation link fetches history in parallel with the conversation list',async()=>{
 await renderAtomic('/pet/1');await reply(0,{title:'old'});await flushFrames();const old=visibleStage();
 const page=p=>p.startsWith('/private-message')?React.createElement(PrivateChat):React.createElement(Harness,{key:p,path:p});
 await renderAtomic('/private-message?conversation=42',page);
 const list=calls.findIndex(c=>c.path.includes('/private/conversations?')),history=calls.findIndex(c=>c.path.includes('/private/messages?conversationId=42'));
 assert.ok(list>=0);assert.ok(history>=0);assert.equal(calls.length,3);assert.equal(visibleStage(),old);
 await reply(history,{records:[{id:1,content:'existing private history',fromUserId:2,createTime:'2026-10-24'}],total:1});await flushFrames();assert.equal(visibleStage(),old);
 await reply(list,{records:[{conversationId:42,otherUser:{id:2,nickName:'friend'},lastMessage:'existing private history'}],total:1});await flushFrames();assert.equal(calls.length,3);assert.ok(visibleStage().textContent.includes('existing private history'));assert.equal(visibleStage().textContent.includes('正在获取内容'),false);
});
