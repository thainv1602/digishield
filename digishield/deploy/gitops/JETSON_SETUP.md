# DigiShield on 2× Jetson (k3s + ArgoCD)

Target: a 2-node arm64 k3s cluster on two Jetson boards (8 GB RAM / 256 GB SSD
each), with ArgoCD pulling everything from this repo (GitOps). CI builds a
multi-arch image (amd64 + arm64) and bumps the tag in
`deploy/helm/digishield/values-jetson.yaml`; ArgoCD does the actual rollout.

```
Jetson #1 (server)                     Jetson #2 (agent)
├─ k3s control-plane + worker          ├─ k3s worker
├─ ArgoCD (ns: argocd)                 ├─ api/worker pods
├─ postgres / redis / rabbitmq         └─ ...
└─ Traefik (bundled with k3s) :80/:443
```

## 0. Prerequisites (both Jetsons)

- JetPack 6.x (Ubuntu 22.04), static LAN IPs recommended (e.g. `192.168.1.11` / `192.168.1.12`).
- Verify the memory cgroup is enabled (needed by k3s):

  ```bash
  grep memory /proc/cgroups   # last column must be 1
  ```

  If it is `0`, append `cgroup_enable=cpuset cgroup_enable=memory cgroup_memory=1`
  to the `APPEND` line in `/boot/extlinux/extlinux.conf` and reboot.

## 1. Install k3s

On **Jetson #1** (server):

```bash
curl -sfL https://get.k3s.io | sh -s - server \
  --node-name jetson-1 \
  --write-kubeconfig-mode 644
sudo cat /var/lib/rancher/k3s/server/node-token   # copy for the agent
```

On **Jetson #2** (agent):

```bash
curl -sfL https://get.k3s.io | \
  K3S_URL=https://192.168.1.11:6443 \
  K3S_TOKEN=<node-token> \
  sh -s - agent --node-name jetson-2
```

Check from Jetson #1: `kubectl get nodes` → both `Ready`.

To run kubectl from your laptop: copy `/etc/rancher/k3s/k3s.yaml` from
Jetson #1, replace `127.0.0.1` with `192.168.1.11`, save as `~/.kube/config`
(or merge into it).

## 2. Install ArgoCD

```bash
kubectl create namespace argocd
kubectl apply -n argocd \
  -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
kubectl -n argocd rollout status deploy/argocd-server --timeout 5m
```

UI access (from a machine with kubectl):

```bash
kubectl -n argocd port-forward svc/argocd-server 8443:443
# admin password:
kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath='{.data.password}' | base64 -d; echo
```

Open https://localhost:8443 (user `admin`).

## 3. Create the app secrets (once, never in git)

```bash
kubectl create namespace digishield

kubectl -n digishield create secret generic digishield-db \
  --from-literal=password='<strong-db-password>'

kubectl -n digishield create secret generic digishield-rabbit \
  --from-literal=username='digishield' \
  --from-literal=password='<strong-rabbit-password>'
```

If the GHCR package `ghcr.io/thainv1602/digishield/app` is **private**, also:

```bash
kubectl -n digishield create secret docker-registry ghcr-pull \
  --docker-server=ghcr.io --docker-username=thainv1602 \
  --docker-password='<PAT with read:packages>'
```

and uncomment `imagePullSecrets` in `values-jetson.yaml`.

## 4. Bootstrap GitOps (the only manual apply)

```bash
kubectl apply -f digishield/deploy/gitops/jetson/root-app.yaml
```

The root app pulls `deploy/gitops/jetson/apps/`, which creates:

- **digishield-infra** — Postgres (20 Gi PVC), Redis, RabbitMQ (plain
  manifests, arm64-native images, k3s `local-path` storage).
- **digishield** — the Helm chart with `values-jetson.yaml`; the Flyway
  migration Job runs as a PreSync hook before each rollout.

Watch it converge: ArgoCD UI, or `kubectl -n digishield get pods -w`.

## 5. Smoke test

```bash
# Traefik listens on :80 of both nodes:
curl http://192.168.1.11/actuator/health
```

## 6. Enable CI → GitOps rollouts

In GitHub → repo → Settings → Secrets and variables → Actions → Variables, set
`JETSON_DEPLOY_ENABLED=true`. From then on every push to `main`:

1. `build` builds the image natively per arch (amd64 + arm64 runners),
   `build-push` merges them into one multi-arch manifest.
2. Trivy scan + cosign sign/attest run against the pinned digest.
3. `deploy-jetson` commits the new tag into `values-jetson.yaml`
   (`[skip ci]` so it doesn't re-trigger CD).
4. ArgoCD (default ~3 min poll) syncs the new tag onto the cluster.

Note: if `main` has branch protection that blocks pushes from Actions, allow
the `github-actions[bot]` to bypass it, or switch the bump to a PR flow.

## Notes & known trade-offs

- **Postgres pinning**: the `local-path` PV gets node affinity on first
  schedule, so Postgres sticks to that Jetson afterwards. Back up with
  `kubectl -n digishield exec postgres-0 -- pg_dump -U digishield digishield`.
- **Memory budget** (per 8 GB node): k3s ~0.6 GB, ArgoCD ~0.7 GB, app pods
  capped by chart limits (api 1 Gi ×2, worker 1 Gi, scheduler 0.5 Gi),
  infra ~2 Gi total — comfortable across two nodes.
- **LAN-only for now**: no TLS/ingress hostname. To expose publicly later,
  reuse the dev pattern (DuckDNS + Let's Encrypt) but terminate on Traefik.
- **RabbitMQ UI**: `kubectl -n digishield port-forward svc/rabbitmq 15672:15672`.
