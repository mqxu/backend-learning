import { createApp } from 'vue';
import ElementPlus from 'element-plus';
// @ts-ignore
import 'element-plus/dist/index.css';
import App from './App.vue';

// 引入路由
import router from './router'

// 图标在各自页面里按需引入，这里不再全量注册
const app = createApp(App);

app.use(ElementPlus);

// 挂载路由
app.use(router);
app.mount("#app");
