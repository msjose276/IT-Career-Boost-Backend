# IT Career Boost Backend

Spring Boot API for the IT Career Boost content platform.

## Run locally

```bash
mvn spring-boot:run
```

The API runs on `http://localhost:8080`.

## Production configuration

The app reads these environment variables in production:

- `PORT`: HTTP port provided by the host.
- `APP_CORS_ALLOWED_ORIGINS`: comma-separated frontend origins allowed to call the API.
- `APP_ADMIN_EMAIL`: admin login email.
- `APP_ADMIN_PASSWORD`: admin login password.

## Admin credentials

- Email: `admin@itcareerboost.local`
- Password: `careerboost`

Use `POST /api/auth/login` to receive a bearer token, then pass it as:

```http
Authorization: Bearer <token>
```

## Endpoints

- `GET /api/state`
- `GET /api/articles?q=&category=&tag=&sort=`
- `GET /api/articles/{slug}`
- `POST /api/auth/login`
- `GET /api/admin/state`
- `GET /api/admin/dashboard`
- `POST /api/admin/articles`
- `PUT /api/admin/articles/{id}`
- `DELETE /api/admin/articles/{id}`
- `POST /api/admin/categories`
- `POST /api/admin/tags`
