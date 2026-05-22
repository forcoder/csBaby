const { Router } = require('express');
const { getDb, queryAll, queryOne, exec, pool } = require('./db');
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

// 调试：测试简单 SQL 操作
router.get('/debug-sql', async (req, res) => {
  await getDb();
  const results = {};
  try {
    results.step1 = (await queryOne('SELECT 1 as val')).val;
  } catch (e) { results.err1 = e.message; }
  try {
    const r = await exec('UPDATE sync_checkpoints SET last_sync_time=$1 WHERE tenant_id=$2', [Date.now(), req.tenantId]);
    results.step2 = r;
  } catch (e) { results.err2 = e.message; }
  try {
    await exec('INSERT INTO keyword_rules (keyword,match_type,reply_template,category,target_type,target_names_json,priority,enabled,created_at,updated_at,tenant_id,sync_version,deleted) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13)',
      ['debug','CONTAINS','debug','debug','ALL','[]',0,1,Date.now(),Date.now(),req.tenantId,Date.now(),0]);
    results.step3 = 'ok';
  } catch (e) { results.err3 = e.message; }
  try {
    const r = await exec('INSERT INTO sync_checkpoints (tenant_id,last_sync_time,is_syncing,last_error) VALUES ($1,$2,0,NULL) ON CONFLICT (tenant_id) DO UPDATE SET last_sync_time=$2',
      [req.tenantId, Date.now()]);
    results.step4 = r;
  } catch (e) { results.err4 = e.message; }
  res.json({ code:0, data:results });
});

// 全量同步
router.get('/all', async (req, res) => {
  await getDb();
  const t = req.tenantId, now = Date.now();
  res.json({ code:0, message:'成功', data:{
    keywordRules: (await queryAll('SELECT * FROM keyword_rules WHERE tenant_id=$1 AND deleted=0 ORDER BY priority DESC, created_at DESC', [t])).map(toRule),
    aiModelConfigs: (await queryAll('SELECT * FROM ai_model_configs WHERE tenant_id=$1 AND deleted=0 ORDER BY is_default DESC, last_used DESC', [t])).map(toModel),
    userStyleProfile: toProfile(await queryOne('SELECT * FROM user_style_profiles WHERE tenant_id=$1 AND deleted=0', [t])),
    appConfigs: (await queryAll('SELECT * FROM app_configs WHERE tenant_id=$1 AND deleted=0 ORDER BY last_used DESC', [t])).map(toApp),
    scenarios: (await queryAll('SELECT * FROM scenarios WHERE tenant_id=$1 AND deleted=0 ORDER BY created_at DESC', [t])).map(toScenario),
    replyHistory: (await queryAll('SELECT * FROM reply_history WHERE tenant_id=$1 AND deleted=0 ORDER BY send_time DESC LIMIT 500', [t])).map(toReply),
    messageBlacklist: (await queryAll('SELECT * FROM message_blacklist WHERE tenant_id=$1 AND deleted=0 ORDER BY created_at DESC', [t])).map(toBlacklist),
    serverTime: now
  }});
});

// 增量：获取变更
router.get('/changes', async (req, res) => {
  await getDb();
  const t = req.tenantId, since = parseInt(req.query.since) || 0, now = Date.now();
  const del = {};
  const dr = await queryAll('SELECT id FROM keyword_rules WHERE tenant_id=$1 AND sync_version>$2 AND deleted=1', [t, since]);
  if (dr.length) del['keyword_rules'] = dr.map(r=>String(r.id));
  const dm = await queryAll('SELECT id FROM ai_model_configs WHERE tenant_id=$1 AND sync_version>$2 AND deleted=1', [t, since]);
  if (dm.length) del['ai_model_configs'] = dm.map(r=>String(r.id));
  const da = await queryAll('SELECT package_name FROM app_configs WHERE tenant_id=$1 AND sync_version>$2 AND deleted=1', [t, since]);
  if (da.length) del['app_configs'] = da.map(r=>String(r.package_name));
  const ds = await queryAll('SELECT id FROM scenarios WHERE tenant_id=$1 AND sync_version>$2 AND deleted=1', [t, since]);
  if (ds.length) del['scenarios'] = ds.map(r=>String(r.id));
  const dh = await queryAll('SELECT id FROM reply_history WHERE tenant_id=$1 AND sync_version>$2 AND deleted=1', [t, since]);
  if (dh.length) del['reply_history'] = dh.map(r=>String(r.id));
  const db = await queryAll('SELECT id FROM message_blacklist WHERE tenant_id=$1 AND sync_version>$2 AND deleted=1', [t, since]);
  if (db.length) del['message_blacklist'] = db.map(r=>String(r.id));

  res.json({ code:0, message:'成功', data:{
    keywordRules: (await queryAll('SELECT * FROM keyword_rules WHERE tenant_id=$1 AND sync_version>$2', [t, since])).map(toRule),
    aiModelConfigs: (await queryAll('SELECT * FROM ai_model_configs WHERE tenant_id=$1 AND sync_version>$2', [t, since])).map(toModel),
    userStyleProfile: toProfile(await queryOne('SELECT * FROM user_style_profiles WHERE tenant_id=$1 AND sync_version>$2', [t, since])),
    appConfigs: (await queryAll('SELECT * FROM app_configs WHERE tenant_id=$1 AND sync_version>$2', [t, since])).map(toApp),
    scenarios: (await queryAll('SELECT * FROM scenarios WHERE tenant_id=$1 AND sync_version>$2', [t, since])).map(toScenario),
    replyHistory: (await queryAll('SELECT * FROM reply_history WHERE tenant_id=$1 AND sync_version>$2 ORDER BY send_time DESC LIMIT 500', [t, since])).map(toReply),
    messageBlacklist: (await queryAll('SELECT * FROM message_blacklist WHERE tenant_id=$1 AND sync_version>$2', [t, since])).map(toBlacklist),
    deletedIds: del, serverTime: now, hasMore: false, nextCursor: null
  }});
});

// 增量：推送变更
router.post('/push', async (req, res) => {
  console.log('[sync/push v3] Starting - using await getDb() and req.tenantId');
  await getDb();
  const t = req.tenantId;
  const { keywordRules=[], aiModelConfigs=[], userStyleProfile, appConfigs=[], scenarios=[], replyHistory=[], messageBlacklist=[], deletedIds={}, baseVersion=0 } = req.body;
  const now = Date.now();
  const conflicts = [];

  console.log('[sync/push] Starting push for tenant:', t, 'rules:', keywordRules.length);

  // 冲突检测
  for (const r of keywordRules) {
    const e = await queryOne('SELECT sync_version,updated_at FROM keyword_rules WHERE id=$1 AND tenant_id=$2', [r.id, t]);
    if (e && e.sync_version > baseVersion) conflicts.push({ entityType:'keyword_rule', entityId:String(r.id), serverVersion:e.sync_version, serverUpdatedAt:e.updated_at });
  }
  for (const m of aiModelConfigs) {
    const e = await queryOne('SELECT sync_version,last_used FROM ai_model_configs WHERE id=$1 AND tenant_id=$2', [m.id, t]);
    if (e && e.sync_version > baseVersion) conflicts.push({ entityType:'ai_model_config', entityId:String(m.id), serverVersion:e.sync_version, serverUpdatedAt:e.last_used });
  }
  if (userStyleProfile) {
    const e = await queryOne('SELECT sync_version,last_trained FROM user_style_profiles WHERE user_id=$1 AND tenant_id=$2', [userStyleProfile.userId, t]);
    if (e && e.sync_version > baseVersion) conflicts.push({ entityType:'style_profile', entityId:userStyleProfile.userId, serverVersion:e.sync_version, serverUpdatedAt:e.last_trained });
  }
  for (const a of appConfigs) {
    const e = await queryOne('SELECT sync_version,last_used FROM app_configs WHERE package_name=$1 AND tenant_id=$2', [a.packageName, t]);
    if (e && e.sync_version > baseVersion) conflicts.push({ entityType:'app_config', entityId:a.packageName, serverVersion:e.sync_version, serverUpdatedAt:e.last_used });
  }
  for (const s of scenarios) {
    const e = await queryOne('SELECT sync_version,created_at FROM scenarios WHERE id=$1 AND tenant_id=$2', [s.id, t]);
    if (e && e.sync_version > baseVersion) conflicts.push({ entityType:'scenario', entityId:String(s.id), serverVersion:e.sync_version, serverUpdatedAt:e.created_at });
  }
  for (const h of replyHistory) {
    const e = await queryOne('SELECT sync_version,send_time FROM reply_history WHERE id=$1 AND tenant_id=$2', [h.id, t]);
    if (e && e.sync_version > baseVersion) conflicts.push({ entityType:'reply_history', entityId:String(h.id), serverVersion:e.sync_version, serverUpdatedAt:e.send_time });
  }
  for (const b of messageBlacklist) {
    const e = await queryOne('SELECT sync_version,created_at FROM message_blacklist WHERE id=$1 AND tenant_id=$2', [b.id, t]);
    if (e && e.sync_version > baseVersion) conflicts.push({ entityType:'message_blacklist', entityId:String(b.id), serverVersion:e.sync_version, serverUpdatedAt:e.created_at });
  }

  try {
    console.log('[sync/push] Before exec calls, checking pool state...');

    // 测试：直接用 pool.query 而不用 exec
    const testResult = await pool.query('SELECT 1 as val');
    console.log('[sync/push] Pool query test OK:', testResult.rows[0]);

    // 逐条写入（不使用事务，避免连接池问题）
    for (const r of keywordRules) {
      await exec('INSERT INTO keyword_rules (id,keyword,match_type,reply_template,category,target_type,target_names_json,priority,enabled,created_at,updated_at,tenant_id,sync_version,deleted) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14) ON CONFLICT (id) DO UPDATE SET keyword=EXCLUDED.keyword,match_type=EXCLUDED.match_type,reply_template=EXCLUDED.reply_template,category=EXCLUDED.category,target_type=EXCLUDED.target_type,target_names_json=EXCLUDED.target_names_json,priority=EXCLUDED.priority,enabled=EXCLUDED.enabled,created_at=EXCLUDED.created_at,updated_at=EXCLUDED.updated_at,tenant_id=EXCLUDED.tenant_id,sync_version=EXCLUDED.sync_version,deleted=EXCLUDED.deleted',
        [r.id,r.keyword,r.matchType,r.replyTemplate,r.category,r.targetType,r.targetNamesJson,r.priority,r.enabled?1:0,r.createdAt,r.updatedAt,t,now,r.deleted?1:0]);
    }
    for (const m of aiModelConfigs) {
      await exec('INSERT INTO ai_model_configs (id,model_type,model_name,api_key,api_endpoint,temperature,max_tokens,is_default,is_enabled,monthly_cost,last_used,created_at,tenant_id,sync_version,deleted) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15) ON CONFLICT (id) DO UPDATE SET model_type=EXCLUDED.model_type,model_name=EXCLUDED.model_name,api_key=EXCLUDED.api_key,api_endpoint=EXCLUDED.api_endpoint,temperature=EXCLUDED.temperature,max_tokens=EXCLUDED.max_tokens,is_default=EXCLUDED.is_default,is_enabled=EXCLUDED.is_enabled,monthly_cost=EXCLUDED.monthly_cost,last_used=EXCLUDED.last_used,created_at=EXCLUDED.created_at,tenant_id=EXCLUDED.tenant_id,sync_version=EXCLUDED.sync_version,deleted=EXCLUDED.deleted',
        [m.id,m.modelType,m.modelName,m.apiKey,m.apiEndpoint,m.temperature,m.maxTokens,m.isDefault?1:0,m.isEnabled?1:0,m.monthlyCost,m.lastUsed,m.createdAt,t,now,m.deleted?1:0]);
    }
    if (userStyleProfile)
      await exec('INSERT INTO user_style_profiles (user_id,formality_level,enthusiasm_level,professionalism_level,word_count_preference,common_phrases,avoid_phrases,learning_samples,accuracy_score,last_trained,created_at,tenant_id,sync_version,deleted) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14) ON CONFLICT (user_id) DO UPDATE SET formality_level=EXCLUDED.formality_level,enthusiasm_level=EXCLUDED.enthusiasm_level,professionalism_level=EXCLUDED.professionalism_level,word_count_preference=EXCLUDED.word_count_preference,common_phrases=EXCLUDED.common_phrases,avoid_phrases=EXCLUDED.avoid_phrases,learning_samples=EXCLUDED.learning_samples,accuracy_score=EXCLUDED.accuracy_score,last_trained=EXCLUDED.last_trained,created_at=EXCLUDED.created_at,tenant_id=EXCLUDED.tenant_id,sync_version=EXCLUDED.sync_version,deleted=EXCLUDED.deleted',
        [userStyleProfile.userId,userStyleProfile.formalityLevel,userStyleProfile.enthusiasmLevel,userStyleProfile.professionalismLevel,userStyleProfile.wordCountPreference,userStyleProfile.commonPhrases,userStyleProfile.avoidPhrases,userStyleProfile.learningSamples,userStyleProfile.accuracyScore,userStyleProfile.lastTrained,userStyleProfile.createdAt,t,now,userStyleProfile.deleted?1:0]);
    for (const a of appConfigs) {
      await exec('INSERT INTO app_configs (package_name,app_name,icon_uri,is_monitored,created_at,last_used,tenant_id,sync_version,deleted) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9) ON CONFLICT (package_name) DO UPDATE SET app_name=EXCLUDED.app_name,icon_uri=EXCLUDED.icon_uri,is_monitored=EXCLUDED.is_monitored,created_at=EXCLUDED.created_at,last_used=EXCLUDED.last_used,tenant_id=EXCLUDED.tenant_id,sync_version=EXCLUDED.sync_version,deleted=EXCLUDED.deleted',
        [a.packageName,a.appName,a.iconUri||null,a.isMonitored?1:0,a.createdAt,a.lastUsed,t,now,a.deleted?1:0]);
    }
    for (const s of scenarios) {
      await exec('INSERT INTO scenarios (id,name,type,target_id,description,created_at,tenant_id,sync_version,deleted) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9) ON CONFLICT (id) DO UPDATE SET name=EXCLUDED.name,type=EXCLUDED.type,target_id=EXCLUDED.target_id,description=EXCLUDED.description,created_at=EXCLUDED.created_at,tenant_id=EXCLUDED.tenant_id,sync_version=EXCLUDED.sync_version,deleted=EXCLUDED.deleted',
        [s.id,s.name,s.type,s.targetId||null,s.description||null,s.createdAt,t,now,s.deleted?1:0]);
    }
    for (const h of replyHistory) {
      await exec('INSERT INTO reply_history (id,source_app,original_message,generated_reply,final_reply,rule_matched_id,model_used_id,style_applied,send_time,modified,tenant_id,sync_version,deleted) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13) ON CONFLICT (id) DO UPDATE SET source_app=EXCLUDED.source_app,original_message=EXCLUDED.original_message,generated_reply=EXCLUDED.generated_reply,final_reply=EXCLUDED.final_reply,rule_matched_id=EXCLUDED.rule_matched_id,model_used_id=EXCLUDED.model_used_id,style_applied=EXCLUDED.style_applied,send_time=EXCLUDED.send_time,modified=EXCLUDED.modified,tenant_id=EXCLUDED.tenant_id,sync_version=EXCLUDED.sync_version,deleted=EXCLUDED.deleted',
        [h.id,h.sourceApp,h.originalMessage,h.generatedReply,h.finalReply,h.ruleMatchedId||null,h.modelUsedId||null,h.styleApplied?1:0,h.sendTime,h.modified?1:0,t,now,h.deleted?1:0]);
    }
    for (const b of messageBlacklist) {
      await exec('INSERT INTO message_blacklist (id,type,value,description,package_name,created_at,is_enabled,tenant_id,sync_version,deleted) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10) ON CONFLICT (id) DO UPDATE SET type=EXCLUDED.type,value=EXCLUDED.value,description=EXCLUDED.description,package_name=EXCLUDED.package_name,created_at=EXCLUDED.created_at,is_enabled=EXCLUDED.is_enabled,tenant_id=EXCLUDED.tenant_id,sync_version=EXCLUDED.sync_version,deleted=EXCLUDED.deleted',
        [b.id,b.type,b.value,b.description,b.packageName||null,b.createdAt,b.isEnabled?1:0,t,now,b.deleted?1:0]);
    }

    // 处理删除
    for (const [et, ids] of Object.entries(deletedIds)) {
      for (const id of ids) {
        if (et==='keyword_rules') await exec('UPDATE keyword_rules SET deleted=1,sync_version=$1 WHERE id=$2 AND tenant_id=$3', [now,id,t]);
        else if (et==='ai_model_configs') await exec('UPDATE ai_model_configs SET deleted=1,sync_version=$1 WHERE id=$2 AND tenant_id=$3', [now,id,t]);
        else if (et==='app_configs') await exec('UPDATE app_configs SET deleted=1,sync_version=$1 WHERE package_name=$2 AND tenant_id=$3', [now,id,t]);
        else if (et==='scenarios') await exec('UPDATE scenarios SET deleted=1,sync_version=$1 WHERE id=$2 AND tenant_id=$3', [now,id,t]);
        else if (et==='reply_history') await exec('UPDATE reply_history SET deleted=1,sync_version=$1 WHERE id=$2 AND tenant_id=$3', [now,id,t]);
        else if (et==='message_blacklist') await exec('UPDATE message_blacklist SET deleted=1,sync_version=$1 WHERE id=$2 AND tenant_id=$3', [now,id,t]);
        else if (et==='user_style_profiles') await exec('UPDATE user_style_profiles SET deleted=1,sync_version=$1 WHERE user_id=$2 AND tenant_id=$3', [now,id,t]);
      }
    }

    // checkpoint
    await exec('INSERT INTO sync_checkpoints (tenant_id,last_sync_time,is_syncing,last_error) VALUES ($1,$2,0,NULL) ON CONFLICT (tenant_id) DO UPDATE SET last_sync_time=$2,is_syncing=0,last_error=NULL', [t, now]);

    res.json({ code:0, message:'成功', data:{ accepted:true, conflicts, newServerVersion:now, serverTime:now } });
  } catch (e) {
    console.error('push error:', e.message, 'STACK:', e.stack);
    res.status(500).json({ code:500, message: e.message });
  }
});

// 冲突解决
router.post('/resolve', async (req, res) => {
  await getDb();
  const t = req.tenantId;
  const { resolutions } = req.body;
  if (!resolutions || !Array.isArray(resolutions)) {
    return res.status(400).json({ code: 400, message: '缺少 resolutions 参数' });
  }
  try {
    for (const r of resolutions) {
      const { entityType, entityId, strategy } = r;
      if (strategy === 'SERVER_WINS') {
        // 服务端已是最新的，客户端拉取时会覆盖，无需操作
      } else if (strategy === 'CLIENT_WINS') {
        // 客户端优先：将 sync_version 设为当前时间戳
        const now = Date.now();
        if (entityType === 'keyword_rule') await exec('UPDATE keyword_rules SET sync_version=$1 WHERE id=$2 AND tenant_id=$3', [now, entityId, t]);
        else if (entityType === 'ai_model_config') await exec('UPDATE ai_model_configs SET sync_version=$1 WHERE id=$2 AND tenant_id=$3', [now, entityId, t]);
        else if (entityType === 'style_profile') await exec('UPDATE user_style_profiles SET sync_version=$1 WHERE user_id=$2 AND tenant_id=$3', [now, entityId, t]);
        else if (entityType === 'app_config') await exec('UPDATE app_configs SET sync_version=$1 WHERE package_name=$2 AND tenant_id=$3', [now, entityId, t]);
        else if (entityType === 'scenario') await exec('UPDATE scenarios SET sync_version=$1 WHERE id=$2 AND tenant_id=$3', [now, entityId, t]);
        else if (entityType === 'reply_history') await exec('UPDATE reply_history SET sync_version=$1 WHERE id=$2 AND tenant_id=$3', [now, entityId, t]);
        else if (entityType === 'message_blacklist') await exec('UPDATE message_blacklist SET sync_version=$1 WHERE id=$2 AND tenant_id=$3', [now, entityId, t]);
      }
      // MERGE 策略：客户端应在 push 时重新提交合并后的数据，此处无需操作
    }
    res.json({ code: 0, message: '成功', data: { resolved: true, serverTime: Date.now() } });
  } catch (e) {
    console.error('resolve error:', e);
    res.status(500).json({ code: 500, message: e.message });
  }
});

module.exports = router;