package com.dylincode.mcp.embedding;

/**
 * 提供文本嵌入向量化功能的服務接口。
 *
 * <p>嵌入服務將文本輸入轉換為向量表示，通常以浮點數陣列的形式呈現。
 * 這種技術在機器學習、自然語言處理、資訊檢索和語意搜尋等應用中非常常見。</p>
 *
 * <p>實現此接口的服務通常會：
 * <ul>
 *   <li>將輸入文本進行預處理</li>
 *   <li>通過預訓練的語言模型生成語意向量</li>
 *   <li>返回可用於相似性計算的標準化向量</li>
 * </ul>
 * </p>
 *
 * <p><strong>使用場景：</strong></p>
 * <ul>
 *   <li>語意搜尋 - 找到與查詢語意相近的文檔</li>
 *   <li>文檔聚類 - 將相似內容的文檔分組</li>
 *   <li>推薦系統 - 基於內容相似性推薦項目</li>
 *   <li>異常檢測 - 識別與正常模式差異較大的文本</li>
 * </ul>

 */
public interface EmbeddingService {
    float[] embed(String text) throws Exception;
}
