import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
const origin='https://localhost:18443';
const results=[];
function session(){const cookies=new Map();return async(route,{body,method='GET',csrf,contentType='application/json'}={})=>{
 const headers={Accept:'application/json'};if(cookies.size)headers.Cookie=[...cookies].map(([k,v])=>`${k}=${v}`).join('; ');
 if(body!==undefined)headers['Content-Type']=contentType;if(csrf)headers['X-CSRF-TOKEN']=csrf;
 const response=await fetch(origin+route,{method,headers,redirect:'manual',body:body===undefined?undefined:JSON.stringify(body)});
 const setCookie=response.headers.getSetCookie();for(const c of setCookie){const[k,...v]=c.split(';')[0].split('=');cookies.set(k,v.join('='));}
 return{status:response.status,setCookie,location:response.headers.get('location'),text:await response.text()};
};}
const anon=session(),student=session(),admin=session();
const anonymous=await anon('/api/admin/lectures/studio/capabilities');
results.push({name:'anonymous capabilities',actual:anonymous.status,location:anonymous.location});
assert.ok([401,403].includes(anonymous.status)||(anonymous.status===302&&new URL(anonymous.location,origin).pathname==='/login'));
const login=await student('/api/auth/login',{method:'POST',body:{email:'student@damulab.kz',password:'password'}});assert.equal(login.status,200);
assert.ok(login.setCookie.some(c=>/Secure/i.test(c)&&/HttpOnly/i.test(c)&&/SameSite=Lax/i.test(c)));
results.push({name:'HTTPS session Secure HttpOnly SameSite=Lax',passed:true});
const cap=await student('/api/admin/lectures/studio/capabilities');assert.equal(cap.status,403);results.push({name:'STUDENT cannot access administrative capabilities',actual:cap.status});
for(const id of [1,2]){const r=await student('/student/lectures/'+id);assert.ok([403,404].includes(r.status));results.push({name:'STUDENT cannot read pilot draft '+id,actual:r.status});}
await admin('/api/auth/login',{method:'POST',body:{email:'admin@damulab.kz',password:'password'}});
const noCsrf=await admin('/api/admin/lectures/studio/sync',{method:'POST',body:{}});assert.equal(noCsrf.status,403);results.push({name:'ADMIN write without CSRF is rejected',actual:noCsrf.status});
for(const route of ['/admin/lectures','/admin/subjects','/admin/topics','/admin/questions','/admin/settings/ai']){const r=await admin(route);assert.equal(r.status,200);results.push({name:route,actual:r.status});}
for(const route of ['/student','/student/tests','/student/analytics','/student/quiz','/student/speaking']){const r=await student(route);assert.equal(r.status,200);results.push({name:route,actual:r.status,location:r.location});}
const run=path.resolve(process.argv[2]);await fs.writeFile(path.join(run,'evidence/access.json'),JSON.stringify({checkedAt:new Date().toISOString(),results},null,2));console.log(JSON.stringify(results));
