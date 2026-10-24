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
const {useData,DataState,AppContext,Button}=await server.ssrLoadModule('/src/core.tsx');
const {AtomicRoutes,useRouteLocation}=await server.ssrLoadModule('/src/routeTransition.tsx');
const {PrivateChat,AIChat}=await server.ssrLoadModule('/src/chat.tsx');
dom.window.HTMLElement.prototype.scrollIntoView=()=>{};
const {DiaryCard,Messages}=await server.ssrLoadModule('/src/social.tsx');
const {default:Admin}=await server.ssrLoadModule('/src/admin.tsx');
dom.window.HTMLDialogElement.prototype.showModal=function(){this.setAttribute('open','');};
dom.window.HTMLDialogElement.prototype.close=function(){this.removeAttribute('open');};
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
 const like=[...host.querySelectorAll('button')].find(b=>b.getAttribute('aria-label')==='点赞');assert.equal(like.disabled,true);
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

test('reference diary comments expand in-place and preserve a draft after collapsing',async()=>{
 const post={id:7,content:'real post',commentCount:0,isLiked:false,likeCount:2,images:[]};
 await act(async()=>root.render(React.createElement(AppContext.Provider,{value:context(0)},React.createElement(DiaryCard,{post}))));assert.equal(calls.length,0);
 const toggle=[...host.querySelectorAll('button')].find(b=>b.textContent.includes('条评论'));await act(async()=>toggle.click());assert.equal(calls.length,1);assert.ok(calls[0].path.includes('targetType=daily'));await reply(0,{records:[],total:0});const draft=host.querySelector('textarea');await act(async()=>{Object.getOwnPropertyDescriptor(dom.window.HTMLTextAreaElement.prototype,'value').set.call(draft,'keep this draft');draft.dispatchEvent(new dom.window.Event('input',{bubbles:true}));});
 await act(async()=>toggle.click());assert.equal(toggle.getAttribute('aria-expanded'),'false');await act(async()=>toggle.click());assert.equal(host.querySelector('textarea'),draft);assert.equal(draft.value,'keep this draft');assert.equal(calls.length,1);
 const like=host.querySelector('button[aria-label="点赞"]');await act(async()=>like.click());assert.ok(calls[1].path.endsWith('/daily/7/like'));await reply(1,true);assert.equal(like.getAttribute('aria-pressed'),'true');assert.ok(like.textContent.includes('3 赞'));assert.ok(like.classList.contains('is-liked'));
});

test('reference AI layout keeps all three real tools in the existing chat header',async()=>{
 await act(async()=>root.render(React.createElement(AppContext.Provider,{value:context(0)},React.createElement(AIChat))));
 const tools=host.querySelector('.ai-workspace .chat-head .ai-tools');assert.ok(tools);assert.deepEqual([...tools.querySelectorAll('button')].map(b=>b.textContent.trim()),['长期记忆','清除记忆','转人工']);assert.ok(host.querySelector('.ai-new-chat'));assert.ok(host.querySelector('.ai-mobile-close'));assert.ok(host.querySelector('textarea[aria-label="给 AI 的问题"]'));assert.ok(host.textContent.includes('Shift+Enter'));assert.equal(calls.length,2);assert.equal(calls.some(c=>c.options.method!=='GET'),false);
});


async function replyWhere(predicate,data){const index=calls.findIndex(c=>!c.replied && predicate(c.path,c));assert.ok(index>=0,'expected request exists');calls[index].replied=true;await reply(index,data);}
async function adminFixture(){
 await act(async()=>root.render(React.createElement(AppContext.Provider,{value:{...context(0),user:{userId:1,role:1}}},React.createElement(Admin))));
 await replyWhere(p=>p.includes('/admin/audit/pending?') && p.endsWith('pageSize=1'),{total:14,records:[]});
 await replyWhere(p=>p.includes('/report/list?') && p.includes('pageSize=1&status=0'),{total:3,records:[]});
 await replyWhere(p=>p.includes('/admin/audit/pending?') && p.includes('pageSize=12'),{total:14,records:[{id:7,title:'real pending',targetType:'adopt',user:{id:2,nickName:'pet friend'},createTime:'2026-10-24',images:[]}]});await flushFrames();
}
async function adminTab(name){await act(async()=>[...host.querySelectorAll('.admin-nav button')].find(b=>b.querySelector('span').textContent===name).click());}

test('reference governance heading, six exact icons and real totals replace prototype counts',async()=>{
 await adminFixture();assert.equal(host.querySelector('.governance-heading h1').textContent,'管理后台 · 内容审核与社区治理');assert.ok(host.textContent.includes('严格把关领养救助真实性，保障宠友权益与社区健康环境'));assert.ok(host.textContent.includes('管理员权限生效中'));
 const tabs=[...host.querySelectorAll('.admin-nav button')];assert.deepEqual(tabs.map(b=>b.querySelector('span').textContent),['待审核内容','审核历史','用户管理','举报处理','数据统计','公告管理']);const icons=['lucide-clock','lucide-circle-check','lucide-users','lucide-triangle-alert','lucide-chart-column','lucide-megaphone'];tabs.forEach((b,i)=>{assert.ok(b.querySelector('svg').classList.contains(icons[i]));assert.equal(b.querySelector('svg').getAttribute('width'),'16');});assert.equal(host.querySelector('[aria-label="待审核总数"]').textContent,'14');assert.equal(host.querySelector('[aria-label="待处理举报总数"]').textContent,'3');assert.equal(host.querySelectorAll('.governance-pending .governance-row').length,1);
});

test('pending approval retains real audit contract and refreshes global badges',async()=>{
 await adminFixture();await act(async()=>[...visibleStage().querySelectorAll('button')].find(b=>b.textContent==='通过').click());const post=calls.find(c=>c.options.method==='POST');assert.ok(post.path.includes('/admin/audit/batch-approve?targetType=adopt'));assert.deepEqual(JSON.parse(post.options.body),{ids:['7'],rejectReason:null});
 await replyWhere((p,c)=>c.options.method==='POST',true);await replyWhere(p=>p.includes('/admin/audit/pending?') && p.endsWith('pageSize=1'),{total:13,records:[]});await replyWhere(p=>p.includes('/report/list?') && p.includes('status=0'),{total:3,records:[]});assert.equal(host.querySelector('[aria-label="待审核总数"]').textContent,'13');
});

test('user management uses reference avatar rows while preserving selection and reset confirmation',async()=>{
 await adminFixture();await adminTab('用户管理');await replyWhere(p=>p.includes('/admin/users?'),{total:1,records:[{userId:4,nickName:'real user',userName:'real',phone:'123',status:1}]});await flushFrames();assert.equal(visibleStage().querySelector('table'),null);assert.ok(visibleStage().textContent.includes('real user'));const checkbox=visibleStage().querySelector('input[aria-label="选择 real user"]');await act(async()=>checkbox.click());const reset=[...visibleStage().querySelectorAll('button')].find(b=>b.textContent.includes('批量重置密码'));assert.equal(reset.disabled,false);globalThis.confirm=()=>false;await act(async()=>reset.click());assert.equal(calls.some(c=>c.options.method==='POST'),false);
});

test('report reject row action opens the existing form with reject selected and makes no premature write',async()=>{
 await adminFixture();await adminTab('举报处理');await replyWhere(p=>p.includes('/report/list?') && p.includes('pageSize=20'),{total:1,records:[{id:9,targetId:7,targetType:'pet',targetTitle:'reported post',reason:'real reason',status:0}]});await flushFrames();await act(async()=>[...visibleStage().querySelectorAll('button')].find(b=>b.textContent==='驳回举报').click());await replyWhere(p=>p.endsWith('/report/detail/9'),{id:9,status:0,targetType:'pet',targetId:7,targetTitle:'reported post',reason:'real reason'});assert.equal(host.querySelector('dialog select').value,'2');assert.equal(calls.some(c=>c.options.method==='POST'),false);
});

test('announcement reference edit icon fetches full draft before opening the editable form',async()=>{
 await adminFixture();await adminTab('公告管理');await replyWhere(p=>p.includes('/notice/admin/list?'),{total:1,records:[{id:3,title:'draft',status:0,type:1,priority:0}]});await flushFrames();assert.equal(visibleStage().querySelector('table'),null);await act(async()=>host.querySelector('button[aria-label="编辑公告：draft"]').click());await replyWhere(p=>p.endsWith('/notice/admin/3'),{id:3,title:'draft',status:0,type:1,priority:0,content:'complete draft body'});assert.equal(host.querySelector('dialog textarea').value,'complete draft body');assert.equal(calls.some(c=>c.options.method==='POST'),false);
});

test('statistics reference uses five true-value cards and real period queries',async()=>{
 await adminFixture();await adminTab('数据统计');await replyWhere(p=>p.includes('/admin/statistics/query?type=weekly'),{totalNewPosts:8,totalNewUsers:2,totalNewComments:5});await replyWhere(p=>p.endsWith('/admin/statistics/realtime'),{newUserCount:2,newPetPostCount:1,newActivityCount:1,newDailyPostCount:1,newCommentCount:5,newLikeCount:7,pendingAuditCount:14});await flushFrames();assert.equal(visibleStage().querySelectorAll('.governance-stat-cards .stat-tile').length,5);assert.equal(visibleStage().querySelectorAll('.governance-periods button').length,5);assert.ok(visibleStage().textContent.includes('数据重算 / 单日补录'));const month=[...visibleStage().querySelectorAll('.governance-periods button')].find(b=>b.textContent==='月');await act(async()=>month.click());assert.ok(calls.some(c=>c.path.includes('/admin/statistics/query?type=monthly&month=')));
});


test('action buttons keep content without spinner and block duplicate writes',async()=>{
 let finish,invocations=0;const action=()=>{invocations++;return new Promise(resolve=>finish=resolve);};
 await act(async()=>root.render(React.createElement(AppContext.Provider,{value:context(0)},React.createElement(Button,{action},'Action'))));
 const button=host.querySelector('button');await act(async()=>button.click());assert.equal(button.disabled,true);assert.equal(button.getAttribute('aria-busy'),'true');assert.equal(button.textContent,'Action');assert.equal(button.querySelector('svg'),null);await act(async()=>button.click());assert.equal(invocations,1);await act(async()=>finish());assert.equal(button.disabled,false);
});

test('detail unlike is immediate, reconciles server count and never reloads detail',async()=>{
 await act(async()=>root.render(React.createElement(AppContext.Provider,{value:context(0)},React.createElement(Detail,{id:'1'}))));
 await replyWhere(p=>p.endsWith('/pet/1'),{id:1,title:'pet',status:1,isLiked:true,likeCount:5,user:{id:2},images:[]});
 const button=host.querySelector('button.reaction-button[aria-pressed="true"]');const reads=calls.length;await act(async()=>button.click());assert.equal(button.getAttribute('aria-pressed'),'false');assert.equal(button.querySelector('.spin'),null);assert.equal(button.disabled,true);assert.ok(button.textContent.includes('4'));
 await replyWhere(p=>p.endsWith('/like'),{isLiked:false,likeCount:6});assert.equal(button.getAttribute('aria-pressed'),'false');assert.ok(button.textContent.includes('6'));assert.equal(calls.length,reads+1);assert.equal(button.disabled,false);
});

test('failed optimistic daily unlike restores original state and count',async()=>{
 const post={id:7,content:'post',commentCount:0,isLiked:true,likeCount:3,images:[]};await act(async()=>root.render(React.createElement(AppContext.Provider,{value:context(0)},React.createElement(DiaryCard,{post}))));
 const button=host.querySelector('button.reaction-button[aria-pressed="true"]');await act(async()=>button.click());assert.equal(button.getAttribute('aria-pressed'),'false');assert.ok(button.textContent.includes('2'));assert.equal(button.querySelector('.spin'),null);await reply(0,null,500);assert.equal(button.getAttribute('aria-pressed'),'true');assert.ok(button.textContent.includes('3'));assert.equal(button.disabled,false);
});

test('nested comment unlike updates in place without reloading comments',async()=>{
 const {Comments}=await server.ssrLoadModule('/src/content.tsx');await act(async()=>root.render(React.createElement(AppContext.Provider,{value:context(0)},React.createElement(Comments,{id:'1',type:'pet_post'}))));
 await reply(0,{total:2,records:[{id:10,content:'root',user:{id:2},replies:[{id:11,content:'reply',user:{id:3},isLiked:true,likeCount:2}]}]});
 const button=host.querySelector('button.reaction-button[aria-pressed="true"]');await act(async()=>button.click());assert.equal(button.getAttribute('aria-pressed'),'false');assert.ok(button.textContent.includes('1'));await reply(1,{isLiked:false,likeCount:1});assert.equal(calls.length,2);assert.equal(button.getAttribute('aria-pressed'),'false');assert.equal(host.textContent.includes('reply'),true);
});

test('notification reference keeps real categories, type icons and business links',async()=>{
 window.history.replaceState({},'', '/message');
 await act(async()=>root.render(React.createElement(AppContext.Provider,{value:context(0)},React.createElement(Messages))));
 await reply(0,{total:2,records:[{id:10,type:'LIKE',title:'real like',content:'liked your post',isRead:0,link:'/pet/7?commentId=3',businessId:7},{id:11,type:'COMMENT',title:'real comment',content:'reply',isRead:1,link:'/activity/63'}]});
 assert.ok(host.textContent.includes('消息中心 · 社区通知'));
 assert.equal(host.querySelectorAll('.notification-categories button').length,16);
 assert.equal(host.querySelectorAll('.notification-list .notification-row').length,2);
 assert.equal(host.querySelector('.type-like .notification-icon svg').getAttribute('width'),'16');
 assert.equal(host.querySelectorAll('.notification-dot').length,1);
 assert.ok(host.querySelector('.type-like .notification-target'));
 await act(async()=>[...host.querySelectorAll('.notification-categories button')].find(b=>b.textContent==='点赞').click());
 assert.ok(calls.some(c=>c.path.includes('/message/list?') && c.path.includes('type=LIKE')));
});

test('notification row uses actual link contract after marking unread message read',async()=>{
 globalThis.PopStateEvent=dom.window.PopStateEvent;window.history.replaceState({},'', '/message');
 await act(async()=>root.render(React.createElement(AppContext.Provider,{value:context(0)},React.createElement(Messages))));
 await reply(0,{total:1,records:[{id:12,type:'COMMENT',title:'reply',content:'body',isRead:0,link:'/activity/63?commentId=9'}]});
 await act(async()=>host.querySelector('.notification-row').click());assert.ok(calls[1].path.endsWith('/message/read/12'));assert.equal(calls[1].options.method,'PUT');
 await reply(1,true);assert.equal(window.location.pathname,'/pets/activity/63');assert.equal(window.location.search,'?commentId=9');assert.equal(host.querySelector('.notification-dot'),null);
});

test('notification mark all uses global read-all even with a category selected',async()=>{
 window.history.replaceState({},'', '/message');
 await act(async()=>root.render(React.createElement(AppContext.Provider,{value:context(0)},React.createElement(Messages))));
 await reply(0,{total:0,records:[]});
 await act(async()=>[...host.querySelectorAll('.notification-categories button')].find(b=>b.textContent==='点赞').click());
 await reply(1,{total:1,records:[{id:2,type:'LIKE',title:'like',isRead:0}]});
 await act(async()=>host.querySelector('.notification-read-all').click());
 assert.equal(calls[2].path,'/pet-system/message/read-all');assert.equal(calls[2].options.method,'PUT');await reply(2,true);assert.equal(host.querySelector('.notification-dot'),null);
});

test('notification targets accept legacy internal paths without guessing ambiguous business IDs',async()=>{
 const {notificationTarget}=await server.ssrLoadModule('/src/notifications.ts');
 assert.equal(notificationTarget({link:'/pet/8?commentId=2'}),'/pets/8?commentId=2');
 assert.equal(notificationTarget({link:'/activity/4'}),'/pets/activity/4');
 assert.equal(notificationTarget({link:'/daily/9'}),'/daily/9');
 assert.equal(notificationTarget({type:'NOTICE',businessId:3}),'/notice/3');
 assert.equal(notificationTarget({type:'LIKE',businessId:3}),null);
 assert.equal(notificationTarget({link:'javascript:alert(1)'}),null);
 assert.equal(notificationTarget({link:'//external.test/pet/2'}),null);
});

test('private reference layout renders avatars and bubbles without changing history requests',async()=>{
 window.history.replaceState({},'', '/private-message?conversation=42');
 await act(async()=>root.render(React.createElement(AppContext.Provider,{value:context(0)},React.createElement(PrivateChat))));
 await replyWhere(p=>p.includes('/private/conversations?'),{total:1,records:[{conversationId:42,otherUser:{userId:2,nickName:'friend'},lastMessage:'hello',unreadCount:2}]});
 await replyWhere(p=>p.includes('/private/messages?'),{total:2,records:[{id:1,fromUserId:2,content:'their message',createTime:'2026-10-24T12:01:00'},{id:2,fromUserId:1,content:'my message',createTime:'2026-10-24T12:02:00'}]});
 assert.ok(host.querySelector('.private-window'));assert.ok(host.textContent.includes('宠友私信会话'));assert.equal(host.querySelectorAll('.bubble-wrap').length,2);assert.equal(host.querySelectorAll('.bubble-wrap.me').length,1);assert.equal(host.querySelector('.bubble-wrap .avatar').style.width,'28px');
 assert.ok(host.querySelector('.chat-compose input'));assert.ok(host.querySelector('.private-options'));assert.equal(calls.length,2);
});
