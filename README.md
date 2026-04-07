# 🛍️ Radiance E-Commerce API & Platform

## 📌 Overview
Radiance is a full-stack, professional-grade e-commerce platform featuring an administrative dashboard, cart mechanics, secure checkout, and robust product management. This repository contains the **Java Spring Boot backend**, providing a reliable and secure RESTful API.

## 🚀 Key Features
- **User Engagement**: Comprehensive product browsing, robust cart mechanics, and seamless checkout pipeline.
- **Admin Dashboard**: Full product lifecycle management, role designation, and centralized order oversight.
- **Role-Based Authentication**: Secure endpoints distinguished by predefined roles (`ROLE_USER`, `ROLE_ADMIN`). Ensures that restricted actions like adding products are strictly guarded.
- **RESTful Architecture**: Clean and standardized JSON request and response payloads that make integrations seamless.
- **Robust Validation**: Explicit server-side object validation before transacting with the database minimizing erroneous inputs.

## 🛠️ Technology Stack
- **Backend Architecture**: Java 17+, Spring Boot
- **Security Protocols**: Spring Security, BCrypt Password Encoding, stateless sesson policies via JWT/Tokens.
- **Persistence Layer**: PostgreSQL with Spring Data JPA/Hibernate
- **Frontend Layer (External)**: React.js coupled with Tailwind CSS for dynamic layouts.
- **Deployment Platform**: Railway (Backend), Vercel (Frontend)

## 🗃️ Core Entity Models
- **User**: authentication boundaries, roles assignment, and authorization data.
- **Product**: Items catalogue representing prices, stock quantities, and core descriptors.
- **Order & Cart**: Relational models managing live shopping sessions and final, finalized receipts. 

## ⚙️ Local Development Setup
1. **Clone the repository**:
   ```bash
   git clone https://github.com/emizzy03/radiance.git
   ```
2. **Navigate to the Backend project directory**:
   ```bash
   cd radiance/Back-End
   ```
3. **Configure the Database Structure**:
   Set your PostgreSQL connection credentials within your environment variables or a local `application.properties`/`application.yml` file under `src/main/resources`.
4. **Compile and Run**:
   ```bash
   mvn clean spring-boot:run
   ```

*The local API server defaults to mapping requests on `http://localhost:8080`.*

---
*Built with ❤️ to power engaging e-commerce digital storefronts.*
