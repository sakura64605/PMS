import {test} from 'node:test';
import assert from 'node:assert/strict';
import {moduleLoader} from '../src/moduleLoader.ts';

test('hover warm-up and navigation share one code download',async()=>{
 let calls=0,resolve;
 const load=moduleLoader(()=>{calls++;return new Promise(done=>{resolve=done;});});
 const first=load(),second=load();assert.equal(first,second);assert.equal(calls,1);
 const module={component:'ready'};resolve(module);assert.equal(await first,module);assert.equal(await load(),module);assert.equal(calls,1);
});
test('a failed warm-up does not poison a later navigation',async()=>{
 let calls=0;
 const load=moduleLoader(async()=>{if(++calls===1)throw new Error('offline');return {component:'ready'};});
 await assert.rejects(load(),/offline/);assert.deepEqual(await load(),{component:'ready'});assert.equal(calls,2);
});
