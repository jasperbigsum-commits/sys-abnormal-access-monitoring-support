package io.github.jasper.monitoring.api.management;

import java.util.Objects;

/**
 * 管理服务实现的通用授权守卫。
 *
 * <p>管理服务应配合构造器注入的可信授权器使用该守卫。Controller 适配器不应自行选择或传入授权器实现。</p>
 */
public final class ManagementServiceSupport {
    private ManagementServiceSupport() {
    }

    /**
     * 在操作者作用域内对单个资源执行授权校验。
     *
     * @return 校验通过后的原操作者对象
     */
    public static ManagementActor authorize(ManagementAuthorizer authorizer,
                                            ManagementActor actor,
                                            ManagementOperation operation,
                                            String resourceType,
                                            String resourceId) {
        Objects.requireNonNull(authorizer, "authorizer");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(resourceType, "resourceType");
        Objects.requireNonNull(resourceId, "resourceId");
        if (resourceType.trim().isEmpty()) {
            throw new IllegalArgumentException("resourceType must not be blank");
        }
        if (resourceId.trim().isEmpty()) {
            throw new IllegalArgumentException("resourceId must not be blank");
        }
        authorizer.require(actor, operation,
            ManagementResource.of(resourceType, resourceId, actor.getSystemScope()));
        return actor;
    }
}
