package com.dylincode.mcp.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * MCP (Model Context Protocol) 工具接口定義。
 *
 * <p>此接口定義了 MCP 協議中工具的基本規範，用於在 tool/list 請求中
 * 展示系統具備的各種能力和功能。每個工具代表系統能夠執行的一個特定操作，
 * 例如搜索、計算、數據處理等。</p>
 *
 * <p><strong>MCP 工具系統的核心概念：</strong></p>
 * <ul>
 *   <li><strong>能力展示</strong> - 通過 tool/list 向客戶端展示可用工具</li>
 *   <li><strong>參數規範</strong> - 使用 JSON Schema 定義工具的輸入參數</li>
 *   <li><strong>動態調用</strong> - 支持基於 JSON 參數的動態工具執行</li>
 *   <li><strong>結果返回</strong> - 將執行結果以結構化方式返回給調用方</li>
 * </ul>
 *
 * <p><strong>實現指南：</strong></p>
 * <ul>
 *   <li>工具名稱應該具有描述性，遵循命名約定</li>
 *   <li>JSON Schema 應該完整描述所有必需和可選參數</li>
 *   <li>invoke 方法應該進行適當的參數驗證和錯誤處理</li>
 *   <li>返回結果應該是可序列化的 JSON 兼容對象</li>
 * </ul>
 *
 * @see <a href="https://spec.modelcontextprotocol.io/">MCP 協議規範</a>
 */

interface Tool {
    String name();
    Map<String, Object> schema();
    Object invoke(JsonNode arguments, ObjectMapper mapper) throws Exception;
}
