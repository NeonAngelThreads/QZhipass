# Copy the values you need into your own PowerShell session before starting the backend.
# Do not commit real passwords or API keys.

$env:MYSQL_URL = "jdbc:mysql://127.0.0.1:3306/qzhipass?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
$env:MYSQL_USERNAME = "root"
$env:MYSQL_PASSWORD = "replace-with-your-mysql-password"

$env:REDIS_HOST = "127.0.0.1"
$env:REDIS_PORT = "6379"
$env:REDIS_PASSWORD = "replace-with-your-redis-password"

# Use a random secret of at least 32 UTF-8 bytes. Each developer should use their own local value.
$env:JWT_SECRET = "replace-with-a-random-secret-at-least-32-bytes"
$env:JWT_EXPIRATION_MS = "28800000"

$env:AI_API_KEY = "replace-with-the-test-ai-key"
$env:AI_API_BASE = "https://api.deepseek.com/v1"
$env:AI_CHAT_MODEL = "deepseek-chat"

$env:SPRING_PROFILES_ACTIVE = "mysql-local"
.\mvnw.cmd spring-boot:run
