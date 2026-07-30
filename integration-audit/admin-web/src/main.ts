import { createApp } from 'vue';
import { createPinia } from 'pinia';
import 'ant-design-vue/dist/reset.css';
import App from './App.vue';
import { createMockMonitoringRepository } from './mocks/mockMonitoringRepository';
import { optionsForPreset, readMockPreset } from './mocks/presets';
import { createHttpMonitoringRepository } from './repositories/httpMonitoringRepository';
import { monitoringRepositoryKey, type MonitoringRepository } from './repositories/monitoringRepository';
import { router } from './router';
import './styles/theme.css';
import './styles/components.css';

const app = createApp(App);
const dataMode = import.meta.env.VITE_DATA_MODE === 'http' ? 'http' : 'mock';
const mockPreset = readMockPreset();
let repository: MonitoringRepository;
if (dataMode === 'http') {
    repository = createHttpMonitoringRepository();
} else {
    const mockRepository = createMockMonitoringRepository(optionsForPreset(mockPreset));
    if (mockPreset === 'empty') mockRepository.clear();
    repository = mockRepository;
}
app.provide(monitoringRepositoryKey, repository);

app
    .use(createPinia())
    .use(router)
    .mount('#app');
