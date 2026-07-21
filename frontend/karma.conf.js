// Configuración de Karma con umbrales de cobertura de código.
//
// El `check` solo se aplica cuando la cobertura está activa (ejecuciones con
// `--code-coverage`, como en CI). Los umbrales son un suelo por debajo de la
// cobertura actual para impedir regresiones; conviene subirlos gradualmente a
// medida que crece la cobertura (equivalente al gate JaCoCo del backend).
module.exports = function (config) {
  config.set({
    basePath: '',
    frameworks: ['jasmine', '@angular-devkit/build-angular'],
    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-jasmine-html-reporter'),
      require('karma-coverage'),
      require('@angular-devkit/build-angular/plugins/karma')
    ],
    client: {
      jasmine: {},
      clearContext: false
    },
    jasmineHtmlReporter: {
      suppressAll: true
    },
    coverageReporter: {
      dir: require('path').join(__dirname, './coverage/frontend'),
      subdir: '.',
      reporters: [{ type: 'html' }, { type: 'text-summary' }, { type: 'lcovonly' }],
      check: {
        global: {
          statements: 65,
          branches: 58,
          functions: 55,
          lines: 65
        }
      }
    },
    reporters: ['progress', 'kjhtml'],
    browsers: ['Chrome'],
    restartOnFileChange: true
  });
};
