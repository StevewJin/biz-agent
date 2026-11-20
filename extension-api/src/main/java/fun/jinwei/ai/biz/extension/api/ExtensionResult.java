package fun.jinwei.ai.biz.extension.api;

import java.util.Map;

/**
 * 扩展包工具统一返回结果。
 *
 * <p>content 面向 LLM（纯文本）；render 面向前端渲染器（声明式 UI payload）。</p>
 */
public class ExtensionResult {

    private boolean success = true;
    private String content;
    private String error;
    private String renderType;
    private Map<String, Object> renderProps;
    private Map<String, Object> attributes;

    public static ExtensionResult ok(String content) {
        ExtensionResult r = new ExtensionResult();
        r.success = true;
        r.content = content;
        return r;
    }

    public static ExtensionResult ok(String content, String renderType, Map<String, Object> renderProps) {
        ExtensionResult r = ok(content);
        r.renderType = renderType;
        r.renderProps = renderProps;
        return r;
    }

    public static ExtensionResult fail(String error) {
        ExtensionResult r = new ExtensionResult();
        r.success = false;
        r.error = error;
        r.content = "Error: " + error;
        return r;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getRenderType() {
        return renderType;
    }

    public void setRenderType(String renderType) {
        this.renderType = renderType;
    }

    public Map<String, Object> getRenderProps() {
        return renderProps;
    }

    public void setRenderProps(Map<String, Object> renderProps) {
        this.renderProps = renderProps;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }
}
