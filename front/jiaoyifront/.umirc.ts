import { defineConfig } from '@umijs/max';

export default defineConfig({
  antd: {},
  access: {},
  model: {},
  initialState: {},
  request: {},

  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    },
    '/uploads': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    },
    '/ws/chat': {
      target: 'ws://localhost:8080',
      ws: true,
      changeOrigin: true,
    },
  },

  layout: {
    title: '校园交易行',
  },

  routes: [
    {
      path: '/',
      redirect: '/login',
    },
    {
      path: '/login',
      component: './Login',
      layout: false,
    },
    {
      path: '/home',
      component: './Home',
      name: '首页',
    },
    {
      path: '/publish',
      component: './Publish',
      name: '发布宝贝',
      menu: false,
    },
    {
      path: '/messages',
      component: './Messages',
      name: '消息',
    },
    {
      path: '/categories',
      component: './Categories',
      name: '市场',
      menu: false,
    },
    {
      path: '/search',
      component: './Search',
      name: '搜索结果',
      menu: false,
    },
    {
      path: '/product/:id',
      component: './ProductDetail',
      menu: false,
    },
    {
      path: '/service/:id',
      component: './ServiceDetail',
      menu: false,
    },
    {
      path: '/my-publish',
      component: './MyPublish',
      name: '我的发布',
    },
    {
      path: '/orders',
      component: './MyOrders',
      name: '我的订单',
    },
    {
      path: '/profile',
      component: './Profile',
      name: '个人中心',
      menu: false,
    },
  ],

  npmClient: 'pnpm',
});
