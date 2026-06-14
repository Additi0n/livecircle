import http from "node:http";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import crypto from "node:crypto";

const __dirname = dirname(fileURLToPath(import.meta.url));
const DATA_DIR = join(__dirname, "data");
const DATA_FILE = join(DATA_DIR, "groups.json");
const PORT = Number(process.env.PORT || 8787);
const TTL_MS = Number(process.env.LIVECIRCLE_TTL_MS || 15 * 60 * 1000);

let groups = Object.create(null);
const clients = new Map();

function json(res, code, body) {
  const text = JSON.stringify(body);
  res.writeHead(code, {
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store",
    "access-control-allow-origin": "*",
    "access-control-allow-methods": "GET,POST,OPTIONS",
    "access-control-allow-headers": "content-type"
  });
  res.end(text);
}

function sse(res) {
  res.writeHead(200, {
    "content-type": "text/event-stream; charset=utf-8",
    "cache-control": "no-store, no-transform",
    "connection": "keep-alive",
    "x-accel-buffering": "no",
    "access-control-allow-origin": "*"
  });
  res.write(": connected\n\n");
}

function sendEvent(res, event, data) {
  res.write(`event: ${event}\n`);
  res.write(`data: ${JSON.stringify(data)}\n\n`);
}

async function readBody(req) {
  let body = "";
  for await (const chunk of req) {
    body += chunk;
    if (body.length > 1024 * 64) throw new Error("request too large");
  }
  return body ? JSON.parse(body) : {};
}

function cleanGroup(group) {
  const now = Date.now();
  for (const [id, member] of Object.entries(group.members)) {
    if (now - member.updatedAt > TTL_MS) delete group.members[id];
  }
}

function validGroupCode(code) {
  return typeof code === "string" && /^[A-Za-z0-9_-]{4,64}$/.test(code);
}

function publicMember(member) {
  return {
    memberId: member.memberId,
    name: member.name,
    lat: member.lat,
    lon: member.lon,
    accuracy: member.accuracy,
    updatedAt: member.updatedAt
  };
}

function snapshot(groupCode) {
  const group = groups[groupCode] || { members: Object.create(null) };
  cleanGroup(group);
  return {
    ok: true,
    groupCode,
    members: Object.values(group.members).map(publicMember)
  };
}

function broadcastGroup(groupCode) {
  const body = snapshot(groupCode);
  const groupClients = clients.get(groupCode);
  if (!groupClients) return;
  for (const res of groupClients) {
    sendEvent(res, "members", body);
  }
}

function addClient(groupCode, res) {
  if (!clients.has(groupCode)) clients.set(groupCode, new Set());
  clients.get(groupCode).add(res);
  sendEvent(res, "members", snapshot(groupCode));

  const heartbeat = setInterval(() => {
    try {
      res.write(": heartbeat\n\n");
    } catch {
      clearInterval(heartbeat);
    }
  }, 25000);

  res.on("close", () => {
    clearInterval(heartbeat);
    const groupClients = clients.get(groupCode);
    if (!groupClients) return;
    groupClients.delete(res);
    if (groupClients.size === 0) clients.delete(groupCode);
  });
}

async function load() {
  try {
    const text = await readFile(DATA_FILE, "utf8");
    groups = JSON.parse(text);
  } catch {
    groups = Object.create(null);
  }
}

async function persist() {
  await mkdir(DATA_DIR, { recursive: true });
  await writeFile(DATA_FILE, JSON.stringify(groups, null, 2));
}

await load();

const server = http.createServer(async (req, res) => {
  try {
    if (req.method === "OPTIONS") return json(res, 204, {});

    const url = new URL(req.url, `http://${req.headers.host || "localhost"}`);

    if (req.method === "GET" && url.pathname === "/api/health") {
      return json(res, 200, { ok: true, now: Date.now(), mode: "relay" });
    }

    if (req.method === "GET" && url.pathname === "/api/events") {
      const groupCode = String(url.searchParams.get("groupCode") || "").trim();
      if (!validGroupCode(groupCode)) return json(res, 400, { error: "invalid groupCode" });
      sse(res);
      addClient(groupCode, res);
      return;
    }

    if (req.method === "POST" && url.pathname === "/api/location") {
      const body = await readBody(req);
      const groupCode = String(body.groupCode || "").trim();
      if (!validGroupCode(groupCode)) return json(res, 400, { error: "invalid groupCode" });

      const memberId = String(body.memberId || crypto.randomUUID()).slice(0, 80);
      const name = String(body.name || "Member").slice(0, 40);
      const lat = Number(body.lat);
      const lon = Number(body.lon);
      const accuracy = Number(body.accuracy || 0);
      if (!Number.isFinite(lat) || !Number.isFinite(lon)) {
        return json(res, 400, { error: "invalid location" });
      }

      const group = groups[groupCode] || (groups[groupCode] = { members: Object.create(null) });
      group.members[memberId] = {
        memberId,
        name,
        lat,
        lon,
        accuracy,
        updatedAt: Date.now()
      };
      cleanGroup(group);
      await persist();
      broadcastGroup(groupCode);
      return json(res, 200, snapshot(groupCode));
    }

    if (req.method === "GET" && url.pathname === "/api/group") {
      const groupCode = String(url.searchParams.get("groupCode") || "").trim();
      if (!validGroupCode(groupCode)) return json(res, 400, { error: "invalid groupCode" });
      return json(res, 200, snapshot(groupCode));
    }

    return json(res, 404, { error: "not found" });
  } catch (error) {
    return json(res, 500, { error: error.message || "server error" });
  }
});

server.listen(PORT, "0.0.0.0", () => {
  console.log(`LiveCircle relay listening on http://0.0.0.0:${PORT}`);
});
