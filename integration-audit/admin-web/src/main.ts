import { createApp } from 'vue';
import { createPinia } from 'pinia';
import 'ant-design-vue/dist/reset.css';
import App from './App.vue';
import router from './router';
import './styles/theme.css';

createApp(App)
    .use(createPinia())
    .use(router)
    .mount('#app');
