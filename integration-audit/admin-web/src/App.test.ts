import { mount } from '@vue/test-utils';
import App from './App.vue';

it('renders the monitoring application root', () => {
    expect(mount(App).find('[data-testid="app-root"]').exists()).toBe(true);
});
