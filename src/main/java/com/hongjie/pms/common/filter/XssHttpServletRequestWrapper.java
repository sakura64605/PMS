package com.hongjie.pms.common.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hongjie.pms.common.utils.XssUtils;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.util.StreamUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * XSS 过滤请求包装器
 * 重写参数获取方法，自动过滤 XSS 内容
 */
@Deprecated
public class XssHttpServletRequestWrapper extends HttpServletRequestWrapper {

    private byte[] body;

    public XssHttpServletRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);

        String contentType = request.getContentType();

        // 处理 JSON 请求
        if (contentType != null && contentType.contains("application/json")) {
            byte[] originalBody = StreamUtils.copyToByteArray(request.getInputStream());
            String originalContent = new String(originalBody, StandardCharsets.UTF_8);
            String escapedContent = escapeJsonValues(originalContent);
            this.body = escapedContent.getBytes(StandardCharsets.UTF_8);
        } else {
            // 非 JSON 请求，原样处理
            this.body = StreamUtils.copyToByteArray(request.getInputStream());
        }
    }

    /**
     * 只转义 JSON 中的字符串值，不破坏 JSON 格式
     */
    private String escapeJsonValues(String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);
            escapeNode(root);
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            // 解析失败，返回原值
            return json;
        }
    }

    /**
     * 递归转义 JSON 节点中的字符串值
     */
    private void escapeNode(JsonNode node) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (field.getValue().isTextual()) {
                    String original = field.getValue().asText();
                    ((ObjectNode) node).put(field.getKey(), XssUtils.clean(original));
                } else {
                    escapeNode(field.getValue());
                }
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                escapeNode(item);
            }
        }
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override
            public int read() throws IOException {
                return byteArrayInputStream.read();
            }

            @Override
            public boolean isFinished() {
                return byteArrayInputStream.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener listener) {
            }
        };
    }

    @Override
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(getInputStream()));
    }

    @Override
    public String getParameter(String name) {
        String value = super.getParameter(name);
        if (value == null) {
            return null;
        }
        return XssUtils.clean(value);
    }

    @Override
    public String[] getParameterValues(String name) {
        String[] values = super.getParameterValues(name);
        if (values == null) {
            return null;
        }
        String[] cleanedValues = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            cleanedValues[i] = XssUtils.clean(values[i]);
        }
        return cleanedValues;
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        Map<String, String[]> originalMap = super.getParameterMap();
        Map<String, String[]> cleanedMap = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> entry : originalMap.entrySet()) {
            String[] cleanedValues = new String[entry.getValue().length];
            for (int i = 0; i < entry.getValue().length; i++) {
                cleanedValues[i] = XssUtils.clean(entry.getValue()[i]);
            }
            cleanedMap.put(entry.getKey(), cleanedValues);
        }
        return cleanedMap;
    }

    @Override
    public String getHeader(String name) {
        String value = super.getHeader(name);
        if (value == null) {
            return null;
        }
        return XssUtils.clean(value);
    }
}