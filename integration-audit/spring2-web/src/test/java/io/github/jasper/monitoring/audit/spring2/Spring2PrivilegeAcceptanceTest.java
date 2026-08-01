package io.github.jasper.monitoring.audit.spring2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.audit.spring2.privilege.PrivilegeGrantRepository;
import io.github.jasper.monitoring.core.domain.SecurityAlert;
import io.github.jasper.monitoring.core.domain.SecurityEvent;
import io.github.jasper.monitoring.mybatis.repository.MyBatisMonitoringStore;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Boot 2 的权限提升防护验收测试。
 *
 * <p>验证管理员为本人增加高权限角色时，拒绝与告警控制均发生在角色关系事务提交之前。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Spring2PrivilegeAcceptanceTest {
    private static final String ACTOR = "audit-admin";
    private static final String ROLE = "tc10-superuser";

    @LocalServerPort
    private int port;
    @Autowired
    private TestRestTemplate http;
    @Autowired
    private PrivilegeGrantRepository roles;
    @Autowired
    private MyBatisMonitoringStore monitoring;

    @Test
    @DisplayName("TC-10 self privilege escalation is rejected before the role transaction commits")
    void tc10_selfPrivilegeEscalationIsRejectedBeforeRoleCommit() {
        assertFalse(roles.findRoles(ACTOR).contains(ROLE));

        ResponseEntity<Void> response = http.postForEntity(url(ACTOR, ROLE),
            new HttpEntity<Void>(headers(ACTOR)), Void.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertFalse(roles.findRoles(ACTOR).contains(ROLE));
        SecurityEvent event = monitoring.findSince("audit-spring2-web", Instant.EPOCH).stream()
            .filter(candidate -> "privilege:change".equals(candidate.getAction()))
            .filter(candidate -> ACTOR.equals(candidate.getUserId()))
            .filter(candidate -> ACTOR.equals(candidate.getAttribute("target_user_id")))
            .reduce((first, second) -> second)
            .orElseThrow(() -> new AssertionError("Missing typed privilege change event"));
        assertEquals("true", event.getAttribute("privilege_increase"));
        assertEquals("MON.PRIVILEGE.SELF_ESCALATION", event.getReasonCode());
        SecurityAlert alert = monitoring.findOpen("PRIV-01|" + ACTOR + "|monitoring:")
            .orElseThrow(() -> new AssertionError("Missing PRIV-01 alert"));
        assertTrue(monitoring.findControl(alert.getAlertId() + ":" + ControlActionType.DENY).isPresent());
    }

    private String url(String targetUserId, String roleId) {
        return "http://localhost:" + port + "/audit/privileges/" + targetUserId + "/roles/" + roleId;
    }

    private static HttpHeaders headers(String principal) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Audit-Principal", principal);
        return headers;
    }
}
