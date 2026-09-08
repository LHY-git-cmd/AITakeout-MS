module.exports = {
  preset: '@vue/cli-plugin-unit-jest/presets/typescript',
  collectCoverage: true,
  collectCoverageFrom: [
    'src/utils/**/*.{ts,vue}',
    '!src/utils/auth.ts',
    '!src/utils/request.ts',
    'src/components/**/*.{ts,vue}'
  ],
  coverageDirectory: '<rootDir>/tests/unit/coverage',
  coverageReporters: ['lcov', 'text-summary'],
  testEnvironmentOptions: {
    url: 'http://localhost/'
  }
}
