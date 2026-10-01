const path = require('path')
const IS_PROD = ['production', 'development'].includes(process.env.NODE_ENV)

module.exports = {
  // 单进程构建可避免 Windows 低虚拟内存环境下 thread-loader 创建子进程失败。
  parallel: false,
  'publicPath': process.env.NODE_ENV === 'production' ? './' : '/', // TODO: Remember to change this to fit your need
  'lintOnSave': process.env.NODE_ENV === 'development',
  'pluginOptions': {
    'style-resources-loader': {
      'preProcessor': 'scss',
      'patterns': [
        path.resolve(__dirname, 'src/styles/_variables.scss'),
        path.resolve(__dirname, 'src/styles/_mixins.scss')
      ]
    }
  },
  // 开启代理
  devServer: {
    host:'0.0.0.0',
    port: 8888,
    open: true,
    allowedHosts: 'all',
    hot:true,//自动保存
    client: {
      overlay: {
        warnings: false,
        errors: true
      }
    },
    proxy: {
      '/api': {
        target: process.env.VUE_APP_URL,
        ws: false,
        secure: false,
        changeOrigin: true,
        pathRewrite:{
          '^/api':''
        }
      }
    }
  },
  chainWebpack: (config) => {
    config.resolve.symlinks(true) // 修复热更新失效
  },
  configureWebpack: {
    devtool: 'source-map'
  },

  css: {
    // 是否使用css分离插件 ExtractTextPlugin
    extract: IS_PROD,
    // 开启 CSS source maps?
    sourceMap: false,
    // css预设器配置项
    loaderOptions: {
    },
},
};
