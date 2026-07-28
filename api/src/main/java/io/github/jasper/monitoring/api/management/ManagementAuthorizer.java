package io.github.jasper.monitoring.api.management;

import java.util.Objects;

/** 管理数据读写前统一调用的授权边界。 */
public interface ManagementAuthorizer {
    /** 执行授权判定；拒绝时由实现抛出相应异常。 */
    void authorize(ManagementActor actor, ManagementOperation operation, ManagementResource resource);

    /** 校验参数并执行授权判定。 */
    default void require(ManagementActor actor, ManagementOperation operation, ManagementResource resource) {
        requireArguments(actor, operation, resource);
        authorize(actor, operation, resource);
    }

    /** 统一参数非空校验。 */
    static void requireArguments(ManagementActor actor, ManagementOperation operation, ManagementResource resource) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(resource, "resource");
    }
}
