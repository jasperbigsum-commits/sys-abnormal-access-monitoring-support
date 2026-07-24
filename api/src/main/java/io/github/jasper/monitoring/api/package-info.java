/**
 * 异常访问监测所需的框架无关契约、标准化事件模型和宿主系统扩展点。
 *
 * <p>应用负责提供可信身份、授权、代理链解析、事件补充和控制执行等集成；本包刻意不依赖
 * 网络框架（Web）或持久化实现，以便在不同系统中复用。调用方可通过
 * {@link io.github.jasper.monitoring.api.error.MonitoringFailure} 读取稳定错误码，并在自身的
 * HTTP、RPC 或消息边界完成协议映射。</p>
 */
package io.github.jasper.monitoring.api;
