# 异常访问监测管理端

面向银行科技风险运营的 Vite + Vue 3 管理端，页面通过统一的 `MonitoringRepository` 同时支持确定性 Mock 数据和 HTTP 联调数据。表格、表单、分页、抽屉、弹窗和字典标签均从 `src/components/jeecg` 的 Jeecg 兼容层调用。

## 本地运行

要求 Node.js 20 或更高版本。

```powershell
npm install
npm run dev
```

默认使用 Mock 数据。开发页右下角的场景按钮可切换默认、空数据、慢响应、无权限、服务不可用、版本冲突和未定义错误；该控件不会出现在生产构建中。

## HTTP 联调

创建 `.env.local` 并按实际宿主修改地址：

```dotenv
VITE_DATA_MODE=http
VITE_API_BASE_URL=http://127.0.0.1:8080/audit/management
```

HTTP 实现位于 `src/repositories/httpMonitoringRepository.ts`，统一使用 `src/api/http/axios.ts` 的 Jeecg 风格 `defHttp`。响应约定如下：

```json
{"success":true,"code":200,"message":"操作成功","result":{},"timestamp":0}
```

列表接口使用后端零基页码 `page/size`，适配器会转换为前端一基页码。已知错误统一映射为 `ManagementError`；未定义的 `errorType`、`errorCode`、`requestId` 和 `details` 会原样保留。

开发模式下的联调夹具可额外配置 `VITE_AUDIT_PRINCIPAL` 和 `VITE_AUDIT_APPROVER` 请求头，生产构建会忽略这两个变量。生产宿主必须从已认证的服务端会话派生操作者和独立审批人，不能信任浏览器自报身份。规则变更请求体不会发送审批人身份。

## 验证

```powershell
npm run test:run
npm run build
```

生产构建输出到 `dist/`。当前页面包括风险态势、告警中心、事件审计、控制中心、检测策略、白名单和管理审计。
