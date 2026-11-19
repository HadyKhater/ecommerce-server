# Oracle Cloud Always Free — click-by-click

> **Card warning (read first):** Oracle asks for a credit/debit card to verify
> identity. The Always Free tier itself costs nothing, but a small temporary
> verification hold may appear on your statement. You will not be charged for
> these resources as long as you stay inside Always Free limits. **If you are
> not comfortable with this step, stop here and use the fallback in
> `deploy/FALLBACK.md`.**

## 1. Create the account

1. Open <https://www.oracle.com/cloud/free/> and click **Start for free**.
2. Enter country, email, accept the terms, click **Next**.
3. Fill in personal details and verify the email (code from your inbox).
4. Enter card details for identity verification and complete signup.
5. Choose your **Home Region** (pick the one geographically closest to you —
   you cannot change it later). Wait for "Your account is being activated".

## 2. Create the ARM VM

1. Log in at <https://cloud.oracle.com>.
2. Hamburger menu (☰) → **Compute** → **Instances**.
3. Click **Create Instance**.
4. Name: `ecommerce-portfolio`.
5. **Image:** choose **Canonical Ubuntu 22.04 LTS aarch64** (ARM). Do **not**
   pick x86/x64.
6. **Shape:** click **Change Shape** → **Virtual Machine** →
   **VM.Standard.A1.Flex**.
   - Set **4 OCPUs** and **24 GB RAM** (the full Always Free ARM allowance).
   - If you see **"Out of capacity"**:
     1. Try **2 OCPUs / 12 GB** first.
     2. Change the **Availability Domain** (AD-1 → AD-2 → AD-3).
     3. Retry at a different time (early morning UTC works best); capacity is
        released continuously.
     4. If it never works, follow `deploy/FALLBACK.md`.
7. **Networking:** keep the default VCN/subnet, and make sure **Assign a public
   IPv4 address** is selected.
8. **SSH keys:** click **Generate a new key pair** and download the private
   key, or upload your own public key. On Windows PowerShell:
   ```powershell
   ssh-keygen -t ed25519 -C "you@example.com"
   # paste the contents of $env:USERPROFILE\.ssh\id_ed25519.pub into the form
   ```
9. Click **Create**. Note the **Public IP** from the instance detail page.

## 3. Open ports in Oracle (cloud firewall)

1. On the instance page, click the **VCN** link → **Security Lists** →
   **Default Security List for ...**.
2. Click **Add Ingress Rules** twice:
   - Rule 1: Source CIDR `0.0.0.0/0`, IP Protocol **TCP**, Destination Port
     Range **80**, description `HTTP Caddy`.
   - Rule 2: Source CIDR `0.0.0.0/0`, IP Protocol **TCP**, Destination Port
     Range **443**, description `HTTPS Caddy`.
3. Click **Add Ingress Rules**. (Leave SSH 22 as created by Oracle.)

## 4. Open ports on the VM itself

SSH in first (Ubuntu image user is `ubuntu`):

```bash
ssh -i ~/.ssh/id_ed25519 ubuntu@<PUBLIC_IP>
```

Then:

```bash
sudo apt update && sudo apt install -y ufw
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw --force enable
sudo ufw status
```

## 5. Install Docker + Compose plugin (ARM64 packages)

```bash
sudo apt update
sudo apt install -y ca-certificates curl git openssl gnupg
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu jammy stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker ubuntu
newgrp docker
docker --version && docker compose version
```

`dpkg --print-architecture` must print `arm64`. All images used in the compose
file (`maven:3.9-eclipse-temurin-21`, `eclipse-temurin:21-jre`,
`postgres:16-alpine`, `caddy:2-alpine`, `busybox:1.36`) publish ARM64 variants.

## 6. Clone and start

```bash
sudo git clone --branch main https://github.com/HadyKhater/ecommerce-server.git /opt/ecommerce
sudo chown -R ubuntu:ubuntu /opt/ecommerce
cd /opt/ecommerce
bash scripts/setup-vm.sh
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml ps
```

`setup-vm.sh` detects the public IP, writes the gitignored `.env`, generates a
Postgres password, an internal gateway secret and an RSA-2048 JWT keypair, and
sets `CADDY_DOMAIN=<ip-with-dashes>.sslip.io`.

### Frontend origin (Bazario on Cloudflare Pages)

Create the Cloudflare Pages project named `bazario`
(`https://bazario.pages.dev`) and allow it as a browser origin in `.env`:

```bash
FRONTEND_URL=https://bazario.pages.dev
CORS_ALLOWED_ORIGINS=https://bazario.pages.dev,http://localhost:4200
```

Restart the gateway after changing CORS: `docker compose -f docker-compose.prod.yml up -d --force-recreate gateway`.

## 7. Verify

```bash
# wait ~2 minutes for Eureka registrations + Let's Encrypt
curl -fsS https://<ip-with-dashes>.sslip.io/health
bash scripts/seed-demo.sh
docker compose -f docker-compose.prod.yml logs -f --tail=100
```

Expected: Caddy obtains a certificate, all 11 containers are `healthy`, and
the seed script prints 10 products and 5 categories.
