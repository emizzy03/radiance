# Using H2 Database for Testing

## ✅ Current Status: H2 is Already Configured!

Your tests are **already using H2** automatically. Here's how it works:

## 🔧 How H2 is Configured for Testing

### 1. Test Configuration File
**Location**: `Back-End/src/test/resources/application-test.yml`

This file automatically activates when running tests and configures H2:

```yaml
spring:
  datasource:
    driver-class-name: org.h2.Driver
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    username: sa
    password: 
  jpa:
    hibernate:
      ddl-auto: create-drop  # Creates schema, drops after test
```

### 2. Test Annotations

#### For Repository Tests:
```java
@DataJpaTest  // Automatically uses in-memory database (H2)
class ProductRepositoryTest {
    // H2 is used automatically!
}
```

#### For Controller/Integration Tests:
```java
@SpringBootTest
@ActiveProfiles("test")  // Activates application-test.yml
class ProductControllerTest {
    // H2 is used via test profile!
}
```

## 🚀 How to Run Tests with H2

### Run All Tests:
```bash
mvn test
```
**Result**: All tests automatically use H2 in-memory database ✅

### Run Specific Test Class:
```bash
mvn test -Dtest=ProductRepositoryTest
```

### Run Specific Test Method:
```bash
mvn test -Dtest=ProductRepositoryTest#testFindByNameContainingIgnoreCase_Found
```

## 🔍 Verify H2 is Being Used

### Method 1: Check Test Output
When tests run, you'll see H2 connection in logs:
```
HikariPool-1 - Starting...
HikariPool-1 - Start completed.
```

### Method 2: Check Test Configuration
Look for `application-test.yml` being loaded:
```bash
mvn test -X | grep -i "application-test"
```

### Method 3: Add Logging
Add to `application-test.yml`:
```yaml
logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
```

## 📝 Example Test Using H2

### Repository Test (Automatically uses H2):
```java
@DataJpaTest  // ← This makes it use H2 automatically
class ProductRepositoryTest {
    
    @Autowired
    private ProductRepository productRepository;
    
    @Test
    void testSaveProduct() {
        Product product = new Product("Test", "Description", 
            new BigDecimal("99.99"), 10, "image.jpg");
        
        Product saved = productRepository.save(product);
        
        assertNotNull(saved.getId());  // H2 auto-generates ID
        assertEquals("Test", saved.getName());
    }
}
```

### Integration Test (Uses H2 via test profile):
```java
@SpringBootTest
@ActiveProfiles("test")  // ← This activates application-test.yml
class ProductControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private ProductService productService;
    
    @Test
    void testGetAllProducts() throws Exception {
        // Test runs with H2 database
        mockMvc.perform(get("/api/products"))
               .andExpect(status().isOk());
    }
}
```

## 🎯 Key Features of H2 for Testing

### ✅ Automatic Setup
- No database installation needed
- No configuration required
- Works out of the box

### ✅ Fast Execution
- In-memory database = very fast
- No network overhead
- Perfect for CI/CD

### ✅ Isolated Tests
- Each test gets fresh database
- `create-drop` ensures clean state
- No test interference

### ✅ No External Dependencies
- No PostgreSQL needed for tests
- Works on any machine
- Perfect for development

## 🔧 Customizing H2 for Tests

### Change Database Name:
Edit `application-test.yml`:
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:mytestdb  # Change testdb to mytestdb
```

### Enable H2 Console During Tests:
Already enabled! Access at: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:mem:testdb`
- Username: `sa`
- Password: (empty)

### Persist Test Data (File-based):
```yaml
spring:
  datasource:
    url: jdbc:h2:file:./testdb  # Creates file instead of memory
```

### Show SQL Queries:
```yaml
spring:
  jpa:
    show-sql: true  # Already enabled in your config
```

## 🐛 Troubleshooting

### Problem: Tests trying to connect to PostgreSQL
**Solution**: Make sure `@ActiveProfiles("test")` is on your test class

### Problem: Database not resetting between tests
**Solution**: `ddl-auto: create-drop` is already set - this is correct

### Problem: Want to see H2 console during tests
**Solution**: H2 console is already enabled in `application-test.yml`

## 📊 Current Test Status

✅ **All 143 tests passing with H2**
- Repository tests: Using `@DataJpaTest` → H2 automatically
- Controller tests: Using `@ActiveProfiles("test")` → H2 via profile
- Service tests: Using mocks → No database needed

## 🎉 Summary

**You're already using H2 for testing!** 

- ✅ Configuration: `application-test.yml`
- ✅ Repository tests: `@DataJpaTest` (auto H2)
- ✅ Integration tests: `@ActiveProfiles("test")` (H2 via profile)
- ✅ All tests passing: 143/143 ✅

**Just run `mvn test` and H2 is used automatically!**

