/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,jsx}"],
  theme: {
    extend: {
      colors: {
        brand: {
          50: "#f4f8ff",
          100: "#dfe9ff",
          200: "#c0d4ff",
          300: "#8fb3ff",
          400: "#5b8af8",
          500: "#3567ef",
          600: "#234ed4",
          700: "#1d3fac",
          800: "#1f3888",
          900: "#1f326d"
        }
      },
      boxShadow: {
        panel: "0 20px 45px rgba(15, 23, 42, 0.18)"
      }
    },
  },
  plugins: [],
};

