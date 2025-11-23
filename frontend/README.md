# Radiance Frontend

React + Tailwind CSS frontend for the Radiance e-commerce platform.

## Features

- 🛍️ Product browsing and search
- 🛒 Shopping cart with persistent storage
- 💳 Checkout process
- 👤 User authentication (Login/Register)
- 🔐 Role-based access control
- 👨‍💼 Admin dashboard for product management
- 📱 Responsive design

## Setup

1. Install dependencies:
```bash
npm install
```

2. Create a `.env` file (optional):
```env
REACT_APP_API_URL=http://localhost:8080
```

3. Start the development server:
```bash
npm start
```

The app will open at `http://localhost:3000`

## Project Structure

```
frontend/
├── public/
│   └── index.html
├── src/
│   ├── components/
│   │   └── Navbar.js
│   ├── context/
│   │   ├── AuthContext.js
│   │   └── CartContext.js
│   ├── pages/
│   │   ├── Home.js
│   │   ├── Products.js
│   │   ├── ProductDetail.js
│   │   ├── Cart.js
│   │   ├── Checkout.js
│   │   ├── Login.js
│   │   ├── Register.js
│   │   └── AdminDashboard.js
│   ├── services/
│   │   └── api.js
│   ├── App.js
│   ├── index.js
│   └── index.css
├── package.json
├── tailwind.config.js
└── postcss.config.js
```

## API Integration

The frontend communicates with the backend API at `http://localhost:8080` by default. Make sure the backend is running before starting the frontend.

## Available Scripts

- `npm start` - Start development server
- `npm build` - Build for production
- `npm test` - Run tests

## Technologies

- React 18
- React Router 6
- Tailwind CSS 3
- Axios

