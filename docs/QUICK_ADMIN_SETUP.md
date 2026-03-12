# Quick Admin Setup

## 🚀 Automatic Setup (No Action Required!)

The admin user is **automatically created** when you start the application!

## 📋 Default Admin Credentials

```
Username: admin
Password: admin123
Email:    admin@radiance.com
```

## ✅ How to Login

### Step 1: Start the Backend
```bash
cd Back-End
mvn spring-boot:run
```

### Step 2: Start the Frontend
```bash
cd frontend
npm start
```

### Step 3: Login
1. Go to: `http://localhost:3000/login`
2. Enter:
   - Username: `admin`
   - Password: `admin123`
3. Click "Sign in"
4. You'll have admin access! ✅

## 🔍 Verify Admin Was Created

When you start the backend, you should see:
```
✅ Created ROLE_USER
✅ Created ROLE_ADMIN
✅ Created admin user:
   Username: admin
   Email: admin@radiance.com
   Password: admin123 (CHANGE THIS IN PRODUCTION!)
```

## 🎯 What You Can Do as Admin

- ✅ Access Admin Dashboard: `http://localhost:3000/admin`
- ✅ Create/Edit/Delete Products
- ✅ Upload Product Images
- ✅ Manage Users and Roles

## ⚠️ Important for Production

**Change the default password!** Update `DataInitializer.java` or use environment variables.

## 📝 That's It!

Just start the app and login with `admin` / `admin123` - it's that simple! 🎉

