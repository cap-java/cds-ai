/**
 * Cleans up AI Core test resource groups.
 *
 * Required environment variables:
 *   RESOURCE_GROUP_PREFIX - The prefix identifying resource groups owned by this run
 *                           (e.g. "itest-12345-1-j17" or "sonar-12345-1")
 *
 * Optional environment variables:
 *   STALE_PREFIXES - Comma-separated list of additional prefixes to clean up
 *                    (defaults to "itest-rg-,cds-itest-")
 *
 * Credentials are resolved from VCAP_SERVICES (via cds bind) or AICORE_SERVICE_KEY.
 */
const https = require("https");

const DEFAULT_STALE_PREFIXES = ["itest-rg-", "cds-itest-"];

function getCredentials() {
  const vcap = JSON.parse(process.env.VCAP_SERVICES || "{}");
  return (
    (vcap.aicore || vcap["ai-core"] || [{}])[0].credentials ||
    JSON.parse(process.env.AICORE_SERVICE_KEY || "null")
  );
}

function request(url, opts = {}) {
  return new Promise((resolve, reject) => {
    const u = new URL(url);
    const req = https.request(
      {
        hostname: u.hostname,
        path: u.pathname + u.search,
        method: opts.method || "GET",
        headers: opts.headers || {},
      },
      (res) => {
        let data = "";
        res.on("data", (chunk) => (data += chunk));
        res.on("end", () => resolve({ status: res.statusCode, body: data }));
      }
    );
    req.on("error", reject);
    if (opts.body) req.write(opts.body);
    req.end();
  });
}

async function getAccessToken(credentials) {
  const tokenUrl = credentials.url + "/oauth/token";
  const params = new URLSearchParams({ grant_type: "client_credentials" });
  const authHeader =
    "Basic " +
    Buffer.from(credentials.clientid + ":" + credentials.clientsecret).toString(
      "base64"
    );
  const res = await request(tokenUrl + "?" + params.toString(), {
    headers: { Authorization: authHeader },
  });
  return JSON.parse(res.body).access_token;
}

async function deleteResourceGroups(apiUrl, headers, prefixes) {
  const res = await request(apiUrl + "/v2/admin/resourceGroups", { headers });
  const groups = JSON.parse(res.body).resources || [];
  const toDelete = groups.filter(
    (rg) =>
      rg.resourceGroupId &&
      prefixes.some((p) => rg.resourceGroupId.startsWith(p))
  );

  for (const rg of toDelete) {
    const delRes = await request(
      apiUrl + "/v2/admin/resourceGroups/" + rg.resourceGroupId,
      { method: "DELETE", headers }
    );
    console.log("Delete", rg.resourceGroupId, "->", delRes.status);
  }

  console.log("Cleaned up", toDelete.length, "resource groups");
}

async function main() {
  const ownPrefix = process.env.RESOURCE_GROUP_PREFIX;
  if (!ownPrefix) {
    console.error("RESOURCE_GROUP_PREFIX environment variable is required");
    process.exit(1);
  }

  const credentials = getCredentials();
  if (!credentials) {
    console.log("No AI Core credentials found, skipping cleanup");
    return;
  }

  const stalePrefixes = process.env.STALE_PREFIXES
    ? process.env.STALE_PREFIXES.split(",").map((s) => s.trim())
    : DEFAULT_STALE_PREFIXES;

  const prefixes = [ownPrefix, ...stalePrefixes];

  const apiUrl = credentials.serviceurls.AI_API_URL;
  const token = await getAccessToken(credentials);
  const headers = {
    Authorization: "Bearer " + token,
    "AI-Resource-Group": "default",
  };

  console.log("Cleaning resource groups matching prefixes:", prefixes);
  await deleteResourceGroups(apiUrl, headers, prefixes);
}

main().catch((e) => {
  console.error(e.message);
  process.exit(0);
});
