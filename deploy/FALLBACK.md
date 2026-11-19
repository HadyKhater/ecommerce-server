# Fallback if ARM capacity is never available

## Option A — keep the full stack, take the smaller ARM shape

Retry the A1 shape at **2 OCPU / 12 GB**, switching Availability Domains.
12 GB is plenty: the full stack uses roughly 3.5–4 GB RAM (9 JVMs with
`-Xmx256..384m` + Postgres + Caddy). This is the best option.

## Option B — Always Free AMD micro (1 GB) + reduced demo

Oracle's `VM.Standard.E2.1.Micro` (1 OCPU / 1 GB, x86) is usually available.
It cannot run nine JVMs. Use `docker-compose.demo.yml` instead:

```bash
# after installing Docker (use amd64 packages automatically)
sudo git clone --branch main https://github.com/HadyKhater/ecommerce-server.git /opt/ecommerce
sudo chown -R ubuntu:ubuntu /opt/ecommerce
cd /opt/ecommerce
cp .env.example .env
# point POSTGRES_* at a free Neon/Supabase Postgres (or keep the local postgres profile)
# generate JWT keys with scripts/setup-vm.sh, then:
docker compose -f docker-compose.demo.yml up -d --build
```

Add swap so the small VM does not OOM:

```bash
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

**Limitation:** the reduced demo includes eureka, gateway, auth, product and
order. Register/login and product browsing work. Full checkout needs inventory,
cart, payment and notification services, so keep the full A1 stack for that.

## Option C — free managed Postgres to save VM RAM

Neon's free tier gives 3 projects / 0.5 GB storage each. Point each
`POSTGRES_HOST` at the Neon host and set `POSTGRES_PASSWORD`; remove the local
`postgres` service from the compose file. No Redis or broker is needed — this
project uses Caffeine in-process caching and has no Kafka/RabbitMQ dependency.
