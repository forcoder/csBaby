const { Router } = require('express');
const { getDb, queryAll, queryOne, exec } = require('./db');
const { authMiddleware } = require('./auth');

const router = Router();
router.use(authMiddleware);

function toRule(r) {
  return { id:r.id, keyword:r.keyword, matchType:r.match_type, replyTemplate:r.reply_template,
    category:r.category, targetType:r.target_type, targetNamesJson:r.target_names_json,
    priority:r.priority, enabled:!!r.enabled, createdAt:r.created_at, updatedAt:r.updated_at,
    tenantId:r.tenant_id, syncVersion:r.sync_version, deleted:!!r.deleted };
}
function toModel(m) {
  return { id:m.id, modelType:m.model_type, modelName:m.model_name, apiKey:m.api_key,
    apiEndpoint:m.api_endpoint, temperature:m.temperature, maxTokens:m.max_tokens,
    isDefault:!!m.is_default, isEnabled:!!m.is_enabled, monthlyCost:m.monthly_cost,
    lastUsed:m.last_used, createdAt:m.created_at, tenantId:m.tenant_id,
    syncVersion:m.sync_version, deleted:!!m.deleted };
}
function toProfile(p) {
  if (!p) return null;
  return { userId:p.user_id, formalityLevel:p.formality_level, enthusiasmLevel:p.enthusiasm_level,
    professionalismLevel:p.professionalism_level, wordCountPreference:p.word_count_preference,
    commonPhrases:p.common_phrases, avoidPhrases:p.avoid_phrases, learningSamples:p.learning_samples,
    accuracyScore:p.accuracy_score, lastTrained:p.last_trained, createdAt:p.created_at,
    tenantId:p.tenant_id, syncVersion:p.sync_version, deleted:!!p.deleted };
}
function toApp(a) {
  return { packageName:a.package_name, appName:a.app_name, iconUri:a.icon_uri,
    isMonitored:!!a.is_monitored, createdAt:a.created_at, lastUsed:a.last_used,
    tenantId:a.tenant_id, syncVersion:a.sync_version, deleted:!!a.deleted };
}
function toScenario(s) {
  return { id:s.id, name:s.name, type:s.type, targetId:s.target_id,
    description:s.description, createdAt:s.created_at, tenantId:s.tenant_id,
    syncVersion:s.sync_version, deleted:!!s.deleted };
}
function toReply(h) {
  return { id:h.id, sourceApp:h.source_app, originalMessage:h.original_message,
    generatedReply:h.generated_reply, finalReply:h.final_reply,
    ruleMatchedId:h.rule_matched_id, modelUsedId:h.model_used_id,
    styleApplied:!!h.style_applied, sendTime:h.send_time, modified:!!h.modified,
    tenantId:h.tenant_id, syncVersion:h.sync_version, deleted:!!h.deleted };
}
function toBlacklist(b) {
  return { id:b.id, type:b.type, value:b.value, description:b.description,
    packageName:b.package_name, createdAt:b.created_at, isEnabled:!!b.is_enabled,
    tenantId:b.tenant_id, syncVersion:b.sync_version, deleted:!!b.deleted };
}

// 全量同步
router.get('/all', async (req, res) => {
  await getDb();
  const t = req.tenantId, now = Date.now();
  res.json({ code:0, message:'成功', data:{
    keywordRules: queryAll('SELECT * FROM keyword_rules WHERE tenant_id=? AND deleted=0 ORDER BY priority DESC, created_at DESC', [t]).map(toRule),
    aiModelConfigs: queryAll('SELECT * FROM ai_model_configs WHERE tenant_id=? AND deleted=0 ORDER BY is_default DESC, last_used DESC', [t]).map(toModel),
    userStyleProfile: toProfile(queryOne('SELECT * FROM user_style_profiles WHERE tenant_id=? AND deleted=0', [t])),
    appConfigs: queryAll('SELECT * FROM app_configs WHERE tenant_id=? AND deleted=0 ORDER BY last_used DESC', [t]).map(toApp),
    scenarios: queryAll('SELECT * FROM scenarios WHERE tenant_id=? AND deleted=0 ORDER BY created_at DESC', [t]).map(toScenario),
    replyHistory: queryAll('SELECT * FROM reply_history WHERE tenant_id=? AND deleted=0 ORDER BY send_time DESC LIMIT 500', [t]).map(toReply),
    messageBlacklist: queryAll('SELECT * FROM message_blacklist WHERE tenant_id=? AND deleted=0 ORDER BY created_at DESC', [t]).map(toBlacklist),
    serverTime: now
  }});
});

// 增量：获取变更
router.get('/changes', async (req, res) => {
  await getDb();
  const t = req.tenantId, since = parseInt(req.query.since) || 0, now = Date.now();
  const del = {};
  const dr = queryAll('SELECT id FROM keyword_rules WHERE tenant_id=? AND sync_version>? AND deleted=1', [t, since]);
  if (dr.length) del['keyword_rules'] = dr.map(r=>String(r.id));
  const dm = queryAll('SELECT id FROM ai_model_configs WHERE tenant_id=? AND sync_version>? AND deleted=1', [t, since]);
  if (dm.length) del['ai_model_configs'] = dm.map(r=>String(r.id));
  const da = queryAll('SELECT package_name FROM app_configs WHERE tenant_id=? AND sync_version>? AND deleted=1', [t, since]);
  if (da.length) del['app_configs'] = da.map(r=>String(r.package_name));
  const ds = queryAll('SELECT id FROM scenarios WHERE tenant_id=? AND sync_version>? AND deleted=1', [t, since]);
  if (ds.length) del['scenarios'] = ds.map(r=>String(r.id));
  const dh = queryAll('SELECT id FROM reply_history WHERE tenant_id=? AND sync_version>? AND deleted=1', [t, since]);
  if (dh.length) del['reply_history'] = dh.map(r=>String(r.id));
  const db = queryAll('SELECT id FROM message_blacklist WHERE tenant_id=? AND sync_version>? AND deleted=1', [t, since]);
  if (db.length) del['message_blacklist'] = db.map(r=>String(r.id));

  res.json({ code:0, message:'成功', data:{
    keywordRules: queryAll('SELECT * FROM keyword_rules WHERE tenant_id=? AND sync_version>?', [t, since]).map(toRule),
    aiModelConfigs: queryAll('SELECT * FROM ai_model_configs WHERE tenant_id=? AND sync_version>?', [t, since]).map(toModel),
    userStyleProfile: toProfile(queryOne('SELECT * FROM user_style_profiles WHERE tenant_id=? AND sync_version>?', [t, since])),
    appConfigs: queryAll('SELECT * FROM app_configs WHERE tenant_id=? AND sync_version>?', [t, since]).map(toApp),
    scenarios: queryAll('SELECT * FROM scenarios WHERE tenant_id=? AND sync_version>?', [t, since]).map(toScenario),
    replyHistory: queryAll('SELECT * FROM reply_history WHERE tenant_id=? AND sync_version>? ORDER BY send_time DESC LIMIT 500', [t, since]).map(toReply),
    messageBlacklist: queryAll('SELECT * FROM message_blacklist WHERE tenant_id=? AND sync_version>?', [t, since]).map(toBlacklist),
    deletedIds: del, serverTime: now, hasMore: false, nextCursor: null
  }});
});

// 增量：推送变更
router.post('/push', async (req, res) => {
  await getDb();
  const t = req.tenantId;
  const { keywordRules=[], aiModelConfigs=[], userStyleProfile, appConfigs=[], scenarios=[], replyHistory=[], messageBlacklist=[], deletedIds={}, baseVersion=0 } = req.body;
  const now = Date.now();
  const conflicts = [];

  for (const r of keywordRules) {
    const e = queryOne('SELECT sync_version,updated_at FROM keyword_rules WHERE id=? AND tenant_id=?', [r.id, t]);
    if (e && e.sync_version > baseVersion) conflicts.push({ entityType:'keyword_rule', entityId:String(r.id), serverVersion:e.sync_version, serverUpdatedAt:e.updated_at });
  }
  for (const m of aiModelConfigs) {
    const e = queryOne('SELECT sync_version,last_used FROM ai_model_configs WHERE id=? AND tenant_id=?', [m.id, t]);
    if (e && e.sync_version > baseVersion) conflicts.push({ entityType:'ai_model_config', entityId:String(m.id), serverVersion:e.sync_version, serverUpdatedAt:e.last_used });
  }
  if (userStyleProfile) {
    const e = queryOne('SELECT sync_version,last_trained FROM user_style_profiles WHERE user_id=? AND tenant_id=?', [userStyleProfile.userId, t]);
    if (e && e.sync_version > baseVersion) conflicts.push({ entityType:'style_profile', entityId:userStyleProfile.userId, serverVersion:e.sync_version, serverUpdatedAt:e.last_trained });
  }
  for (const a of appConfigs) {
    const e = queryOne('SELECT sync_version,last_used FROM app_configs WHERE package_name=? AND tenant_id=?', [a.packageName, t]);
    if (e && e.sync_version > baseVersion) conflicts.push({ entityType:'app_config', entityId:a.packageName, serverVersion:e.sync_version, serverUpdatedAt:e.last_used });
  }
  for (const s of scenarios) {
    const e = queryOne('SELECT sync_version,created_at FROM scenarios WHERE id=? AND tenant_id=?', [s.id, t]);
    if (e && e.sync_version > baseVersion) conflicts.push({ entityType:'scenario', entityId:String(s.id), serverVersion:e.sync_version, serverUpdatedAt:e.created_at });
  }
  for (const h of replyHistory) {
    const e = queryOne('SELECT sync_version,send_time FROM reply_history WHERE id=? AND tenant_id=?', [h.id, t]);
    if (e && e.sync_version > baseVersion) conflicts.push({ entityType:'reply_history', entityId:String(h.id), serverVersion:e.sync_version, serverUpdatedAt:e.send_time });
  }
  for (const b of messageBlacklist) {
    const e = queryOne('SELECT sync_version,created_at FROM message_blacklist WHERE id=? AND tenant_id=?', [b.id, t]);
    if (e && e.sync_version > baseVersion) conflicts.push({ entityType:'message_blacklist', entityId:String(b.id), serverVersion:e.sync_version, serverUpdatedAt:e.created_at });
  }

  try {
    // 使用事务保证原子性
    exec('BEGIN TRANSACTION');
    for (const r of keywordRules)
      exec('INSERT OR REPLACE INTO keyword_rules (id,keyword,match_type,reply_template,category,target_type,target_names_json,priority,enabled,created_at,updated_at,tenant_id,sync_version,deleted) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)',
        [r.id,r.keyword,r.matchType,r.replyTemplate,r.category,r.targetType,r.targetNamesJson,r.priority,r.enabled?1:0,r.createdAt,r.updatedAt,t,now,r.deleted?1:0]);
    for (const m of aiModelConfigs)
      exec('INSERT OR REPLACE INTO ai_model_configs (id,model_type,model_name,api_key,api_endpoint,temperature,max_tokens,is_default,is_enabled,monthly_cost,last_used,created_at,tenant_id,sync_version,deleted) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)',
        [m.id,m.modelType,m.modelName,m.apiKey,m.apiEndpoint,m.temperature,m.maxTokens,m.isDefault?1:0,m.isEnabled?1:0,m.monthlyCost,m.lastUsed,m.createdAt,t,now,m.deleted?1:0]);
    if (userStyleProfile)
      exec('INSERT OR REPLACE INTO user_style_profiles (user_id,formality_level,enthusiasm_level,professionalism_level,word_count_preference,common_phrases,avoid_phrases,learning_samples,accuracy_score,last_trained,created_at,tenant_id,sync_version,deleted) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)',
        [userStyleProfile.userId,userStyleProfile.formalityLevel,userStyleProfile.enthusiasmLevel,userStyleProfile.professionalismLevel,userStyleProfile.wordCountPreference,userStyleProfile.commonPhrases,userStyleProfile.avoidPhrases,userStyleProfile.learningSamples,userStyleProfile.accuracyScore,userStyleProfile.lastTrained,userStyleProfile.createdAt,t,now,userStyleProfile.deleted?1:0]);
    for (const a of appConfigs)
      exec('INSERT OR REPLACE INTO app_configs (package_name,app_name,icon_uri,is_monitored,created_at,last_used,tenant_id,sync_version,deleted) VALUES (?,?,?,?,?,?,?,?,?)',
        [a.packageName,a.appName,a.iconUri||null,a.isMonitored?1:0,a.createdAt,a.lastUsed,t,now,a.deleted?1:0]);
    for (const s of scenarios)
      exec('INSERT OR REPLACE INTO scenarios (id,name,type,target_id,description,created_at,tenant_id,sync_version,deleted) VALUES (?,?,?,?,?,?,?,?,?)',
        [s.id,s.name,s.type,s.targetId||null,s.description||null,s.createdAt,t,now,s.deleted?1:0]);
    for (const h of replyHistory)
      exec('INSERT OR REPLACE INTO reply_history (id,source_app,original_message,generated_reply,final_reply,rule_matched_id,model_used_id,style_applied,send_time,modified,tenant_id,sync_version,deleted) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)',
        [h.id,h.sourceApp,h.originalMessage,h.generatedReply,h.finalReply,h.ruleMatchedId||null,h.modelUsedId||null,h.styleApplied?1:0,h.sendTime,h.modified?1:0,t,now,h.deleted?1:0]);
    for (const b of messageBlacklist)
      exec('INSERT OR REPLACE INTO message_blacklist (id,type,value,description,package_name,created_at,is_enabled,tenant_id,sync_version,deleted) VALUES (?,?,?,?,?,?,?,?,?,?)',
        [b.id,b.type,b.value,b.description,b.package_name||null,b.createdAt,b.isEnabled?1:0,t,now,b.deleted?1:0]);

    for (const [et, ids] of Object.entries(deletedIds)) {
      for (const id of ids) {
        if (et==='keyword_rules') exec('UPDATE keyword_rules SET deleted=1,sync_version=? WHERE id=? AND tenant_id=?', [now,id,t]);
        else if (et==='ai_model_configs') exec('UPDATE ai_model_configs SET deleted=1,sync_version=? WHERE id=? AND tenant_id=?', [now,id,t]);
        else if (et==='app_configs') exec('UPDATE app_configs SET deleted=1,sync_version=? WHERE package_name=? AND tenant_id=?', [now,id,t]);
        else if (et==='scenarios') exec('UPDATE scenarios SET deleted=1,sync_version=? WHERE id=? AND tenant_id=?', [now,id,t]);
        else if (et==='reply_history') exec('UPDATE reply_history SET deleted=1,sync_version=? WHERE id=? AND tenant_id=?', [now,id,t]);
        else if (et==='message_blacklist') exec('UPDATE message_blacklist SET deleted=1,sync_version=? WHERE id=? AND tenant_id=?', [now,id,t]);
        else if (et==='user_style_profiles') exec('UPDATE user_style_profiles SET deleted=1,sync_version=? WHERE user_id=? AND tenant_id=?', [now,id,t]);
      }
    }

    exec('INSERT OR REPLACE INTO sync_checkpoints (tenant_id,last_sync_time,is_syncing,last_error) VALUES (?,?,0,NULL)', [t, now]);
    exec('COMMIT');
    res.json({ code:0, message:'成功', data:{ accepted:true, conflicts, newServerVersion:now, serverTime:now } });
  } catch (e) {
    try { exec('ROLLBACK'); } catch (_) {}
    console.error('push error:', e);
    res.status(500).json({ code:500, message:e.message });
  }
});

// 冲突解决
router.post('/resolve', async (req, res) => {
  await getDb();
  res.json({ code:0, message:'成功', data:{ resolved:true, serverTime:Date.now() } });
});

module.exports = router;
