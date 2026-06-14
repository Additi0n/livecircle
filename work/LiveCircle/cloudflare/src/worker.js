const TTL_MS = 15 * 60 * 1000;

function json(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
      "access-control-allow-origin": "*",
      "access-control-allow-methods": "GET,POST,OPTIONS",
      "access-control-allow-headers": "content-type"
    }
  });
}

function validGroupCode(code) {
  return typeof code === "string" && /^[A-Za-z0-9_-]{4,64}$/.test(code);
}

function sseHeaders() {
  return {
    "content-type": "text/event-stream; charset=utf-8",
    "cache-control": "no-store, no-transform",
    "connection": "keep-alive",
    "x-accel-buffering": "no",
    "access-control-allow-origin": "*"
  };
}

function encodeEvent(event, data) {
  return `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`;
}

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") return json({}, 204);

    const url = new URL(request.url);
    if (url.pathname === "/api/health") {
      return json({ ok: true, now: Date.now(), mode: "cloudflare-worker" });
    }

    const groupCode = request.method === "GET"
      ? String(url.searchParams.get("groupCode") || "").trim()
      : String((await request.clone().json().catch(() => ({}))).groupCode || "").trim();

    if (!validGroupCode(groupCode)) return json({ error: "invalid groupCode" }, 400);

    const id = env.GROUP_ROOM.idFromName(groupCode);
    const room = env.GROUP_ROOM.get(id);
    const roomUrl = new URL(request.url);
    roomUrl.searchParams.set("groupCode", groupCode);
    return room.fetch(new Request(roomUrl, request));
  }
};

export class GroupRoom {
  constructor(state) {
    this.state = state;
    this.clients = new Set();
  }

  async fetch(request) {
    const url = new URL(request.url);

    if (request.method === "GET" && url.pathname === "/api/events") {
      return this.events(url.searchParams.get("groupCode"));
    }

    if (request.method === "GET" && url.pathname === "/api/group") {
      return json(await this.snapshot(url.searchParams.get("groupCode")));
    }

    if (request.method === "POST" && url.pathname === "/api/location") {
      const body = await request.json();
      const member = this.memberFromBody(body);
      if (!member) return json({ error: "invalid location" }, 400);

      const members = await this.loadMembers();
      members[member.memberId] = member;
      const cleaned = this.cleanMembers(members);
      await this.state.storage.put("members", cleaned);
      const snap = this.makeSnapshot(body.groupCode, cleaned);
      this.broadcast(snap);
      return json(snap);
    }

    return json({ error: "not found" }, 404);
  }

  memberFromBody(body) {
    const lat = Number(body.lat);
    const lon = Number(body.lon);
    if (!Number.isFinite(lat) || !Number.isFinite(lon)) return null;
    return {
      memberId: String(body.memberId || crypto.randomUUID()).slice(0, 80),
      name: String(body.name || "Member").slice(0, 40),
      lat,
      lon,
      accuracy: Number(body.accuracy || 0),
      updatedAt: Date.now()
    };
  }

  async loadMembers() {
    return (await this.state.storage.get("members")) || {};
  }

  cleanMembers(members) {
    const now = Date.now();
    const cleaned = {};
    for (const [memberId, member] of Object.entries(members)) {
      if (now - member.updatedAt <= TTL_MS) cleaned[memberId] = member;
    }
    return cleaned;
  }

  makeSnapshot(groupCode, members) {
    return {
      ok: true,
      groupCode,
      members: Object.values(members)
    };
  }

  async snapshot(groupCode) {
    const members = this.cleanMembers(await this.loadMembers());
    await this.state.storage.put("members", members);
    return this.makeSnapshot(groupCode, members);
  }

  async events(groupCode) {
    const encoder = new TextEncoder();
    const self = this;
    let heartbeat = null;

    const stream = new ReadableStream({
      async start(controller) {
        const client = {
          send(text) {
            controller.enqueue(encoder.encode(text));
          },
          close() {
            try {
              controller.close();
            } catch {
            }
          }
        };
        self.clients.add(client);
        client.send(": connected\n\n");
        client.send(encodeEvent("members", await self.snapshot(groupCode)));
        heartbeat = setInterval(() => {
          try {
            client.send(": heartbeat\n\n");
          } catch {
            clearInterval(heartbeat);
            self.clients.delete(client);
          }
        }, 25000);
      },
      cancel() {
        if (heartbeat) clearInterval(heartbeat);
      }
    });

    return new Response(stream, { headers: sseHeaders() });
  }

  broadcast(snapshot) {
    const text = encodeEvent("members", snapshot);
    for (const client of this.clients) {
      try {
        client.send(text);
      } catch {
        this.clients.delete(client);
      }
    }
  }
}
