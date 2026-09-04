interface Env {
  BONSAI_URL: string;
}

const CORS_HEADERS: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type",
};

function jsonResponse(body: object, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...CORS_HEADERS },
  });
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (request.method === "OPTIONS") {
      return new Response(null, { headers: CORS_HEADERS });
    }

    const url = new URL(request.url);

    if (request.method !== "POST" || url.pathname !== "/") {
      return jsonResponse(
        { error: "Access denied. Only POST / is allowed." },
        403
      );
    }

    if (!env.BONSAI_URL) {
      return jsonResponse(
        { error: "Missing BONSAI_URL environment secret." },
        500
      );
    }

    try {
      const bonsaiParsed = new URL(env.BONSAI_URL.trim());
      const hostUrl = `${bonsaiParsed.protocol}//${bonsaiParsed.host}`;
      const targetUrl = `${hostUrl}/magic-singles/_search`;

      const username = decodeURIComponent(bonsaiParsed.username);
      const password = decodeURIComponent(bonsaiParsed.password);
      const authHeader = `Basic ${btoa(`${username}:${password}`)}`;

      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 10000);

      const requestBody = await request.text();

      const bonsaiResponse = await fetch(targetUrl, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: authHeader,
          "User-Agent": "Cloudflare-Worker-Proxy",
        },
        body: requestBody,
        signal: controller.signal,
      });

      clearTimeout(timeoutId);

      const responseText = await bonsaiResponse.text();

      return new Response(responseText, {
        status: bonsaiResponse.status,
        headers: { "Content-Type": "application/json", ...CORS_HEADERS },
      });
    } catch (err) {
      const isTimeout = err instanceof Error && err.name === "AbortError";
      return jsonResponse(
        { error: isTimeout ? "Bonsai gateway timeout." : "Proxy error." },
        isTimeout ? 504 : 502
      );
    }
  },
};
