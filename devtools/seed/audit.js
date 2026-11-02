// Explicit dev database guard and stable upserts: never delete existing audit data.
(() => {
  if (db.getName() !== "xcore_dev") throw new Error("Audit seed requires the dev database");
  for (let index = 0; index < 12; index++) {
    const id = `dev-audit-${index}`;
    db.moderation_audit.updateOne({audit_id: id}, {$setOnInsert: {
      audit_id: id, schema_version: 1, action: "WARN", category: "PUNISHMENT",
      target: {uuid: `d3100000-0000-4000-8000-${String(index + 1).padStart(12, "0")}`, pid: 900001 + index, name_snapshot: `Demo ${index + 1}`},
      actor: {type: "SERVER_CONSOLE", id: "dev-console", name_snapshot: "Dev console", pid: null},
      origin: {server_id: "dev", request_id: id}, reason: "Demonstration audit record",
      occurred_at: new Date(), created_at_epoch_ms: NumberLong(String(Date.now())),
    }}, {upsert: true});
  }
})();
