package io.github.jasper.monitoring.audit.spring3.report;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 报告查询 HTTP 适配器。
 *
 * <p>Controller 只负责接收资源路径、顺序访问标记和验收会话头，并委托
 * {@link ReportQueryService} 执行前置控制检查和 Query Action 埋点。它不负责身份认证、资源授权、
 * 规则评估或直接访问监测表；URL 和响应格式只属于验收宿主。</p>
 */
@RestController
@RequestMapping("/audit/queries")
public class ReportQueryController {
    private final ReportQueryService queries;
    public ReportQueryController(ReportQueryService queries) {
        this.queries = queries;
    }
    /**
     * 执行一次报告查询。
     *
     * <p>请求先由 Service 检查已生效的会话、拒绝和限流控制，通过后提交 Query Action。新命中的
     * 控制通常影响后续请求；本方法不是资源授权入口，也不直接从请求头构造生产身份。</p>
     *
     * @param resourceId 报告资源标识；生产系统应使用已授权的服务端资源对象
     * @param sequential 是否存在顺序遍历特征
     * @param sessionId 验收会话标识
     * @return 查询状态和对应 HTTP 状态
     */
    @GetMapping("/{resourceId}")
    public ResponseEntity<Map<String,Object>> query(@PathVariable("resourceId") String resourceId,
        @RequestParam(value="sequential",defaultValue="false") boolean sequential,
        @RequestHeader(value="X-Audit-Session",required=false) String sessionId){
        HttpStatus status = queries.query(resourceId, sequential, sessionId);
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("status", status.name());
        return ResponseEntity.status(status).body(body);
    }
}
