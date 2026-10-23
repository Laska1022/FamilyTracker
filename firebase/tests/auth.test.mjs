import {test} from 'node:test';
import assert from 'node:assert/strict';
import {initializeApp,deleteApp} from 'firebase/app';
import {getAuth,connectAuthEmulator,createUserWithEmailAndPassword,updateProfile,signOut,signInWithEmailAndPassword,onAuthStateChanged,sendPasswordResetEmail} from 'firebase/auth';
test('registration, profile, auth-state changes, sign-out, sign-in and reset work against Auth emulator',async()=> {
 const app=initializeApp({projectId:'demo-family-tracker',apiKey:'demo-api-key'},'auth-tests');
 const auth=getAuth(app);connectAuthEmulator(auth,'http://127.0.0.1:9099',{disableWarnings:true});
 const seen=[];const unsubscribe=onAuthStateChanged(auth,user=>seen.push(user?.uid ?? null));
 try {
  const email=`family-${Date.now()}@example.test`;
  const credential=await createUserWithEmailAndPassword(auth,email,'test-password-123');
  await updateProfile(credential.user,{displayName:'Семья'});const uid=credential.user.uid;
  assert.equal(auth.currentUser.displayName,'Семья');
  await signOut(auth);assert.equal(auth.currentUser,null);
  await signInWithEmailAndPassword(auth,email,'test-password-123');
  assert.equal(auth.currentUser.uid,uid);assert.equal(auth.currentUser.displayName,'Семья');
  await sendPasswordResetEmail(auth,email);
  assert.ok(seen.includes(uid));assert.ok(seen.includes(null));
 } finally {unsubscribe();await signOut(auth);await deleteApp(app);}
});
