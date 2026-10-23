import {readFileSync} from 'node:fs';
import {before,after,beforeEach,test} from 'node:test';
import assert from 'node:assert/strict';
import {initializeTestEnvironment,assertFails,assertSucceeds} from '@firebase/rules-unit-testing';
import {doc,setDoc,getDoc,deleteDoc,collection,getDocs,query,where,orderBy,serverTimestamp} from 'firebase/firestore';
import {ref,set,get,remove,update,serverTimestamp as rtTime} from 'firebase/database';
let env;
const projectId='demo-family-tracker';
before(async()=> {env=await initializeTestEnvironment({projectId,firestore:{rules:readFileSync('firestore.rules','utf8'),host:'127.0.0.1',port:8080},database:{rules:readFileSync('database.rules.json','utf8'),host:'127.0.0.1',port:9000}});});
after(async()=> {await env?.cleanup();});
const fs=(uid)=>uid?env.authenticatedContext(uid).firestore():env.unauthenticatedContext().firestore();
const db=(uid)=>uid?env.authenticatedContext(uid).database():env.unauthenticatedContext().database();
const point=(uid='member',circleId='owner')=>({uid,circleId,latitude:55.75,longitude:37.62,accuracy:25,speed:null,battery:80,deviceTime:Date.now()-1000,uploadedAt:serverTimestamp()});
const live=(uid='member',circleId='owner')=>({uid,circleId,latitude:55.75,longitude:37.62,accuracy:25,battery:80,deviceTime:Date.now()-1000,uploadedAt:rtTime(),intervalMs:60000});
const path='circles/owner/history/member/points/p1';
beforeEach(async()=> {
 await env.clearFirestore();await env.clearDatabase();
 await env.withSecurityRulesDisabled(async ctx=> {
  const f=ctx.firestore();const d=ctx.database();
  await setDoc(doc(f,'circles/owner'),{name:'Family',ownerUid:'owner'});
  for(const uid of ['owner','member']) {await setDoc(doc(f,`circles/owner/members/${uid}`),{name:uid});await setDoc(doc(f,`circles/owner/sharing/${uid}`),{enabled:true});}
  await setDoc(doc(f,path),point());
  await set(ref(d,'circles/owner'),{name:'Family',members:{owner:{name:'Owner'},member:{name:'Member'}},locations:{member:live()}});
 });
});
test('unauthenticated clients cannot read or write private data',async()=> {
 await assertFails(getDoc(doc(fs(null),path)));await assertFails(setDoc(doc(fs(null),path),point()));
 await assertFails(get(ref(db(null),'circles/owner/locations/member')));await assertFails(set(ref(db(null),'circles/owner/locations/member'),live()));
});
test('other Circle and pending requester cannot read live locations or history',async()=> {
 await assertSucceeds(set(ref(db('stranger'),'circles/owner/requests/stranger'),{name:'Stranger'}));
 await assertFails(getDoc(doc(fs('stranger'),path)));await assertFails(get(ref(db('stranger'),'circles/owner/locations/member')));
 await assertFails(get(ref(db('stranger'),'circles/owner/members')));
});
test('approved members can read live and active daily history; parent RTDB enumeration denied',async()=> {
 await assertSucceeds(get(ref(db('owner'),'circles/owner/locations/member')));
 await assertFails(get(ref(db('owner'),'circles/owner/locations')));
 const q=query(collection(fs('owner'),'circles/owner/history/member/points'),where('deviceTime','>=',Date.now()-86400000),where('deviceTime','<',Date.now()+1000),orderBy('deviceTime'));
 const result=await assertSucceeds(getDocs(q));assert.equal(result.size,1);
});
test('users cannot self-approve in either database or forge owner metadata',async()=> {
 await assertFails(setDoc(doc(fs('stranger'),'circles/owner/members/stranger'),{name:'Fake'}));
 await assertFails(set(ref(db('stranger'),'circles/owner/members/stranger'),{name:'Fake'}));
 await assertFails(setDoc(doc(fs('stranger'),'circles/owner'),{name:'Taken',ownerUid:'stranger'}));
 await assertFails(set(ref(db('stranger'),'circles/owner/name'),'Taken'));
});
test('a user can create their own Circle, not an owner identity belonging to another user',async()=> {
 await assertSucceeds(setDoc(doc(fs('newowner'),'circles/newowner'),{name:'New',ownerUid:'newowner'}));
 await assertSucceeds(setDoc(doc(fs('newowner'),'circles/newowner/members/newowner'),{name:'New'}));
 await assertSucceeds(set(ref(db('newowner'),'circles/newowner/name'),'New'));
 await assertSucceeds(set(ref(db('newowner'),'circles/newowner/members/newowner'),{name:'New'}));
});
test('owner can approve and reject requests; non-owner cannot approve another user',async()=> {
 await assertSucceeds(set(ref(db('stranger'),'circles/owner/requests/stranger'),{name:'Stranger'}));
 await assertFails(setDoc(doc(fs('member'),'circles/owner/members/stranger'),{name:'Stranger'}));
 await assertFails(set(ref(db('member'),'circles/owner/members/stranger'),{name:'Stranger'}));
 await assertSucceeds(setDoc(doc(fs('owner'),'circles/owner/members/stranger'),{name:'Stranger'}));
 await assertSucceeds(update(ref(db('owner'),'circles/owner'),{'members/stranger':{name:'Stranger'},'requests/stranger':null}));
 await assertSucceeds(getDoc(doc(fs('stranger'),path)));
});
test('forged identity, invalid coordinates, timestamps, intervals and extra fields are rejected',async()=> {
 await assertFails(setDoc(doc(fs('owner'),path),point('owner')));
 await assertFails(set(ref(db('owner'),'circles/owner/locations/member'),live('owner')));
 for(const invalid of [{latitude:91},{longitude:-181},{accuracy:-1},{battery:101},{deviceTime:Date.now()+120000},{uid:'owner'},{circleId:'other'},{extra:true}]) {
  await assertFails(setDoc(doc(fs('member'),'circles/owner/history/member/points/bad'),{...point(),...invalid}));
  await assertFails(set(ref(db('member'),'circles/owner/locations/member'),{...live(),...invalid}));
 }
 await assertFails(setDoc(doc(fs('member'),'circles/owner/history/member/points/bad'),{...point(),speed:-1}));
 await assertFails(set(ref(db('member'),'circles/owner/locations/member'),{...live(),intervalMs:1000}));
});
test('history uploads are idempotent, preserve original timestamp and reject edits',async()=> {
 const f=fs('member');const p=point();const r=doc(f,'circles/owner/history/member/points/idempotent');
 await assertSucceeds(setDoc(r,p));await assertSucceeds(setDoc(r,{...p,uploadedAt:serverTimestamp()}));
 assert.equal((await getDoc(r)).data().deviceTime,p.deviceTime);
 await assertFails(setDoc(r,{...p,latitude:1,uploadedAt:serverTimestamp()}));
});
test('stopping sharing hides history from peers but preserves self access and deletion',async()=> {
 await assertSucceeds(setDoc(doc(fs('member'),'circles/owner/sharing/member'),{enabled:false}));
 await assertFails(getDoc(doc(fs('owner'),path)));
 await assertSucceeds(getDoc(doc(fs('member'),path)));
 await assertFails(setDoc(doc(fs('owner'),'circles/owner/sharing/member'),{enabled:true}));
 await assertSucceeds(deleteDoc(doc(fs('member'),path)));
});
test('revoked membership blocks reads and new writes in both databases',async()=> {
 await assertSucceeds(deleteDoc(doc(fs('owner'),'circles/owner/members/member')));
 await assertSucceeds(update(ref(db('owner'),'circles/owner'),{'members/member':null,'locations/member':null}));
 await assertFails(getDoc(doc(fs('member'),'circles/owner/history/owner/points/anything')));
 await assertFails(setDoc(doc(fs('member'),'circles/owner/history/member/points/new'),point()));
 await assertFails(get(ref(db('member'),'circles/owner/locations/owner')));
 await assertFails(set(ref(db('member'),'circles/owner/locations/member'),live()));
 await assertFails(getDoc(doc(fs('owner'),path)));
 await assertSucceeds(remove(ref(db('member'),'circles/owner/locations/member')));
});
test('partial approval never grants access in the database still awaiting approval',async()=> {
 await assertSucceeds(setDoc(doc(fs('owner'),'circles/owner/members/stranger'),{name:'Stranger'}));
 await assertSucceeds(getDoc(doc(fs('stranger'),path)));
 await assertFails(get(ref(db('stranger'),'circles/owner/locations/member')));
});
test('non-owner can leave, but cannot remove a different member or delete their history',async()=> {
 await assertFails(deleteDoc(doc(fs('member'),'circles/owner/members/owner')));
 await assertFails(remove(ref(db('member'),'circles/owner/members/owner')));
 await assertFails(deleteDoc(doc(fs('owner'),path)));
 await assertSucceeds(deleteDoc(doc(fs('member'),'circles/owner/members/member')));
 await assertSucceeds(remove(ref(db('member'),'circles/owner/members/member')));
});
test('unapproved users cannot publish location or enable sharing',async()=> {
 await assertFails(set(ref(db('stranger'),'circles/owner/locations/stranger'),live('stranger')));
 await assertFails(setDoc(doc(fs('stranger'),'circles/owner/history/stranger/points/new'),point('stranger')));
 await assertFails(setDoc(doc(fs('stranger'),'circles/owner/sharing/stranger'),{enabled:true}));
 await assertSucceeds(setDoc(doc(fs('stranger'),'circles/owner/sharing/stranger'),{enabled:false}));
});
