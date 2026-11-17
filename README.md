<p align="center">
  <img src="assets/banner.png" alt="Patient Management System - Candidate B Assessment">
</p>

# 🚑 Patient Management System - Capstone Assessment (Candidate B)

Welcome to the **Patient Management System Capstone Assessment**. This repository contains a Spring Boot-based medical and hospital management system that has been intentionally sabotaged, stripped of enterprise security infrastructure, configured with architectural flaws, and impaired with broken JPA relationships.

Your objective as **Candidate B** is to audit the codebase, diagnose the architectural and runtime defects, and implement production-ready solutions across the **5 core capstone tasks** outlined below.

---

## 🎯 Candidate Tasks Overview

| # | Task Area | Core Focus |
|---|---|---|
| **1** | **Custom Servlet Filter & Security** | Build a Custom Servlet Filter enforcing header authentication via `X-Hospital-Code`. Block requests without valid codes. |
| **2** | **JPA Mapping & N+1 Performance Optimization** | Repair the broken entity relationship (`mappedBy` mismatch), configure `FetchType.LAZY`, and eliminate N+1 subqueries using `JOIN FETCH` / `@EntityGraph`. |
| **3** | **Centralized Global Exception Handling** | Implement a production-ready `@RestControllerAdvice` mapping custom exceptions to standard HTTP response structures. |
| **4** | **Transactional Notification / Email Service** | Implement an asynchronous or fail-safe `EmailService` / `NotificationService` that dispatches confirmations for booked appointments and medication updates. |
| **5** | **Production Readiness & Observability (Actuator)** | Integrate Spring Boot Actuator, expose monitoring endpoints (`/actuator/health`, `/actuator/metrics`), and implement a custom `HospitalSystemHealthIndicator`. |

---

## 📋 Detailed Task Specifications

### 🔹 Task 1: Custom Servlet Filter & Header Authentication (`X-Hospital-Code`)
- **Background**:
  - The legacy Spring Security configuration and session-based authentication have been removed to transition the system to an internal hospital microservice architecture. Access control must now be enforced at the gateway/servlet layer using an HTTP header check.
  - Candidate B is tasked with building a **Custom Servlet Filter** that inspects requests for an `X-Hospital-Code` HTTP header before allowing them to pass through.
- **Deliverables**:
  1. Create a servlet filter class (e.g. `HospitalCodeFilter` extending `OncePerRequestFilter` or implementing `jakarta.servlet.Filter`) in package `com.example.patient_system.filter`.
  2. The filter must:
     - Check incoming requests for the `X-Hospital-Code` HTTP header.
     - Validate the header value against the configured hospital code in `application.properties` (key: `hospital.security.header.expected-code`, default: `HOSP-NYC-001`) or ensure it is non-empty and well-formed.
     - If the header is **missing, empty, or invalid**:
       - Halt request processing immediately.
       - Return HTTP status `401 UNAUTHORIZED` (or `403 FORBIDDEN`).
       - Respond with a clear JSON error payload:
         ```json
         {
           "timestamp": "2026-11-17T10:45:00",
           "status": 401,
           "error": "Unauthorized",
           "message": "Missing or invalid X-Hospital-Code header",
           "path": "/api/patient/1"
         }
         ```
     - If the header is **present and valid**:
       - Allow the request to pass down the filter chain (`filterChain.doFilter(request, response)`).
  3. **Exclusion Rules**: Ensure public/operational endpoints (such as `/h2-console/**`, `/actuator/health`, and static assets under `/css/**`, `/js/**`) can bypass header inspection.
  4. Register the filter using `@Component` or `@Bean FilterRegistrationBean<HospitalCodeFilter>` with appropriate URL mapping.

---

### 🔹 Task 2: Fix Broken JPA Relationship & Eliminate N+1 Performance Issues
- **Background**:
  - The application currently experiences entity mapping errors on startup and fails context loading.
  - The relationship mapping between `Patient` and `Appointment` in `Patient.java` has been broken (`mappedBy = "patientProfile"` mismatch against the owning field `patient` in `Appointment.java`), throwing an `AnnotationException` during Hibernate initialization.
  - Associated collections (`appointments` and `medications`) in `Patient.java` are set to `FetchType.EAGER`, causing Cartesian product issues and severe N+1 SELECT query bottlenecks when querying patient records.
- **Deliverables**:
  1. Fix the broken JPA relationship in `Patient.java` so that `mappedBy` aligns accurately with the corresponding owning entity (`Appointment.java`'s `patient` property).
  2. Eliminate the N+1 query problem:
     - Change collection fetch configurations on `appointments` and `medications` from `FetchType.EAGER` to `FetchType.LAZY`.
     - Where associated entities are genuinely required (e.g., retrieving a patient alongside their complete appointments and medication histories), implement optimized queries in `PatientRepository` / `AppointmentRepository` using `JOIN FETCH` or Spring Data `@EntityGraph`.
  3. Verify bidirectional consistency and cascade definitions (`CascadeType.ALL`, `orphanRemoval`) across `Patient`, `Appointment`, `Doctor`, and `Medication`.

---

### 🔹 Task 3: Centralized Global Exception Handling
- **Background**:
  - Centralized exception handlers were completely deleted.
  - Domain exceptions (`IllegalArgumentException`, patient not found, appointment slot conflicts, validation errors) result in raw stack traces or default 500 error responses that leak internal implementation details and provide a poor client experience.
- **Deliverables**:
  1. Implement a centralized exception handler using `@RestControllerAdvice` (or `@ControllerAdvice`) in package `com.example.patient_system.exception`.
  2. Create domain-specific custom exceptions where appropriate:
     - `ResourceNotFoundException` (or `PatientNotFoundException`, `DoctorNotFoundException`)
     - `AppointmentSlotUnavailableException`
     - `InvalidHospitalCodeException`
  3. Return a consistent, structured JSON error response:
     ```json
     {
       "timestamp": "2026-11-17T10:45:00",
       "status": 404,
       "error": "Not Found",
       "message": "Patient not found with id: 99",
       "path": "/api/patient/99"
     }
     ```
  4. Implement specific `@ExceptionHandler` methods for:
     - `ResourceNotFoundException` / `PatientNotFoundException` ➡️ **404 NOT FOUND**
     - `AppointmentSlotUnavailableException` / `IllegalArgumentException` ➡️ **400 BAD REQUEST**
     - `MethodArgumentNotValidException` ➡️ **400 BAD REQUEST** (with detailed field-by-field validation failure messages)
     - `DateTimeParseException` ➡️ **400 BAD REQUEST**
     - Generic `Exception.class` fallback ➡️ **500 INTERNAL SERVER ERROR**

---

### 🔹 Task 4: Implement Transactional Patient Notification / Email Service
- **Background**:
  - The hospital system lacks an automated notification and alerting mechanism. Patients and doctors receive no confirmation when appointments are scheduled or when medications are prescribed or updated.
- **Deliverables**:
  1. Design and implement an `EmailService` / `NotificationService` interface and its corresponding implementation in `com.example.patient_system.service`.
  2. Integrate the notification service into:
     - `AppointmentService.bookAppointment()`: dispatch an appointment confirmation email containing patient name, doctor name, scheduled date/time, and appointment status.
     - `MedicationService.addMedication()`: dispatch a medication prescription alert with dosage and frequency instructions.
  3. Notification payloads should contain:
     - Recipient email address
     - Subject line (e.g. `Appointment Confirmation - Reference #...`)
     - Body with structured appointment/medication details
     - Timestamp
  4. **Resilience & Best Practices**:
     - Implement mock email logging or integrate with Spring's `JavaMailSender`.
     - Ensure notifications are asynchronous (`@Async` with `@EnableAsync`) or wrapped in fail-safe error handling so an email delivery failure does NOT abort or roll back a successful database transaction.

---

### 🔹 Task 5: Production Readiness & Observability with Spring Boot Actuator
- **Background**:
  - The application lacks health checking, metrics, and production monitoring dependencies. DevOps and operations teams cannot monitor system uptime, database availability, or component health.
- **Deliverables**:
  1. Add `spring-boot-starter-actuator` to `pom.xml`.
  2. Configure `application.properties` to expose monitoring endpoints:
     ```properties
     management.endpoints.web.exposure.include=health,info,metrics
     management.endpoint.health.show-details=always
     ```
  3. Implement a custom `HealthIndicator` (e.g., `HospitalSystemHealthIndicator` in package `com.example.patient_system.actuator`):
     - Validates database connectivity and repository operational readiness.
     - Reports custom status (`UP` / `DOWN`) along with diagnostic details (e.g. `database: connected`, `hospitalCode: active`).
  4. Verify that `/actuator/health` and `/actuator/metrics` return valid JSON telemetry.

---

## 🛠️ Technology Stack & Prerequisites

- **Java**: JDK 17
- **Framework**: Spring Boot 3.2.5 (Spring MVC, Spring Data JPA, Hibernate, Bean Validation)
- **Database**: 
  - **H2 In-Memory Database** (configured by default for immediate local testing)
  - **PostgreSQL** (optional driver included for production parity)
- **Build Tool**: Apache Maven 3.6+ (or included `./mvnw`)

---

## 🚀 Local Setup & Execution Guide

### 1. Database Configuration
By default, the application is configured to run on an in-memory **H2** database. No external database installation is required for local evaluation.

To inspect the H2 database:
- **H2 Console URL**: `http://localhost:8080/h2-console`
- **JDBC URL**: `jdbc:h2:mem:patientdb`
- **Username**: `sa`
- **Password**: *(leave blank)*

*(Optional)* If testing with PostgreSQL, uncomment the PostgreSQL block in `src/main/resources/application.properties`.

### 2. Build and Run
From the project root:
```bash
# Compile and run unit tests (skipping context boot test until JPA mapping is fixed)
./mvnw clean test-compile

# Run the application
./mvnw spring-boot:run
```

The application starts on `http://localhost:8080`.
- **H2 Web Console**: `http://localhost:8080/h2-console`
- **Actuator Health (once Task 5 is implemented)**: `http://localhost:8080/actuator/health`

---

## 🧪 Testing Your Fixes

### 1. Custom Header Filter Verification (Task 1)
```bash
# Test 1: Request WITHOUT X-Hospital-Code header (Must return HTTP 401 Unauthorized)
curl -i -X GET http://localhost:8080/api/patient/1

# Test 2: Request WITH valid X-Hospital-Code header (Must proceed successfully)
curl -i -X GET http://localhost:8080/api/patient/1 -H "X-Hospital-Code: HOSP-NYC-001"
```

### 2. JPA Startup & N+1 Query Inspection (Task 2)
1. Run `./mvnw test -Dtest=PatientSystemApplicationTests` to verify that `ApplicationContext` boots without `AnnotationException`.
2. Inspect the console logs while querying patients with `spring.jpa.show-sql=true`. Verify that retrieving patient records executes a single `JOIN FETCH` query rather than firing N individual subqueries for appointments and medications.

### 3. Centralized Exception Handling Verification (Task 3)
```bash
# Request a non-existent patient (Must return HTTP 404 with structured JSON)
curl -i -X GET http://localhost:8080/api/patient/99999 -H "X-Hospital-Code: HOSP-NYC-001"
```
**Expected Response**:
```json
{
  "timestamp": "...",
  "status": 404,
  "error": "Not Found",
  "message": "Patient not found with id: 99999",
  "path": "/api/patient/99999"
}
```

### 4. Email Notification Verification (Task 4)
Book an appointment or add medication and check application logs for asynchronous notification confirmation output:
```
[INFO] [NotificationService] Dispatching appointment confirmation email to john@example.com for Dr. Smith on 2026-11-20T10:00:00. Reference ID: APPT-101
```

### 5. Health Check & Observability Verification (Task 5)
```bash
# Verify Actuator health endpoint (Must return HTTP 200 with custom indicator details)
curl -i -X GET http://localhost:8080/actuator/health
```

---

## 📂 Git Branching & Submission Instructions

1. **Active Branch**: Work on the `Ax_hackathon` branch:
   ```bash
   git checkout -b Ax_hackathon
   ```
2. **Commit Guidelines**:
   - Commit changes logically per task:
     - `feat(filter): implement custom servlet filter for X-Hospital-Code header`
     - `fix(jpa): repair patient appointment mappedBy relationship and set lazy fetching`
     - `feat(exception): add centralized global exception handler and custom exceptions`
     - `feat(notification): implement asynchronous email service for appointments`
     - `feat(actuator): add spring boot actuator and custom hospital health indicator`
3. **Submission**:
   - Push your branch to the remote repository:
     ```bash
     git push origin Ax_hackathon
     ```
   - Open a Pull Request from `Ax_hackathon` targeting `main` with detailed notes on your fixes.
