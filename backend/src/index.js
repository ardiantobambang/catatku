// Catatku sync API — Cloudflare Worker + D1
// Binding: DB (D1 "catatku"), Secret: API_TOKEN

const UPSERT = `
INSERT INTO notes (id, text, tags, pinned, created_at, updated_at, deleted, srv_ts)
VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)
ON CONFLICT(id) DO UPDATE SET
  text = excluded.text,
  tags = excluded.tags,
  pinned = excluded.pinned,
  updated_at = excluded.updated_at,
  deleted = excluded.deleted,
  srv_ts = excluded.srv_ts
WHERE excluded.updated_at > notes.updated_at`;

const json = (data, status = 200) =>
  new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json; charset=utf-8" },
  });

export default {
  async fetch(req, env) {
    const url = new URL(req.url);

    if (url.pathname === "/" && req.method === "GET") return json({ ok: true, app: "catatku" });

    const auth = req.headers.get("authorization") || "";
    if (!env.API_TOKEN || auth !== `Bearer ${env.API_TOKEN}`) return json({ error: "unauthorized" }, 401);

    if (url.pathname === "/sync" && req.method === "POST") {
      let body;
      try {
        body = await req.json();
      } catch {
        return json({ error: "bad json" }, 400);
      }
      const since = Number(body.since) || 0;
      const changes = Array.isArray(body.changes) ? body.changes : [];
      const now = Date.now();

      const stmts = changes
        .filter((n) => n && typeof n.id === "string" && typeof n.text === "string")
        .map((n) =>
          env.DB.prepare(UPSERT).bind(
            n.id,
            n.text,
            String(n.tags || ""),
            n.pinned ? 1 : 0,
            Number(n.created_at) || now,
            Number(n.updated_at) || now,
            n.deleted ? 1 : 0,
            now
          )
        );
      for (let i = 0; i < stmts.length; i += 50) await env.DB.batch(stmts.slice(i, i + 50));

      // 5s overlap guards against same-millisecond writes from other devices
      const { results } = await env.DB
        .prepare("SELECT id, text, tags, pinned, created_at, updated_at, deleted FROM notes WHERE srv_ts > ?1")
        .bind(Math.max(0, since - 5000))
        .all();

      return json({ now, notes: results });
    }

    if (url.pathname === "/export" && req.method === "GET") {
      const { results } = await env.DB
        .prepare("SELECT id, text, tags, pinned, created_at, updated_at FROM notes WHERE deleted = 0 ORDER BY created_at")
        .all();
      return json({ notes: results });
    }

    return json({ error: "not found" }, 404);
  },
};
