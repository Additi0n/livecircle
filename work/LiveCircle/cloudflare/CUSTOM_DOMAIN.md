# Bind a Custom Domain

If you deploy the optional Cloudflare Worker version, it may get a default workers.dev hostname like:

```text
https://YOUR_WORKER.YOUR_ACCOUNT.workers.dev
```

This hostname may be unreliable on some mainland China mobile networks.

## Required

Add a domain to the same Cloudflare account first. The account currently has no zones/domains.

After a domain is active in Cloudflare, add a custom domain to the Worker:

```powershell
cd work\LiveCircle\cloudflare
npx wrangler triggers --help
```

You can also bind it in the Cloudflare dashboard:

```text
Workers & Pages -> livecircle-relay -> Settings -> Domains & Routes -> Add Custom Domain
```

Recommended hostname:

```text
livecircle.your-domain.com
```

Then update the Android app's `Server URL` field to:

```text
https://livecircle.your-domain.com
```

If you want the APK default changed permanently, edit:

```text
android/app/src/main/java/com/codex/livecircle/AppConfig.java
```

and replace `DEFAULT_SERVER_URL`.
