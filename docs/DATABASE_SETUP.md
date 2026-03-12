# Database Configuration Guide

## ✅ Current Setup (Correct)

### Configuration Files:

1. **`application.yml`** (Default - Development)
   - **Database**: H2 (in-memory)
   - **Purpose**: Easy local development, no installation needed
   - **Port**: 8080
   - **H2 Console**: Enabled at `/h2-console`

2. **`application-prod.yml`** (Production Profile)
   - **Database**: PostgreSQL
   - **Purpose**: Production deployment (Railway)
   - **Port**: 8081
   - **Requires**: Environment variables (DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD)

3. **`application-test.yml`** (Test Profile)
   - **Database**: H2 (in-memory)
   - **Purpose**: Unit and integration tests
   - **Auto-activated**: When running tests

## 🎯 Which Database to Use

### ✅ For Local Development:
**Use H2** (Default in `application.yml`)
- No installation required
- Fast startup
- Perfect for development
- H2 Console available for debugging

**Run:**
```bash
mvn spring-boot:run
# Or
cd Back-End && mvn spring-boot:run
```

### ✅ For Production:
**Use PostgreSQL** (Profile: `prod`)
- Production-grade database
- Required for Railway deployment
- Better performance for production workloads

**Run:**
```bash
mvn spring-boot:run -Dspring.profiles.active=prod
# Or set environment variable:
# SPRING_PROFILES_ACTIVE=prod
```

**Required Environment Variables:**
- `DB_HOST` (default: localhost)
- `DB_PORT` (default: 5432)
- `DB_NAME` (default: radiance)
- `DB_USERNAME` (required)
- `DB_PASSWORD` (required)

### ✅ For Testing:
**Use H2** (Auto-configured in `application-test.yml`)
- Fast test execution
- Isolated test database
- No external dependencies
- Already correctly configured ✅

**Run:**
```bash
mvn test
```

## 📋 Summary

| Environment | Database | Configuration File | Status |
|------------|----------|-------------------|--------|
| **Development** | H2 | `application.yml` | ✅ Correct |
| **Production** | PostgreSQL | `application-prod.yml` | ✅ Correct |
| **Testing** | H2 | `application-test.yml` | ✅ Correct |

## 🔧 Switching Between Databases

### Development → Production:
```bash
# Option 1: Command line
mvn spring-boot:run -Dspring.profiles.active=prod

# Option 2: Environment variable
export SPRING_PROFILES_ACTIVE=prod
mvn spring-boot:run

# Option 3: In IDE (IntelliJ/Eclipse)
# Run Configuration → VM Options: -Dspring.profiles.active=prod
```

### Production → Development:
```bash
# Just run without profile (uses default H2)
mvn spring-boot:run
```

## 🎉 Conclusion

**Current configuration is CORRECT:**
- ✅ H2 for development (easy setup)
- ✅ PostgreSQL for production (as per README)
- ✅ H2 for testing (fast, isolated)

No changes needed! The setup supports both databases correctly.

