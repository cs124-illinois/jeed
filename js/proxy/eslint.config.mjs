// @ts-check

import eslint from "@eslint/js"
import eslintConfigPrettier from "eslint-config-prettier"
import tseslint from "typescript-eslint"

export default [
  ...tseslint.config(eslint.configs.recommended, ...tseslint.configs.recommended, {
    rules: {
      "@typescript-eslint/no-namespace": "off",
      "no-empty": "off",
      "@typescript-eslint/no-unused-vars": ["error", { caughtErrors: "none" }],
      "@typescript-eslint/no-unused-expressions": [2, { allowShortCircuit: true, allowTernary: true }],
    },
  }),
  eslintConfigPrettier,
]
