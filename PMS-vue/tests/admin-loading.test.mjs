import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import ts from 'typescript';

const source=readFileSync(new URL('../src/views/audit/index.vue',import.meta.url),'utf8');
const script=source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1];
const tree=ts.createSourceFile('audit.ts',script,ts.ScriptTarget.Latest,true);
const compile=node=>ts.transpileModule(node.getText(tree),{compilerOptions:{target:ts.ScriptTarget.ES2022}}).outputText;
const declaration=name=>tree.statements.find(s=>ts.isVariableStatement(s)&&s.declarationList.declarations.some(d=>d.name.getText(tree)===name));

test('entering admin loads pending only; statistics remain deferred',()=>{
  const calls=[];
  const mounted=tree.statements.find(s=>ts.isExpressionStatement(s)&&ts.isCallExpression(s.expression)&&s.expression.expression.getText(tree)==='onMounted');
  const context={onMounted:fn=>fn(),fetchAuditList:()=>calls.push('pending'),fetchOverviewData:()=>calls.push('stats'),window:{addEventListener:()=>{}},handleResize:()=>{}};
  for(const key of ['dayDate','weekDate','monthDate','yearDate','customDateRange'])context[key]={value:null};
  vm.runInNewContext(compile(mounted),context);
  assert.deepEqual(calls,['pending']);
  assert.ok(context.dayDate.value);
});

test('statistics tab requests overview and realtime data on demand',()=>{
  const calls=[];
  const context={activeTab:{value:'stats'},setTimeout:fn=>fn(),fetchOverviewData:()=>calls.push('overview'),fetchRealtimeData:()=>calls.push('realtime')};
  vm.runInNewContext(compile(declaration('handleTabChange'))+';handleTabChange();',context);
  assert.deepEqual(calls,['overview','realtime']);
});

test('slow statistics do not toggle pending list loading state',async()=>{
  let release;
  const response=new Promise(resolve=>release=resolve);
  const context={period:{value:'day'},dayDate:{value:'2026-10-23'},loading:{value:false},overviewLoading:{value:false},trendLoading:{value:false},overviewData:{value:null},activeTab:{value:'pending'},trendChartRef:{value:null},getStatistics:()=>response,nextTick:async()=>{},ElMessage:{error:()=>{}},console:{log:()=>{},error:()=>{}}};
  const load=vm.runInNewContext(compile(declaration('fetchOverviewData'))+';fetchOverviewData;',context);
  const pending=load();
  assert.equal(context.overviewLoading.value,true);
  assert.equal(context.loading.value,false);
  release({code:200,data:{}});await pending;
  assert.equal(context.overviewLoading.value,false);
  assert.equal(context.loading.value,false);
});
