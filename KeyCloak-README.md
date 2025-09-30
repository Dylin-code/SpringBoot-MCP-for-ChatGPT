
# Keycloak 詳細設定指南

## 前置準備

確保 Keycloak 容器已經啟動並可以通過 `http://localhost:8090` 或 `https://{YOUR-HOST}` 訪問。

## 1. 進入 Admin Console

1. 開啟瀏覽器，前往 Keycloak Admin Console
   - 本地: `http://localhost:8090/admin`
   - 外部: `https://{YOUR-HOST}/admin`

2. 使用管理員帳號登入
   - Username: `admin`
   - Password: `admin`

## 2. 建立新的 Realm

1. 在左上角點選 **Master** realm 下拉選單
2. 點選 **Create Realm**
3. 填入 Realm 資訊：
   - **Realm name**: `mcp-realm` (或您偏好的名稱)
   - **Enabled**: 確保為 `ON`
4. 點選 **Create**

## 3. 調整 User Profile

1. 在左側選單選擇 **Realm settings**
2. 點選 **User Profile** 頁籤
3. 檢查並調整以下屬性：
   - **username**: 設為 Required
   - **email**: 設為 Required
   - **firstName**: 設為 Required  
   - **lastName**: 設為 Required

4. 如需新增自訂屬性，點選 **Create attribute**：
   - **Name**: 輸入屬性名稱
   - **Display name**: 顯示名稱
   - **Required**: 根據需求設定
   - **Permissions**: 設定讀寫權限

## 4. Authentication Flow 設定 (可選)

### 設定 No Review Profile

1. 左側選單選擇 **Authentication**
2. 點選 **Flows** 頁籤
3. 找到 **First broker login** flow
4. 點選 **Duplicate** 建立副本
5. 重新命名為 `First broker login - No Review`
6. 在複製的 flow 中：
   - 找到 **Review Profile** step
   - 點選右側的 **Actions** → **Delete**
7. 儲存變更

## 5. 建立 Client 並設定 Trust Host

1. 左側選單選擇 **Clients**
2. 點選 **Create client**
3. 填入基本資訊：
   - **Client type**: `OpenID Connect`
   - **Client ID**: `mcp-client`
   - 點選 **Next**

4. 在 Capability config 頁面：
   - **Client authentication**: `OFF` (public client)
   - **Authorization**: `OFF`
   - **Standard flow**: `ON`
   - **Implicit flow**: `OFF`
   - **Direct access grants**: `ON`
   - 點選 **Next**

5. 在 Login settings 頁面設定：
   - **Root URL**: `https://chat.openai.com`
   - **Home URL**: `https://chat.openai.com`
   - **Valid redirect URIs**: 
     
     https://chat.openai.com/*
     https://chatgpt.com/*
     
   - **Valid post logout redirect URIs**: `https://chat.openai.com/*`
   - **Web origins**: 
     
     https://chat.openai.com
     https://chatgpt.com
     

6. 點選 **Save**

### 5.1 Client registration 新增 Trust Host

1. 進入 Clients -> Client registration -> Trust Hosts
   Trusted Hosts 新增: 
      - chatgpt.com
      - chat.openai.com
      - https://chatgpt.com/connector_platform_oauth_redirect

## 6. 設定 Google 作為 Identity Provider

### 6.1 準備 Google OAuth 2.0 憑證

1. 前往 [Google Cloud Console](https://console.cloud.google.com/)
2. 建立新專案或選擇現有專案
3. 啟用 **Google+ API** 和 **Google Identity** 服務
4. 前往 **憑證** 頁面
5. 點選 **建立憑證** → **OAuth 2.0 用戶端 ID**
6. 選擇 **網頁應用程式**
7. 設定授權重新導向 URI：
   
https://{YOUR-HOST}/realms/mcp-realm/broker/google/endpoint
   
8. 記錄 **用戶端 ID** 和 **用戶端密碼** (可以下載json，Client Secret無法再次查看)

### 6.2 在 Keycloak 中設定 Google IDP

1. 回到 Keycloak，左側選單選擇 **Identity providers**
2. 點選 **Add provider** → **Google**
3. 填入設定：
   - **Alias**: `google`
   - **Display name**: `Google`
   - **Enabled**: `ON`
   - **Store tokens**: `ON`
   - **Stored tokens readable**: `ON`
   - **Trust email**: `ON`
   - **Account linking only**: `OFF`
   - **Hide on login page**: `OFF`
   - **First broker login flow**: 選擇先前建立的 `First broker login - No Review`
   - **Client ID**: 輸入 Google OAuth 用戶端 ID
   - **Client Secret**: 輸入 Google OAuth 用戶端密碼
   - **Default scopes**: `openid profile email`
   - **Authorization URL**: `https://accounts.google.com/o/oauth2/v2/auth`
   - **Token URL**: `https://oauth2.googleapis.com/token`
   - **User Info URL**: `https://openidconnect.googleapis.com/v1/userinfo`
   - **Issuer**: `https://accounts.google.com`
   - **Validate Signature**: `ON`
   - **JWKS URL**: `https://www.googleapis.com/oauth2/v3/certs`


4. 點選 **Save**

## 7. 設定 Mapper 取得使用者欄位

1. 在 Google Identity Provider 設定頁面
2. 點選 **Mappers** 頁籤
3. 點選 **Add mapper**

### 預設需要的 Mappers：

#### Email Mapper
- **Name**: `email`
- **Mapper type**: `Attribute Importer`
- **Claim**: `email`
- **User attribute**: `email`

#### First Name Mapper  
- **Name**: `first_name`
- **Mapper type**: `Attribute Importer`
- **Claim**: `given_name`
- **User attribute**: `firstName`

#### Last Name Mapper
- **Name**: `last_name`
- **Mapper type**: `Attribute Importer`
- **Claim**: `family_name`
- **User attribute**: `lastName`

#### Username Mapper
- **Name**: `username`
- **Mapper type**: `Username Template Importer`
- **Template**: `${CLAIM.email}`

每個 mapper 建立後都要點選 **Save**。

## 8. Authentication Flow Browser 設定 (可選)

### 設定 Google 作為預設 IDP Redirector

1. 左側選單選擇 **Authentication**
2. 點選 **Flows** 頁籤
3. 選擇 **Browser** flow
4. 點選 **Duplicate** 建立副本
5. 重新命名為 `Browser - Google Redirect`
6. 在複製的 flow 中：
   - 找到 **Identity Provider Redirector** 
   - 點選右側的 **⚙️** (Configure)
   - **Alias**: `google`
   - **Default Identity Provider**: `google`
   - 點選 **Save**

7. 回到 **Realm Settings** → **Login** 頁籤
8. 將 **Browser Flow** 改為 `Browser - Google Redirect`

## 9. 測試 OpenID Configuration

使用瀏覽器或 curl 測試以下端點：


# 測試 Keycloak OIDC 設定
curl -s "https://{YOUR-HOST}/realms/mcp-realm/.well-known/openid-configuration" | jq .

# 測試應用程式的 well-known 端點
curl -s "https://{YOUR-HOST}/.well-known/oauth-protected-resource" | jq .
curl -s "https://{YOUR-HOST}/.well-known/openid-configuration" | jq .

確認回應包含正確的 `issuer`、`authorization_endpoint`、`token_endpoint` 等資訊。

## 10. Spring Boot 應用程式設定

在 `application.yml`  中加入：

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://{YOUR-HOST}/realms/mcp-realm
          jwk-set-uri: https://{YOUR-HOST}/realms/mcp-realm/protocol/openid-connect/certs

app:
  oauth-uri: https://{YOUR-HOST}/realms/mcp-realm

logging:
  level:
    org.springframework.security: DEBUG
    org.springframework.web: DEBUG
```



## 11. 其他重要設定

### 11.1 Token 有效期限設定

1. **Realm Settings** → **Tokens** 頁籤
2. 調整以下設定：
   - **Access Token Lifespan**: `300` seconds (5 分鐘)
   - **Refresh Token Max Reuse**: `0`
   - **SSO Session Idle**: `1800` seconds (30 分鐘)
   - **SSO Session Max**: `36000` seconds (10 小時)

### 11.2 安全性設定

1. **Realm Settings** → **Security Defenses** 頁籤
2. 確認以下設定：
   - **Brute Force Detection**: `ON`
   - **X-Frame-Options**: `DENY`
   - **Content Security Policy**: `frame-src 'self'; frame-ancestors 'self'; object-src 'none';`

### 11.3 Email 設定 (可選)

如需發送驗證郵件：

1. **Realm Settings** → **Email** 頁籤
2. 填入 SMTP 設定：
   - **Host**: SMTP 伺服器地址
   - **Port**: SMTP 埠號
   - **From**: 寄件者郵件地址
   - **Enable StartTLS**: 根據需求設定
   - **Enable Authentication**: 如需認證請開啟
   - **Username/Password**: SMTP 認證資訊

## 12. 測試整體流程

1. 清除瀏覽器 cookies
2. 前往您的應用程式
3. 嘗試訪問需要認證的端點
4. 確認自動重導向到 Google 登入
5. 完成 Google 認證後檢查：
   - 用戶是否正確建立在 Keycloak
   - JWT token 是否包含正確的用戶資訊
   - 應用程式是否能正確驗證 token

## 故障排除

### 常見問題：

1. **重導向 URI 不符**：檢查 Google Cloud Console 和 Keycloak Client 設定
2. **CORS 錯誤**：確認 Web origins 設定正確
3. **Token 驗證失敗**：檢查 `issuer-uri` 和 `jwk-set-uri` 是否正確
4. **用戶屬性缺失**：檢查 Identity Provider Mappers 設定

### 除錯建議：

- 啟用 Spring Security DEBUG 日誌
- 檢查 Keycloak Admin Console 的 Events 頁面
- 使用瀏覽器開發者工具檢查網路請求
- 驗證 JWT token 內容：使用 [jwt.io](https://jwt.io) 解析 token

