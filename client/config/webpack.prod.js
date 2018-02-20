const merge = require('webpack-merge')
const UglifyJSPlugin = require('uglifyjs-webpack-plugin')
const common = require('./webpack.common.js')
const paths = require('../config/paths');
const HtmlWebpackPlugin = require('html-webpack-plugin')
const CopyWebpackPlugin = require('copy-webpack-plugin');
const InterpolateHtmlPlugin = require('react-dev-utils/InterpolateHtmlPlugin')

module.exports = merge(common, {
  bail: true,
  devtool: 'source-map',
  output: {
    publicPath: '/assets/client/',
  },
  plugins: [
    new InterpolateHtmlPlugin({ PUBLIC_URL: '/assets/client/' }),
    new HtmlWebpackPlugin({
      inject: true,
      template: paths.appHtml,
      filename: paths.appBuild + '/../../views/index.ftl',
      minify: {
          removeComments: true,
          collapseWhitespace: true,
          removeRedundantAttributes: true,
          useShortDoctype: true,
          removeEmptyAttributes: true,
          removeStyleLinkTypeAttributes: true,
          keepClosingSlash: true,
          minifyJS: true,
          minifyCSS: true,
          minifyURLs: true
      }
  }),
    new CopyWebpackPlugin([
      { from: 'public', ignore : ['index.html','alloy-editor/**/*'] },
      { from: 'node_modules/alloyeditor/dist/alloy-editor', to: 'alloy-editor'}
  ]),
    new UglifyJSPlugin({
      parallel: true,
      cache: true,
      sourceMap: true
    }),
]})