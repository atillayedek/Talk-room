import { compareSync, hashSync } from "npm:bcryptjs@3.0.3";
import * as AgoraAccessToken from "npm:agora-access-token@2.0.4";

const { RtcRole, RtcTokenBuilder } = AgoraAccessToken;

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "GET, POST, DELETE, OPTIONS",
};

type SupabaseUser = {
  id: string;
  email?: string;
};

type RoomRecord = {
  id: string;
  name: string;
  owner_id: string;
  password_hash: string | null;
  is_private: boolean;
  created_at: string | null;
};

type RoomMemberRecord = {
  room_id: string;
  user_id: string;
};

type RoomResponse = {
  id: string;
  name: string;
  owner_id: string;
  is_private: boolean;
  member_count: number;
  created_at: string | null;
};

const supabaseUrl = requiredEnv("SUPABASE_URL").replace(/\/$/, "");
const supabaseAnonKey = requiredEnv("SUPABASE_ANON_KEY");
const supabaseServiceRoleKey = requiredEnv("SUPABASE_SERVICE_ROLE_KEY");
const agoraAppId = requiredEnv("AGORA_APP_ID");
const agoraAppCertificate = requiredEnv("AGORA_APP_CERTIFICATE");
const agoraTokenTtlSeconds = Number(Deno.env.get("AGORA_TOKEN_TTL_SECONDS") ?? "3600");

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const route = routePath(request.url);

    if (request.method === "GET" && route === "/health") {
      return json({ ok: true, service: "talkroom-supabase-edge" });
    }

    const user = await requireUser(request);

    if (request.method === "GET" && route === "/rooms") {
      return json(await listRooms());
    }

    if (request.method === "POST" && route === "/rooms") {
      const body = await request.json().catch(() => ({}));
      const name = String(body.name ?? "").trim();
      const password = typeof body.password === "string" ? body.password.trim() : "";

      if (name.length < 2 || name.length > 80) {
        return error("Oda adı 2-80 karakter olmalı.", 400);
      }
      if (password && password.length < 4) {
        return error("Oda şifresi en az 4 karakter olmalı.", 400);
      }

      const passwordHash = password ? hashSync(password, 12) : null;
      const created = await createRoom(user.id, name, passwordHash);
      await joinRoom(user.id, created.id);
      return json(toPublicRoom(created, 1), 201);
    }

    const joinMatch = route.match(/^\/rooms\/([^/]+)\/join$/);
    if (joinMatch && request.method === "POST") {
      const roomId = joinMatch[1];
      const body = await request.json().catch(() => ({}));
      const room = await roomById(roomId);
      if (!room) return error("Oda bulunamadı.", 404);

      if (room.password_hash) {
        const password = typeof body.password === "string" ? body.password : "";
        if (!compareSync(password, room.password_hash)) {
          return error("Oda şifresi hatalı.", 403);
        }
      }

      await joinRoom(user.id, roomId);
      const counts = await memberCounts();
      return json(toPublicRoom(room, counts.get(room.id) ?? 0));
    }

    if (joinMatch && request.method === "DELETE") {
      await leaveRoom(user.id, joinMatch[1]);
      return new Response(null, { status: 204, headers: corsHeaders });
    }

    if (request.method === "POST" && route === "/agora/token") {
      const body = await request.json().catch(() => ({}));
      const roomId = String(body.room_id ?? "");
      const channelName = String(body.channel_name ?? "");
      const uid = Number(body.uid ?? 0);

      if (!roomId || !channelName || !Number.isInteger(uid) || uid < 1) {
        return error("Agora token isteği geçersiz.", 400);
      }
      if (!(await isMember(user.id, roomId))) {
        return error("Önce odaya katılmalısınız.", 403);
      }

      const expiresAt = Math.floor(Date.now() / 1000) + agoraTokenTtlSeconds;
      const token = RtcTokenBuilder.buildTokenWithUid(
        agoraAppId,
        agoraAppCertificate,
        channelName,
        uid,
        RtcRole.PUBLISHER,
        expiresAt,
      );

      return json({
        token,
        app_id: agoraAppId,
        channel_name: channelName,
        uid,
        expires_at_epoch_seconds: expiresAt,
      });
    }

    return error("Endpoint bulunamadı.", 404);
  } catch (cause) {
    const message = cause instanceof Error ? cause.message : "Beklenmeyen hata.";
    const status = message === "UNAUTHORIZED" ? 401 : 500;
    return error(status === 401 ? "Oturum gerekli." : message, status);
  }
});

async function requireUser(request: Request): Promise<SupabaseUser> {
  const authHeader = request.headers.get("authorization") ?? "";
  const token = authHeader.replace(/^Bearer\s+/i, "").trim();
  if (!token) throw new Error("UNAUTHORIZED");

  const response = await fetch(`${supabaseUrl}/auth/v1/user`, {
    headers: {
      apikey: supabaseAnonKey,
      authorization: `Bearer ${token}`,
    },
  });
  if (!response.ok) throw new Error("UNAUTHORIZED");
  return await response.json();
}

async function listRooms(): Promise<RoomResponse[]> {
  const rooms = await supabaseFetch<RoomRecord[]>("/rest/v1/rooms?select=*&order=created_at.desc");
  const counts = await memberCounts();
  return rooms.map((room) => toPublicRoom(room, counts.get(room.id) ?? 0));
}

async function createRoom(ownerId: string, name: string, passwordHash: string | null): Promise<RoomRecord> {
  const rooms = await supabaseFetch<RoomRecord[]>("/rest/v1/rooms?select=*", {
    method: "POST",
    headers: { Prefer: "return=representation" },
    body: JSON.stringify({
      owner_id: ownerId,
      name,
      password_hash: passwordHash,
      is_private: passwordHash !== null,
    }),
  });
  return rooms[0];
}

async function roomById(roomId: string): Promise<RoomRecord | null> {
  const rooms = await supabaseFetch<RoomRecord[]>(`/rest/v1/rooms?id=eq.${encodeURIComponent(roomId)}&select=*`);
  return rooms[0] ?? null;
}

async function joinRoom(userId: string, roomId: string): Promise<void> {
  await supabaseFetch("/rest/v1/room_members?on_conflict=room_id,user_id", {
    method: "POST",
    headers: { Prefer: "resolution=merge-duplicates" },
    body: JSON.stringify({ room_id: roomId, user_id: userId }),
  });
}

async function leaveRoom(userId: string, roomId: string): Promise<void> {
  await supabaseFetch(`/rest/v1/room_members?room_id=eq.${encodeURIComponent(roomId)}&user_id=eq.${encodeURIComponent(userId)}`, {
    method: "DELETE",
  });
}

async function isMember(userId: string, roomId: string): Promise<boolean> {
  const rows = await supabaseFetch<RoomMemberRecord[]>(
    `/rest/v1/room_members?room_id=eq.${encodeURIComponent(roomId)}&user_id=eq.${encodeURIComponent(userId)}&select=room_id,user_id`,
  );
  return rows.length > 0;
}

async function memberCounts(): Promise<Map<string, number>> {
  const rows = await supabaseFetch<RoomMemberRecord[]>("/rest/v1/room_members?select=room_id,user_id");
  const counts = new Map<string, number>();
  for (const row of rows) {
    counts.set(row.room_id, (counts.get(row.room_id) ?? 0) + 1);
  }
  return counts;
}

async function supabaseFetch<T = unknown>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`${supabaseUrl}${path}`, {
    ...init,
    headers: {
      apikey: supabaseServiceRoleKey,
      authorization: `Bearer ${supabaseServiceRoleKey}`,
      "content-type": "application/json",
      ...(init.headers ?? {}),
    },
  });

  if (!response.ok) {
    const details = await response.text();
    throw new Error(`Supabase isteği başarısız: ${response.status} ${details}`);
  }

  if (response.status === 204) return undefined as T;
  return await response.json();
}

function toPublicRoom(room: RoomRecord, memberCount: number): RoomResponse {
  return {
    id: room.id,
    name: room.name,
    owner_id: room.owner_id,
    is_private: room.is_private,
    member_count: memberCount,
    created_at: room.created_at,
  };
}

function routePath(url: string): string {
  const path = new URL(url).pathname;
  const marker = "/talkroom";
  const index = path.indexOf(marker);
  const route = index >= 0 ? path.slice(index + marker.length) : path;
  return route === "" ? "/" : route;
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      ...corsHeaders,
      "content-type": "application/json",
    },
  });
}

function error(message: string, status: number): Response {
  return json({ error: message }, status);
}

function requiredEnv(name: string): string {
  const value = Deno.env.get(name)?.trim();
  if (!value) throw new Error(`${name} environment variable is required`);
  return value;
}
