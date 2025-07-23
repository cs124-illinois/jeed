// @ts-check

import { FlatCompat } from "@eslint/eslintrc"

const compat = new FlatCompat()

export default [...compat.extends("next/core-web-vitals")]
