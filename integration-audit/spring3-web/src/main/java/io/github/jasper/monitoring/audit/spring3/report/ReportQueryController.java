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

/** HTTP adapter for stateful traversal and frequency scenarios. */
@RestController
@RequestMapping("/audit/queries")
public class ReportQueryController {
    private final ReportQueryService queries;
    public ReportQueryController(ReportQueryService queries){this.queries=queries;}
    @GetMapping("/{resourceId}")
    public ResponseEntity<Map<String,Object>> query(@PathVariable("resourceId") String resourceId,
        @RequestParam(value="sequential",defaultValue="false") boolean sequential,
        @RequestHeader(value="X-Audit-Session",required=false) String sessionId){
        HttpStatus status=queries.query(resourceId,sequential,sessionId);
        Map<String,Object> body=new LinkedHashMap<String,Object>(); body.put("status",status.name());
        return ResponseEntity.status(status).body(body);
    }
}
