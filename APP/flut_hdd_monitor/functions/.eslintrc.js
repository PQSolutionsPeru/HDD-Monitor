module.exports = {
  env: {
    es6: true,
    node: true
  },
  parserOptions: {
    "ecmaVersion": 2018
  },
  extends: [
    "eslint:recommended",
    "google"
  ],
  rules: {
    "max-len": ["error", 120], // Aumenta el límite de longitud de línea a 120
    "quotes": ["error", "double", {"avoidEscape": true, "allowTemplateLiterals": true}],
    "comma-dangle": ["error", "never"],
    "indent": ["error", 2]// Establece la indentación a 2 espacios
  },
  overrides: [
    {
      files: ["**/*.spec.*"],
      env: {
        mocha: true
      },
      rules: {}
    }
  ],
  globals: {}
};
