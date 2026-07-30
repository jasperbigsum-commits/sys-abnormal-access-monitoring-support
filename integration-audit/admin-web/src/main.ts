import { createApp } from 'vue';
import { createPinia } from 'pinia';
import 'ant-design-vue/dist/reset.css';
import App from './App.vue';
import { createMockMonitoringRepository } from './mocks/mockMonitoringRepository';
import { monitoringRepositoryKey } from './repositories/monitoringRepository';
import { router } from './router';
import './styles/theme.css';
import './styles/components.css';

const app = createApp(App);
app.provide(monitoringRepositoryKey, createMockMonitoringRepository());

app
    .use(createPinia())
    .use(router)
    .mount('#app');
