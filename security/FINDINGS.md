# Security Findings: Jeed

Suggestions from the Docker Hub image audit in `~/claude/docker-image-scan`, for whoever works in
this repository next. Every item is **Open** until a fixed image is built, pushed, and deployed.
The generated detail lives in `security/docker-image-scan/` (gitignored, rewritten on every audit
run); this file is written by hand and carries the reasoning.

- **Scanned:** 2026-09-11, docker scout against the registry:
  - `cs124/jeed:latest` (= `2026.9.2`, `sha256:c4ba4eb0c1af`): 1 critical, 0 high
  - `cs124/jeed-proxy:latest` (= `2025.12.4`, `sha256:fe9fa5705378`): 6 critical, 53 high
- An earlier report here showed `cs124/jeed` clean. That was stale: the audit reused an old scan
  without checking the image digest. It now rescans whenever the digest changes or a result is
  more than seven days old.

## Progress (2026-09-11)

Applied in the working tree, not yet committed, pushed, or deployed. Every item stays Open until then.

- **1. netty:** the BOM is in place, `:server` resolves every netty module at 4.2.18.Final, the full
  core and server suites pass (999 and 50 tests), and `cs124/jeed:2026.9.5` built locally scans
  0 critical, 0 high.
- **2. jeed-proxy:** `js/proxy/Dockerfile` pins `node:24.21.0-alpine3.24`, both `docker:push` and
  `:server:dockerPush` now build with `--pull --no-cache`, and `js/proxy/.dockerignore` keeps `.env`
  files out of the build context. Built locally the image scans 0 critical, 0 high, 0 medium, 0 low.
  Still needs pushing.
- **3. plexus-utils:** core declares `plexus-utils:4.1.0`, and the generated POM and module metadata
  both carry it. Questioner can drop its constraint once a core release containing this is out.
- **4. deployments:** unchanged.

## Summary

`cs124/jeed` has one critical, cleared by a netty BOM that is **already applied in
`server/build.gradle.kts`, uncommitted, and not yet built or pushed.** `cs124/jeed-proxy` looks far
worse, but its fix is already in the source; nothing has been pushed since 2025-12-31. The third
item is about what core publishes to its consumers, and it is how questioner shipped a high.

## Fix Soon

### 1. cs124/jeed: netty-handler 4.2.16.Final, CVE-2026-75595 (Critical)

- **Path:** `io.ktor:ktor-server-netty:3.5.2` pins netty 4.2.16.Final. 3.5.2 is the newest ktor.
  CVE-2026-75596 (medium) rides along.
- **Fixed in:** 4.2.17.Final, per OSV. Scout's range `>=4.2.0.Final,<=4.2.16.Final` looks like "no
  fix available"; it is not.
- **Change (applied):** `implementation(platform("io.netty:netty-bom:4.2.18.Final"))` beside the
  ktor dependencies; a plain platform, like the jackson BOM already there.
- **Verified:** `:server` `runtimeClasspath` resolves netty-handler and netty-codec-http2 at
  4.2.18.Final. **Not verified here:** no image built or scanned and no tests run. The identical
  change in questioner produced an image that scanned 0 critical, 0 high, and questioner's server
  tests passed.
- **Later:** drop the BOM once a ktor release ships netty 4.2.17.Final or newer.

### 2. cs124/jeed-proxy: Push the Image the Source Already Describes

`latest` is `2025.12.4`, pushed 2025-12-31. Its findings are `alpine/openssl` 3.5.1-r0 (4 critical,
25 high), `alpine/musl`, and the npm CLI's vendored `tar`, `glob`, `minimatch`, `brace-expansion`,
`picomatch`, and `pacote`. The current `js/proxy/Dockerfile` already deals with all of them
(Alpine 3.24, `apk upgrade`, the npm, corepack, and yarn CLIs removed), and `js/proxy/package.json`
is at `2026.9.1`, but no 2026 tag has been pushed.

- **Suggested:** `bun run docker:push` in `js/proxy`, then rescan.
- **Not verified:** the current Dockerfile was not built during this audit.

### 3. core Publishes plexus-utils 3.1.1 to Its Consumers (High, CVE-2025-67030)

`core/build.gradle.kts` forces plexus-utils 4.1.0 because `plexus-container-default:2.1.1` pins
3.1.1. A `resolutionStrategy.force` applies only to this build and is not published, so anything
that depends on `org.cs124.jeed:core` still resolves 3.1.1. Questioner did, and its image carried
the high until it added its own constraint.

- **Suggested:** also declare it as an ordinary dependency in core, which is published:
  `implementation("org.codehaus.plexus:plexus-utils:4.1.0")`. The `force` can stay; the dependency
  is what consumers see. Questioner can then drop its constraint.

### 4. Deployed Tags Predate the Fix

As of 2026-09-11 (`kubectl get pods -A`):

| Namespace | Images |
|---|---|
| `cs124` | `cs124/jeed:2026.9.2` (2 pods), `cs124/jeed-proxy:2025.12.4` |
| `learncsonline` | `cs124/jeed:2026.4.0`, `cs124/jeed-proxy:2025.12.4` |
| `runjeed` | `cs124/jeed:2025.6.0` |

`~/www/jeed.run/docker-compose.yml` also pins `cs124/jeed:2025.3.1`.

## Verify

After pushing:

```bash
docker scout cves --platform linux/amd64 registry://cs124/jeed:latest
docker scout cves --platform linux/amd64 registry://cs124/jeed-proxy:latest
```
