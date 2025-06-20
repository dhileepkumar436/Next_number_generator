
# 🔢 Next Number Generator - Java Spring Boot Backend

A backend service to generate custom formatted codes based on dynamic configuration, using Java Spring Boot and PostgreSQL.

---

## 📌 Features

- ✅ Generate codes dynamically using config values
- 🔒 JWT-based user authentication
- 📜 API endpoints to manage configuration, history, and archive
- 🧠 Auto-increment logic with reset mechanism (daily/monthly)
- 💾 PostgreSQL database integration
- 📂 Archive old history records automatically
- 📈 REST API with JSON response
- 🌐 Can be connected to a React frontend

---

## ⚙️ Technologies Used

- Java 17
- Spring Boot
- Spring Web
- Spring Data JPA
- PostgreSQL
- JWT Authentication
- Maven

---


## 🔐 Authentication

- Login with `username` and `password` to receive a JWT token.
- Use the token in headers for accessing secure endpoints.


---

## 📮 API Endpoints

| Method | Endpoint             | Description                  |
|--------|----------------------|------------------------------|
| POST   | `/api/generate`      | Generate next number         |
| GET    | `/api/config`        | View config table            |
| POST   | `/api/config`        | Update config                |
| GET    | `/api/history`       | View history records         |
| GET    | `/api/archived`      | View archived records        |

---

## 🗃️ Database Schema

### Tables:
- `config`
- `history`
- `archive_history`




