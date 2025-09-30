

# ChatGPT MCP (Model Context Protocol) 連接器服務

一個基於Spring Boot的智能向量搜尋服務，整合MCP協定、OAuth2認證和語意相似性搜尋技術，支援從Confluence獲取文檔並建立向量索引進行智能問答。

## 🏗️ 技術架構

### 核心技術棧
- **Java 21** - 使用最新LTS版本，支援虛擬線程等新特性
- **Spring Boot 3.3.3** - 企業級微服務框架
- **Spring Security 6** - 提供OAuth2 Resource Server支援
- **Apache Lucene 9.8** - HNSW向量搜尋引擎
- **Docker Compose** - 容器化部署和服務編排

### 外部服務依賴
- **KeyCloak 26.0** - 開源身份認證和授權管理
- **Ollama** - 本地LLM模型服務（支援bge-m3:567m向量模型）
- **Confluence API** - 知識庫內容獲取

### 核心功能模組
- **向量化處理** - 文檔分塊和embedding生成
- **相似性搜尋** - 基於HNSW算法的高性能向量檢索
- **身份驗證** - OAuth2.0 JWT Token驗證
- **權限控制** - 基於電子郵件白名單的細粒度授權

## 🚀 快速開始

### 環境準備

1. **啟動基礎設施服務**

# 啟動KeyCloak和Ollama服務

# 等待服務完全啟動（大約30秒）

2. **設定環境變數**

# Confluence API 配置
export CONFLUENCE_BASE_URL="https://your-domain.atlassian.net/wiki"
export CONFLUENCE_USERNAME="your-email@example.com"
export CONFLUENCE_API_TOKEN="your-api-token"

# OpenAI API（如需要）
export OPENAI_API_KEY="your-openai-key"

3. **配置KeyCloak**
- 訪問 `http://localhost:8090`
- 使用管理員賬號：`admin/admin`
- 創建新的Realm和Client
- 配置OAuth2客戶端設定

### 應用程式啟動

# 編譯和啟動Spring Boot應用
mvn clean package
mvn spring-boot:run

# 或使用Docker
docker build -t mcp-connector .
docker run -p 8080:8080 mcp-connector

## 📖 使用方法

### 1. 文檔索引建立

**根據頁面ID索引**


curl -X POST http://localhost:8080/ingest \
-H 'Content-Type: application/json' \
-H 'Authorization: Bearer YOUR_JWT_TOKEN' \
-d '{
"pageIds": ["123456", "789012"],
"chunkSize": 800,
"chunkOverlap": 120
}'

**根據URL索引**


curl -X POST http://localhost:8080/ingest \
-H 'Content-Type: application/json' \
-H 'Authorization: Bearer YOUR_JWT_TOKEN' \
-d '{
"pageUrls": [
"https://your-domain.atlassian.net/wiki/spaces/SPACE/pages/123456/Title",
"https://your-domain.atlassian.net/wiki/spaces/SPACE/pages/789012/Another+Title"
],
"chunkSize": 1000,
"chunkOverlap": 100
}'

### 2. 智能查詢

curl "http://localhost:8080/query?q=payment+webhook+signature&k=5" \
-H 'Authorization: Bearer YOUR_JWT_TOKEN'

**查詢參數說明**
- `q` - 查詢關鍵字或語句
- `k` - 返回結果數量（預設5）
- `threshold` - 相似度閾值（可選）

### 3. 健康檢查

# 無需認證的健康檢查端點
curl http://localhost:8080/api/health

# 完整系統狀態（需要認證）
curl http://localhost:8080/api/status \
-H 'Authorization: Bearer YOUR_JWT_TOKEN'

## 🔐 安全性保障

### 認證機制
- **OAuth2.0 + JWT** - 使用標準的身份令牌驗證
- **KeyCloak整合** - 企業級身份認證管理
- **令牌驗簽** - 自動驗證JWT簽章和有效期

### 授權控制

**電子郵件白名單**

yaml
# application.yml
security:
whitelist:
emails:
- admin@company.com
- user@company.com
domains:
- company.com
- partner.org

**IP位址限制**
- 本機請求（127.0.0.1, localhost）自動放行
- 遠程請求必須通過OAuth2驗證

### 資料保護
- **傳輸加密** - 支援HTTPS/TLS
- **敏感資訊保護** - API Token透過環境變數管理
- **會話管理** - 無狀態JWT Token，避免會話劫持

### 安全最佳實踐

1. **生產環境配置**

bash
# KeyCloak生產模式
- --hostname=https://your-domain.com
- --proxy-headers=xforwarded
- --https-certificate-file=/path/to/cert.pem

2. **網路隔離**

yaml
# docker-compose.yml - 生產配置
networks:
internal:
driver: bridge
internal: true
public:
driver: bridge

3. **機密管理**


# 使用Docker Secrets或K8s ConfigMap
echo "your-secret" | docker secret create confluence_token -

## 📊 效能調優

### 向量搜尋優化
- **HNSW參數調整** - 根據資料量調整M和efConstruction
- **分塊策略** - 合理設定chunkSize和overlap
- **記憶體管理** - 配置適當的JVM堆大小

### Ollama模型配置

# 拉取更大的embedding模型（更高精度）
ollama pull bge-large-en:1.5b

# 配置GPU加速（如可用）
docker-compose up ollama --gpus all

## 🔧 開發和調試

### 本地開發環境

bash
# 開發模式啟動（跳過認證）
mvn spring-boot:run -Dspring.profiles.active=dev

# 使用內嵌H2資料庫
spring.profiles.active=dev,h2

### 日誌配置

yaml
# logback-spring.xml
logging:
level:
com.dylincode.mcp: DEBUG
org.springframework.security: DEBUG
org.apache.lucene: INFO

### 健康檢查和監控

bash
# Actuator端點
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/metrics
curl http://localhost:8080/actuator/prometheus

## 🐳 Docker部署

### 生產環境Docker Compose

yaml
version: '3.8'
services:
mcp-connector:
build: .
ports:
- "8080:8080"
environment:
- SPRING_PROFILES_ACTIVE=production
- SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI=https://your-keycloak/realms/your-realm/protocol/openid_connect/certs
depends_on:
- keycloak
- ollama
restart: unless-stopped


