/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./src/**/*.{html,ts}'],
  theme: {
    extend: {
      colors: {
        papel: '#f7f5f0',
        institucional: '#1c2430',
        destaque: '#78350f',
      },
      fontFamily: {
        serif: ['Georgia', 'Cambria', "'Times New Roman'", 'serif'],
      },
    },
  },
  plugins: [],
};
