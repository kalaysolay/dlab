# Настройка нового VPS и deployment Damulab.kz

Инструкция рассчитана на чистый **Ubuntu 22.04**, установленный nginx, домен
`damulab.kz` и репозиторий `git@github.com:kalaysolay/dlab.git`.

Production-схема:

```text
Internet -> nginx :80/:443 -> 127.0.0.1:8080 -> Spring Boot container
                                               -> PostgreSQL container
```

PostgreSQL не публикуется в интернет. Порт приложения доступен только через
loopback, поэтому наружу открыты лишь SSH, HTTP и HTTPS.

## 0. Что понадобится

- IP нового VPS;
- пользователь с `sudo` и рабочий вход по SSH-ключу;
- доступ VPS к GitHub-репозиторию;
- `OPENAI_API_KEY`, если нужны реальные AI-вызовы;
- email для Let's Encrypt;
- обе A-записи `damulab.kz` и `www.damulab.kz`, если будет использоваться `www`.

Если A-записи `www` нет, либо создайте её, либо везде ниже удалите
`www.damulab.kz`: из `server_name` и команды Certbot. Иначе сертификат не
выпустится.

## 1. Проверить DNS до настройки TLS

На своем компьютере:

```bash
dig +short damulab.kz A
dig +short www.damulab.kz A
```

Обе команды должны вернуть IP нового VPS. Также проверьте, что не осталось
старой AAAA-записи: если IPv6 на новом VPS не настроен, удалите её.

```bash
dig +short damulab.kz AAAA
dig +short www.damulab.kz AAAA
```

## 2. Обновить ОС и настроить firewall

Подключиться к VPS и выполнить:

```bash
sudo apt update
sudo apt full-upgrade -y
sudo apt install -y ca-certificates curl git openssl ufw unattended-upgrades
sudo timedatectl set-timezone Asia/Almaty
```

Сначала разрешите SSH, и только потом включайте UFW, иначе можно потерять
доступ к серверу:

```bash
sudo ufw allow OpenSSH
sudo ufw allow 'Nginx Full'
sudo ufw enable
sudo ufw status verbose
```

Ожидаются входящие порты `22`, `80`, `443`. Порты `5432` и `8080` открывать
не нужно. Compose дополнительно привязывает `8080` только к `127.0.0.1`.

Проверить автоматические security-обновления:

```bash
systemctl status unattended-upgrades --no-pager
```

### Рекомендуемое усиление SSH

Не отключайте парольный вход, пока вход по ключу не проверен во второй SSH-сессии.
После проверки создайте `/etc/ssh/sshd_config.d/99-hardening.conf`:

```text
PermitRootLogin no
PasswordAuthentication no
PubkeyAuthentication yes
```

Затем:

```bash
sudo sshd -t
sudo systemctl reload ssh
```

## 3. Установить Docker Engine и Compose plugin

Используется официальный apt-репозиторий Docker, а не старый standalone
`docker-compose`:

```bash
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
```

```bash
echo "Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}")
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc" | sudo tee /etc/apt/sources.list.d/docker.sources >/dev/null
```

```bash
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
sudo docker run --rm hello-world
sudo docker compose version
```

Дальше команды используют `sudo docker`. Добавлять пользователя в группу
`docker` необязательно: эта группа фактически даёт root-доступ к серверу.

## 4. Дать серверу доступ к репозиторию

Для приватного репозитория создайте отдельный read-only deploy key:

```bash
ssh-keygen -t ed25519 -C "damulab-production" -f ~/.ssh/damulab_deploy -N ""
cat ~/.ssh/damulab_deploy.pub
```

Добавьте выведенный публичный ключ в GitHub: repository **Settings -> Deploy
keys -> Add deploy key**, без `Allow write access`. Затем создайте SSH config:

```text
Host github.com
  HostName github.com
  User git
  IdentityFile ~/.ssh/damulab_deploy
  IdentitiesOnly yes
```

Права и проверка:

```bash
chmod 700 ~/.ssh
chmod 600 ~/.ssh/config ~/.ssh/damulab_deploy
chmod 644 ~/.ssh/damulab_deploy.pub
ssh -T git@github.com
```

GitHub отвечает сообщением об успешной аутентификации и сообщает, что shell
access не предоставляется — это нормально.

## 5. Клонировать проект и создать production env

```bash
sudo mkdir -p /opt/damulab
sudo chown "$USER":"$USER" /opt/damulab
git clone git@github.com:kalaysolay/dlab.git /opt/damulab
cd /opt/damulab
cp .env.prod.example .env.prod
chmod 600 .env.prod
openssl rand -hex 32
nano .env.prod
```

Вставьте результат `openssl rand -hex 32` в `POSTGRES_PASSWORD`. Минимально
проверьте следующие значения:

```dotenv
POSTGRES_DB=damulab
POSTGRES_USER=damulab
POSTGRES_PASSWORD=<случайный длинный пароль>
OPENAI_API_KEY=<ключ OpenAI или пусто>
WEBAUTHN_RP_ID=damulab.kz
WEBAUTHN_ALLOWED_ORIGINS=https://damulab.kz
```

Если AI пока не нужен, безопаснее явно установить аварийный выключатель:

```dotenv
AI_REAL_PROVIDERS_ENABLED=false
```

Активные провайдеры и модели для вопросов и лекций выбираются после входа в
`Админка → Настройки AI`. Новая установка начинает со Stub для обоих сценариев.

Не коммитьте `.env.prod` и не отправляйте его в чаты. Файл уже добавлен в
`.gitignore`.

## 6. Собрать и запустить приложение

Проверить Compose-конфиг без печати секретов и запустить контейнеры:

```bash
cd /opt/damulab
sudo docker compose --env-file .env.prod -f docker-compose.prod.yml config --quiet
sudo docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build
sudo docker compose --env-file .env.prod -f docker-compose.prod.yml ps
sudo docker compose --env-file .env.prod -f docker-compose.prod.yml logs --tail=150 app
```

При первом старте Flyway автоматически создаст и обновит схему БД. В логах не
должно быть stack trace или ошибок Flyway.

Проверить приложение с самого VPS:

```bash
curl -I http://127.0.0.1:8080/
```

Код `200` или допустимый redirect `3xx` означает, что приложение отвечает.

Production-профиль отключает `DemoUserSeeder`: аккаунты
`admin@damulab.kz`, `student@damulab.kz`, `parent@damulab.kz` с паролем
`password` на чистой production-БД больше не создаются.

## 7. Подключить nginx по HTTP

```bash
cd /opt/damulab
sudo systemctl enable --now nginx
sudo cp deploy/nginx/damulab.conf /etc/nginx/sites-available/damulab.conf
sudo ln -sfn /etc/nginx/sites-available/damulab.conf /etc/nginx/sites-enabled/damulab.conf
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl reload nginx
curl -I http://damulab.kz
```

На этом этапе сайт должен отвечать по HTTP. Не переходите к Certbot, пока эта
проверка не работает.

## 8. Выпустить HTTPS-сертификат

Certbot рекомендует snap-установку:

```bash
sudo snap install core
sudo snap refresh core
sudo snap install --classic certbot
sudo ln -sfn /snap/bin/certbot /usr/local/bin/certbot
sudo certbot --nginx -d damulab.kz -d www.damulab.kz
```

Укажите email, примите условия и выберите redirect HTTP -> HTTPS. Затем:

```bash
sudo nginx -t
sudo systemctl reload nginx
sudo certbot renew --dry-run
curl -I https://damulab.kz
systemctl list-timers | grep certbot
```

Проверьте в браузере `https://damulab.kz`: сертификат должен быть валидным,
а редиректы после логина должны оставаться на HTTPS.

## 9. Создать первого администратора

1. Откройте `https://damulab.kz/register` и зарегистрируйте свой реальный
   аккаунт как родителя или ученика с сильным паролем.
2. На VPS замените email в SQL и выполните:

```bash
cd /opt/damulab
sudo docker compose --env-file .env.prod -f docker-compose.prod.yml exec postgres \
  sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

В открывшемся `psql`:

```sql
BEGIN;
DELETE FROM student_profiles WHERE user_id = (SELECT id FROM app_users WHERE lower(email) = lower('YOUR_EMAIL'));
DELETE FROM parent_profiles  WHERE user_id = (SELECT id FROM app_users WHERE lower(email) = lower('YOUR_EMAIL'));
DELETE FROM user_roles       WHERE user_id = (SELECT id FROM app_users WHERE lower(email) = lower('YOUR_EMAIL'));
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM app_users u
CROSS JOIN roles r
WHERE lower(u.email) = lower('YOUR_EMAIL') AND r.code = 'ADMIN';
COMMIT;
```

Проверьте результат до выхода:

```sql
SELECT u.email, r.code
FROM app_users u
JOIN user_roles ur ON ur.user_id = u.id
JOIN roles r ON r.id = ur.role_id
WHERE lower(u.email) = lower('YOUR_EMAIL');
\q
```

Должна остаться роль `ADMIN`.

## 10. Проверить production

```bash
sudo docker compose --env-file /opt/damulab/.env.prod -f /opt/damulab/docker-compose.prod.yml ps
sudo docker compose --env-file /opt/damulab/.env.prod -f /opt/damulab/docker-compose.prod.yml logs --tail=200 app
sudo ss -lntp
```

Итоговый checklist:

- `80` и `443` слушает nginx;
- `127.0.0.1:8080` слушает Docker proxy, но `0.0.0.0:8080` отсутствует;
- `5432` отсутствует среди внешних listening ports;
- `https://damulab.kz` открывается без предупреждения;
- регистрация, вход, logout и загрузка вложения работают;
- Passkeys работают только с production-значениями `WEBAUTHN_*`;
- при заданном OpenAI-ключе генерация проходит с VPS из Нидерландов;
- после перезагрузки VPS контейнеры поднимаются (`restart: unless-stopped`).

Перезагрузку можно проверить в согласованное окно:

```bash
sudo reboot
```

После повторного SSH-входа выполните `docker compose ps` и `curl -I` из шагов
выше.

## 11. Обычное обновление приложения

Сначала создать backup, затем получить только fast-forward изменения:

```bash
sudo bash /opt/damulab/deploy/backup.sh
cd /opt/damulab
git pull --ff-only
sudo docker compose --env-file .env.prod -f docker-compose.prod.yml build app
sudo docker compose --env-file .env.prod -f docker-compose.prod.yml up -d
sudo docker compose --env-file .env.prod -f docker-compose.prod.yml logs --tail=150 app
curl -I https://damulab.kz
```

Compose пересоздаёт только изменившийся контейнер приложения. PostgreSQL и
именованные volumes сохраняются.

Перед деплоем желательно прогнать тесты локально:

```powershell
.\gradlew.bat test
```

## 12. Backup

Скрипт сохраняет custom-format dump PostgreSQL и архив вложений, затем удаляет
локальные backup старше 14 дней:

```bash
sudo chmod +x /opt/damulab/deploy/backup.sh
sudo mkdir -p /var/backups/damulab
sudo bash /opt/damulab/deploy/backup.sh
sudo ls -lh /var/backups/damulab
```

Добавить ежедневный запуск от root в 03:15:

```bash
sudo crontab -e
```

```cron
15 3 * * * /opt/damulab/deploy/backup.sh >> /var/log/damulab-backup.log 2>&1
```

Локальный диск VPS не является полноценным backup. Регулярно копируйте архивы
на отдельное хранилище и периодически проверяйте восстановление.

## 13. Диагностика

```bash
cd /opt/damulab
sudo docker compose --env-file .env.prod -f docker-compose.prod.yml ps
sudo docker compose --env-file .env.prod -f docker-compose.prod.yml logs -f app
sudo docker compose --env-file .env.prod -f docker-compose.prod.yml logs --tail=100 postgres
sudo nginx -t
sudo journalctl -u nginx -n 100 --no-pager
sudo ufw status verbose
df -h
free -h
```

Частые причины:

| Симптом | Проверка |
|---|---|
| `502 Bad Gateway` | контейнер `app` не запущен или не отвечает на `127.0.0.1:8080` |
| app перезапускается | `docker compose logs app`; обычно DB env или Flyway |
| Certbot не выпускает сертификат | DNS ещё указывает на старый IP, неверная AAAA-запись или закрыт порт 80 |
| OpenAI не отвечает | проверить `OPENAI_API_KEY`, логи приложения и исходящий HTTPS с VPS |
| Passkey отклоняется | `WEBAUTHN_RP_ID=damulab.kz`, origin строго `https://damulab.kz` |
| вложения исчезли | не подключён volume `lecture-attachments` или был удалён volume |

Не запускайте `docker compose down -v`: ключ `-v` удаляет данные PostgreSQL и
вложения. Обычный `docker compose down` volumes не удаляет.

## Источники по системным пакетам

- [Docker Engine for Ubuntu](https://docs.docker.com/engine/install/ubuntu/)
- [Docker Compose plugin](https://docs.docker.com/compose/install/linux/)
- [Ubuntu firewall documentation](https://documentation.ubuntu.com/server/how-to/security/firewalls/)
- [Certbot with nginx](https://certbot.eff.org/instructions?ws=nginx&os=snap)
