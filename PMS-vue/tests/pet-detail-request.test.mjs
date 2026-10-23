import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import ts from 'typescript';

const component=readFileSync(new URL('../src/views/pets/detail.vue',import.meta.url),'utf8');
const script=component.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1];
const tree=ts.createSourceFile('detail.ts',script,ts.ScriptTarget.Latest,true);
const declaration=tree.statements.find(statement=>ts.isVariableStatement(statement)&&statement.declarationList.declarations.some(item=>item.name.getText(tree)==='fetchPetDetail'));
const executable=ts.transpileModule(declaration.getText(tree),{compilerOptions:{target:ts.ScriptTarget.ES2022}}).outputText;
function fixture(outcome){
  const requests=[],errors=[];let comments=0;
  const context={route:{params:{id:'10'}},loading:{value:false},pet:{value:null},isLiked:{value:false},isCollected:{value:false},isFollowing:{value:false},
    getPetDetail:async id=>{requests.push('pet:'+id);if(outcome instanceof Error)throw outcome;return outcome;},
    getActivityDetail:async id=>{requests.push('activity:'+id);return {code:200,data:{id}};},
    fetchComments:async()=>{comments++;},scrollToComment:()=>{},ElMessage:{error:message=>errors.push(message)},console:{error:()=>{}}};
  const load=vm.runInNewContext(executable+';fetchPetDetail;',context);
  return {context,requests,errors,load,get comments(){return comments;}};
}
test('pet detail success performs only one content request',async()=>{const f=fixture({code:200,data:{id:10,user:{isFollow:true}}});await f.load();assert.deepEqual(f.requests,['pet:10']);assert.equal(f.comments,1);assert.equal(f.context.isFollowing.value,true);assert.equal(f.context.loading.value,false);});
test('pending or forbidden response never probes an activity with the same ID',async()=>{const f=fixture({code:403,message:'forbidden'});await f.load();assert.deepEqual(f.requests,['pet:10']);assert.equal(f.comments,0);assert.equal(f.errors.length,1);});
test('pet request error does not fetch another content type',async()=>{const f=fixture(new Error('offline'));await f.load();assert.deepEqual(f.requests,['pet:10']);assert.equal(f.comments,0);assert.equal(f.errors.length,1);assert.equal(f.context.loading.value,false);});
