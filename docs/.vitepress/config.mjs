import { defineConfig } from 'vitepress'

export default defineConfig({
  lang: 'zh-CN',
  title: '后端工程化开发',
  description: 'Spring Boot 后端工程化多模块学习仓库',
  base: '/backend-learning/',
  cleanUrls: true,
  themeConfig: {
    nav: [
      { text: 'GitHub 仓库', link: 'https://github.com/mqxu/backend-learning' }
    ],
    sidebar: [
      { text: '课程导读', link: '/' },
      {
        text: '模块教程',
        items: [
          { text: '01 快速入门', link: '/01-quickstart' },
          { text: '02 配置管理', link: '/02-config' },
          { text: '03 日志管理', link: '/03-logging' },
          { text: '04 Web 开发进阶', link: '/04-web' },
          { text: '05 数据访问', link: '/05-mybatis' },
          { text: '06 缓存与分布式锁', link: '/06-redis' },
          { text: '07 消息队列', link: '/07-mq' },
          { text: '08 定时任务', link: '/08-schedule' },
          { text: '09 认证与授权', link: '/09-security' },
          { text: '10 文件上传下载', link: '/10-file' },
          { text: '11 接口文档', link: '/11-doc' },
          { text: '12 测试', link: '/12-test' },
          { text: '13 应用监控', link: '/13-actuator' },
          { text: '14 部署与 CI/CD', link: '/14-deploy' }
        ]
      }
    ],
    outline: { label: '本页目录', level: [2, 3] },
    docFooter: { prev: '上一节', next: '下一节' }
  }
})
