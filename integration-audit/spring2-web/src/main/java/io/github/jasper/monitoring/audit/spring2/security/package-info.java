/**
 * Boot 2 宿主的 Shiro 身份、会话与资源范围授权夹具。
 *
 * <p>该包从已认证 Shiro Subject 派生监测身份，并在真实资源决策点执行授权。固定请求头和账号
 * 仅用于验收模拟；生产系统必须接入自身可信认证、会话和组织范围数据源。</p>
 */
package io.github.jasper.monitoring.audit.spring2.security;
