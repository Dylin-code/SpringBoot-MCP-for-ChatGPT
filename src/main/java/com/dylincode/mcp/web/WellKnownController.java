package com.dylincode.mcp.web;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Controller that provides several endpoints under the `.well-known` path to expose
 * specific configurations and metadata related to OAuth and OpenID Connect.
 * This class enables integration and interaction with supported authorization servers
 * and protected resources.
 */
@Slf4j
@RestController
@RequestMapping("/.well-known")
public class WellKnownController {

    @Value("${app.oauth-uri}")
    private String issuer;
    @Value("${app.resource-uri}")
    private String resource;

    @GetMapping(value = "/oauth-protected-resource", produces = "application/json")
    public Map<String, Object> resource() {
        log.info("call well-known/oauth-protected-resource");
        return Map.of(
                // 你的 MCP Resource 識別（可放 MCP base URL）
                "resource", resource,
                // 告訴客戶端：去 Google 當授權伺服器拿 token
                "authorization_servers", List.of(issuer),
                // 可選，給客戶端提示
                "scopes_supported", List.of("openid", "email", "profile")
        );
    }

    private final RestClient http = RestClient.create();

    // 讓連接器若打在你這，也能拿到 AS metadata（大多 IdP 用 OIDC 這份）
    @GetMapping(value = "/openid-configuration", produces = "application/json")
    public ResponseEntity<byte[]> oidc(HttpServletRequest request) {
        log.info("call well-known/openid-configuration from {}", getBaseUrl(request));
        byte[] body = http.get()
                .uri(issuer + "/.well-known/openid-configuration")
                .retrieve().body(byte[].class);
        return ResponseEntity.ok().body(body);
    }

    // 有些實作會打這條；Google 沒提供同名路徑，直接回傳等價資訊（轉發 OIDC）
    @GetMapping(value = "/oauth-authorization-server", produces = "application/json")
    public ResponseEntity<byte[]> oas(HttpServletRequest request) {
        log.info("call well-known/oauth-authorization-server from {}", getBaseUrl(request));
        byte[] body = http.get()
                .uri( issuer + "/.well-known/openid-configuration")
                .retrieve().body(byte[].class);
        return ResponseEntity.ok().body(body);
    }

    private String getBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();
        String contextPath = request.getContextPath();

        return scheme + "://" + serverName +
                (serverPort != 80 && serverPort != 443 ? ":" + serverPort : "") +
                contextPath;
    }

}

