const path = require('path');

module.exports = {
  entry: [
    './resources/js/index.js'
  ],
  output: {
    path: path.resolve(__dirname, 'resources/public', 'js'),
    filename: "libs.js"
  },  
};
