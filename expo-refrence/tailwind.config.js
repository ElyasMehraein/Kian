module.exports = {
  content: ['./app/**/*.{ts,tsx}', './components/**/*.{ts,tsx}'],
  presets: [require('nativewind/preset')],
  theme: {
    extend: {
      colors: {
        canvas: '#ffffff',
        ink: '#111827',
        muted: '#64748b',
        line: '#e5e7eb',
        panel: '#f8fafc',
        accent: '#2563eb',
        accentSoft: '#dbeafe',
        danger: '#b91c1c',
      },
    },
  },
  plugins: [],
};
