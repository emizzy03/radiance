# Admin Login Setup Guide

## ✅ Automatic Setup (Recommended)

The application **automatically creates an admin user** on startup!

### Default Admin Credentials:
- **Username**: `admin`
- **Email**: `admin@radiance.com`
- **Password**: `admin123`

**⚠️ IMPORTANT**: Change the default password in production!

## 🚀 How to Login as Admin

### Option 1: Via Frontend
1. Start the backend: `mvn spring-boot:run`
2. Start the frontend: `cd frontend && npm start`
3. Go to: `http://localhost:3000/login`
4. Enter credentials:
   - Username: `admin`
   - Password: `admin123`
5. Click "Sign in"
6. You'll be redirected to the home page with admin access

### Option 2: Via API (Postman/curl)

**Login Request:**
```bash
POST http://localhost:8080/api/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "admin123"
}
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "user": {
    "id": 1,
    "username": "admin",
    "email": "admin@radiance.com",
    "roles": [
      {
        "id": 2,
        "name": "ROLE_ADMIN"
      }
    ]
  }
}
```

**Use the token in subsequent requests:**
```bash
Authorization: Bearer <your-token-here>
```

## 🔧 Manual Admin Setup

If you need to create an admin user manually:

### Method 1: Using UserService (Programmatically)

Create a simple script or use the existing endpoint:

```java
@Autowired
private UserService userService;

// Create admin user
User admin = userService.createAdminUser(
    "admin", 
    "admin@radiance.com", 
    "your-secure-password"
);
```

### Method 2: Via API (After creating roles)

**Step 1: Create ROLE_ADMIN** (if not exists)
```bash
# First, create a regular user
POST http://localhost:8080/api/users
{
  "username": "admin",
  "email": "admin@radiance.com",
  "password": "admin123"
}
```

**Step 2: Assign Admin Role**
```bash
POST http://localhost:8080/api/users/{userId}/roles/ROLE_ADMIN
Authorization: Bearer <token>
```

### Method 3: Using H2 Console (Development Only)

1. Start the application
2. Go to: `http://localhost:8080/h2-console`
3. Connect with:
   - JDBC URL: `jdbc:h2:mem:radiancedb`
   - Username: `sa`
   - Password: (empty)
4. Run SQL:
```sql
-- Create ROLE_ADMIN if not exists
INSERT INTO roles (name) 
SELECT 'ROLE_ADMIN' 
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ROLE_ADMIN');

-- Create admin user (password is bcrypt encoded 'admin123')
-- You'll need to get the encoded password from UserService
```

## 🔐 Changing Admin Password

### Via API:
```bash
PATCH http://localhost:8080/api/users/{adminUserId}/password
Authorization: Bearer <admin-token>
Content-Type: application/json

{
  "currentPassword": "admin123",
  "newPassword": "newSecurePassword123"
}
```

### Programmatically:
```java
userService.updatePassword(adminUserId, "admin123", "newSecurePassword123");
```

## 🛡️ Admin Permissions

Admins have access to:
- ✅ Create/Update/Delete products (`POST /api/products`, `PATCH /api/products/{id}`, `DELETE /api/products/{id}`)
- ✅ Upload/Delete images (`POST /api/upload/image`, `DELETE /api/upload/image/{filename}`)
- ✅ Access admin dashboard (frontend: `/admin`)
- ✅ View all users
- ✅ Assign/Remove roles from users

## 🔍 Verify Admin Setup

### Check if admin exists:
```bash
GET http://localhost:8080/api/users/username/admin
```

### Check admin roles:
```bash
GET http://localhost:8080/api/users/username/admin
# Response will include roles array with ROLE_ADMIN
```

## 🎯 Production Setup

### 1. Change Default Password
Update `DataInitializer.java`:
```java
String adminPassword = System.getenv("ADMIN_PASSWORD");
if (adminPassword == null) {
    adminPassword = "your-secure-default-password";
}
```

### 2. Use Environment Variables
Set in production:
```bash
export ADMIN_PASSWORD=your-secure-password
export ADMIN_EMAIL=admin@yourdomain.com
```

### 3. Disable Auto-Creation (Optional)
Add condition in `DataInitializer.java`:
```java
@Value("${app.auto-create-admin:true}")
private boolean autoCreateAdmin;

if (autoCreateAdmin && !userService.existsByUsername("admin")) {
    // Create admin...
}
```

## 📝 What Gets Created on Startup

1. **ROLE_USER** - For regular users
2. **ROLE_ADMIN** - For administrators
3. **Admin User** - Default admin account

All are created automatically if they don't exist!

## 🐛 Troubleshooting

### Problem: "Admin role does not exist"
**Solution**: The `DataInitializer` should create it automatically. Check application logs on startup.

### Problem: Can't login with admin credentials
**Solution**: 
1. Check if admin user exists: `GET /api/users/username/admin`
2. Verify password is correct
3. Check if ROLE_ADMIN exists in database

### Problem: Admin user created but no admin access
**Solution**: 
1. Verify user has ROLE_ADMIN role
2. Check SecurityConfig allows ADMIN role
3. Ensure JWT token includes roles

## ✅ Quick Start

1. **Start the application:**
   ```bash
   cd Back-End
   mvn spring-boot:run
   ```

2. **Look for startup messages:**
   ```
   ✅ Created ROLE_USER
   ✅ Created ROLE_ADMIN
   ✅ Created admin user:
      Username: admin
      Email: admin@radiance.com
      Password: admin123
   ```

3. **Login at:** `http://localhost:3000/login`
   - Username: `admin`
   - Password: `admin123`

4. **Access admin dashboard:** `http://localhost:3000/admin`

## 🎉 Summary

- ✅ Admin user is **automatically created** on startup
- ✅ Default credentials: `admin` / `admin123`
- ✅ Roles are **automatically initialized**
- ✅ Ready to use immediately after starting the app!

**Just start the application and login with the default admin credentials!**

