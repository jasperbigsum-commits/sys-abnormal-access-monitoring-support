/**
 * 认证监测的核心应用实现。
 *
 * <p>本包负责 opaque 登录主体派生、认证控制预检以及登录结果事件编排。实现保持原始登录名
 * 瞬时化，并通过版本化 HMAC 主体关联规则、告警和控制。</p>
 */
package io.github.jasper.monitoring.core.application.authentication;
