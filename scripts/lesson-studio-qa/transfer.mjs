import fs from 'node:fs/promises';
import path from 'node:path';
import {pathToFileURL} from 'node:url';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';

// Только локальный изолированный стенд: production-адрес сюда передать нельзя.
const repo=path.resolve(import.meta.dirname,'../../..','kz-textboox-parser');
const {DamulabClient}=await import(pathToFileURL(path.join(repo,'lesson-studio/damulab-client.mjs')));
const {validateLesson,buildExport}=await import(pathToFileURL(path.join(repo,'lesson-studio/core.mjs')));
const {compareHtml}=await import(pathToFileURL(path.join(repo,'lesson-studio/html-fidelity.mjs')));
const {hasOpenReviewIssues}=await import(pathToFileURL(path.join(repo,'lesson-studio/review.mjs')));
const run=path.resolve(process.argv[2]);
const mode=process.argv[3]??'upload';
const origin='https://localhost:18443';
const root=path.join(run,'lessons');
const evidence=path.join(run,'evidence');
const ids=['decimal-operations-grade5','kimak-khaganate-grade6'];
async function walk(dir,result=[]){for(const e of await fs.readdir(dir,{withFileTypes:true})){if(e.name.startsWith('.'))continue;const file=path.join(dir,e.name);if(e.isDirectory())await walk(file,result);else if(file.endsWith('.lesson.json'))result.push(file);}return result;}
if(mode==='upload'){
 await fs.mkdir(root,{recursive:true});
 const snapshot=path.join(repo,'lesson-studio/.local/release-20261023/source/lessons');
 for(const file of await walk(snapshot)){
  const doc=JSON.parse(await fs.readFile(file,'utf8'));if(!ids.includes(doc.id))continue;
  if(!await fs.stat(path.join(root,doc.id)).catch(()=>null))await fs.cp(path.dirname(file),path.join(root,doc.id),{recursive:true});
 }
}
const items=[];
for(const file of await walk(root)){const raw=await fs.readFile(file,'utf8');const doc=JSON.parse(raw);items.push({file,raw,doc,id:doc.id,hash:createHash('sha256').update(raw).digest('hex')});}
assert.equal(items.length,2);
const client=new DamulabClient({rendererVersion:'2.0.1',find:async id=>{const item=items.find(i=>i.id===id);assert.ok(item);return item;},validate:validateLesson,
 readAsset:async(item,asset)=>fs.readFile(path.resolve(path.dirname(item.file),asset.path)),
 appendJournal:async(item,entry)=>fs.appendFile(path.join(path.dirname(item.file),'qa-journal.jsonl'),JSON.stringify(entry)+'\n'),
 reviewReady:async item=>{const review=JSON.parse(await fs.readFile(path.join(path.dirname(item.file),'04-Ревью.json'),'utf8'));return review.lessonSha256===item.hash&&review.verdict==='ready_for_preview'&&!hasOpenReviewIssues(review);}});
await client.connect({baseUrl:origin,email:'admin@damulab.kz',password:'password'});
assert.equal(client.status().capabilities.rendererVersion,'2.0.1');
let state=mode==='upload'?{checkedAt:new Date().toISOString(),origin,capabilities:client.status().capabilities,lessons:[]}:JSON.parse(await fs.readFile(path.join(evidence,'transfer.json'),'utf8'));
const catalog=await client.catalog();
if(mode==='upload'){
 for(const item of items){
  assert.equal(validateLesson(item.doc).ok,true);assert.equal(await client.reviewReady(item),true);
  const grade=catalog.grades.find(g=>g.gradeNo===item.doc.grade);
  const subject=catalog.subjects.find(s=>item.doc.grade===5?s.code==='math':s.titleRu==='История Казахстана');assert.ok(subject);assert.ok(grade);
  const {topics}=await client.catalog({subjectId:subject.id,gradeId:grade.id});
  // Справочник считан с прода; новые QA-темы создаются только при отсутствии точного названия.
  let topic=topics.find(t=>t.titleRu===item.doc.title.ru);
  if(!topic)topic=await client.request('/api/admin/topics',{method:'POST',body:{subjectId:subject.id,gradeId:grade.id,code:'studio-qa-'+item.id,titleRu:item.doc.title.ru,titleKk:item.doc.title.kk}});
  const selection={subjectId:subject.id,gradeId:grade.id,topicId:topic.id,controlMode:'NONE',questionCount:3};
  const plan=await client.prepare(item.id,selection),uploaded=await client.upload(item.id,plan.planId);
  assert.equal(uploaded.verified,true);assert.equal(uploaded.status,'draft');
  const again=await client.prepare(item.id,selection),repeated=await client.upload(item.id,again.planId);
  assert.equal(repeated.unchanged,true);assert.equal(repeated.lectureId,uploaded.lectureId);
  const readback=await client.lookup(item.id);
  await fs.writeFile(path.join(evidence,item.id+'-readback.json'),JSON.stringify(readback,null,2));
  state.lessons.push({id:item.id,sha256:item.hash,selection,mapping:uploaded,repeatUnchanged:repeated.unchanged});
  console.log(JSON.stringify({id:item.id,lectureId:uploaded.lectureId,verified:true,ru:uploaded.comparisons.ru.ok,kk:uploaded.comparisons.kk.ok,repeatUnchanged:true}));
 }
 await fs.writeFile(path.join(evidence,'transfer.json'),JSON.stringify(state,null,2));
}else{
 const checks=[];
 for(const entry of state.lessons){
  const item=items.find(i=>i.id===entry.id),remote=await client.lookup(entry.id);
  const comparisons=Object.fromEntries(['ru','kk'].map(lang=>[lang,compareHtml(buildExport(item.doc,lang),remote.lecture[lang==='ru'?'contentRu':'contentKk'])]));
  assert.ok(Object.values(comparisons).every(c=>c.ok),JSON.stringify(comparisons));
  assert.equal(remote.lecture.id,entry.mapping.lectureId);assert.equal(remote.lecture.status,'draft');
  let conflict=false;
  if(mode==='conflict'){
   try{await client.prepare(entry.id,entry.selection);}catch(e){assert.equal(e.code,'remote_changed');conflict=true;}
   assert.equal(conflict,true);
  }
  checks.push({id:entry.id,lectureId:remote.lecture.id,comparisons,conflict,revision:remote.revision});
 }
 await fs.writeFile(path.join(evidence,mode+'.json'),JSON.stringify({checkedAt:new Date().toISOString(),checks},null,2));console.log(JSON.stringify(checks));
}
// Публикация намеренно отсутствует: учебные пилоты остаются черновиками.
client.disconnect();
