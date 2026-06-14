# Aliyun Deployment

Use Aliyun Lightweight Application Server or ECS.

Recommended minimum:

- Region: mainland China near your contacts, for example Hangzhou, Shanghai, Shenzhen, or Beijing.
- Image: Ubuntu 22.04 or Debian 12.
- Spec: 1 vCPU / 1 GB RAM is enough for this relay.
- Bandwidth: 3 Mbps or more is fine.
- Open inbound TCP port: `8787`.

Important:

- Buying or creating the server may consume your 300 CNY voucher or create billable resources.
- The browser automation environment cannot access the Aliyun console, so create the server manually in Chrome.

After the server is created, send the public IP here. If SSH key login is enabled, also tell me the local key path. I can then run:

```powershell
powershell -ExecutionPolicy Bypass -File work\LiveCircle\aliyun\deploy-livecircle.ps1 -HostName YOUR_PUBLIC_IP
```

When deployment is done, the Android app server URL should be:

```text
http://YOUR_PUBLIC_IP:8787
```

Health check:

```text
http://YOUR_PUBLIC_IP:8787/api/health
```
