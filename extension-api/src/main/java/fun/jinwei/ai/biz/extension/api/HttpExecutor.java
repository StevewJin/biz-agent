package fun.jinwei.ai.biz.extension.api;

import java.util.Map;

/**
 * 业务系统接口调用器。平台负责域名白名单（仅限已登记接口）、鉴权注入、超时与响应截断。
 */
public interface HttpExecutor {

    /**
     * 调用已登记的接口。
     *
     * @param endpointCode 接口编码（必须已登记）
     * @param params       路径/查询/请求体参数
     * @return 响应体文本（超长会被截断）
     */
    String call(String endpointCode, Map<String, Object> params);
}
