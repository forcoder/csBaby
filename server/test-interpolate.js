function interpolate(sql, params) {
  return sql.replace(/\$(\d+)/g, (_, n) => {
    const idx = parseInt(n, 10) - 1;
    return idx < params.length ? "'" + String(params[idx]).replace(/'/g, "''") + "'" : _;
  });
}

const sql = 'INSERT INTO keyword_rules (keyword,match_type,reply_template,category,target_type,target_names_json,priority,enabled,created_at,updated_at,tenant_id,sync_version,deleted) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13)';
const params = ['test-keyword', 'CONTAINS', 'test reply', '', 'ALL', '[]', 0, 1, 1779425000000, 1779425000000, 'fc0807c8-38ff-4c34-8fc2-b61dd1ce582d', 1779425000000, 0];
console.log('SQL:', interpolate(sql, params));

const sql2 = 'INSERT INTO keyword_rules (id,keyword,match_type,reply_template,category,target_type,target_names_json,priority,enabled,created_at,updated_at,tenant_id,sync_version,deleted) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14) ON CONFLICT (id) DO UPDATE SET keyword=EXCLUDED.keyword, sync_version=EXCLUDED.sync_version';
const params2 = [99999, 'conflict-test', 'CONTAINS', 'test', '', 'ALL', '[]', 0, 1, 1779425000000, 1779425000000, 'fc0807c8-38ff-4c34-8fc2-b61dd1ce582d', 1779425000000, 0];
console.log('\nSQL2:', interpolate(sql2, params2));