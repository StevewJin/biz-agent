import colors from 'tailwindcss/colors'

/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      // 品牌色统一：历史聊天页使用 purple-*，统一映射到 indigo 调色板，
      // 管理后台使用中性色 + indigo 强调，两处视觉保持一致。
      colors: {
        purple: colors.indigo,
      },
    },
  },
  plugins: [
    require('@tailwindcss/typography'),
  ],
}
