# Product Inventory & Analytics Service

API REST para la gestión integral de catálogo de productos, control de inventario y generación de métricas de ventas enriquecidas con Inteligencia Artificial.

---

## Tabla de Contenidos

- [Descripción](#descripción)
- [Stack Tecnológico](#stack-tecnológico)
- [Arquitectura](#arquitectura)
- [Módulos Principales](#módulos-principales)
- [Modelo de Datos](#modelo-de-datos)
- [Autenticación y Seguridad](#autenticación-y-seguridad)
- [Integración con IA](#integración-con-ia)
- [Caché de Sesiones (Redis)](#caché-de-sesiones-redis)
- [Endpoints Principales](#endpoints-principales)
- [Configuración y Entorno](#configuración-y-entorno)
- [Ejecución Local](#ejecución-local)
- [Roadmap](#roadmap)

---

## Descripción

Este servicio backend expone una API REST que permite:

- **Gestión de productos vía agente IA**: alta y edición de productos mediante instrucciones en lenguaje natural y/o archivos adjuntos (PDF, imágenes, CSV, texto plano).
- **Catálogo**: visualización paginada del catálogo con campos SKU, imagen, descripción, precio, stock y marca de tiempo de última actualización.
- **Dashboard de métricas**: análisis de ventas con resúmenes e insights generados por un modelo de IA intercambiable.
- **Autenticación**: login seguro con JWT para proteger el acceso al dashboard y a las operaciones de escritura.

---

## Stack Tecnológico

| Capa | Tecnología |
| :--- | :--- |
| Lenguaje | Java 21 (LTS) |
| Framework | Spring Boot 3.3 |
| Persistencia | Spring Data JPA / Hibernate |
| Base de Datos | PostgreSQL 16 |
| Seguridad | Spring Security + JWT (jjwt 0.12) |
| Agente IA | HTTP client genérico — proveedor configurable por entorno |
| RAG (documentos) | Apache PDFBox + vector store en memoria (cosine similarity) |
| Caché de sesiones | Caffeine en memoria — migrable a **Redis** para producción multi-instancia |
| Reintentos / Escalado | Spring Retry |
| Mapeo de modelos | MapStruct |
| Documentación API | Springdoc OpenAPI / Swagger UI |
| Build | Maven 3.9+ (Maven Wrapper incluido) |
| Runtime | JDK 21 |
| Tests | JUnit 5 + Testcontainers (PostgreSQL) |

---

## Arquitectura

### Hexagonal (Ports & Adapters)

El dominio nunca depende de frameworks ni de proveedores externos. Los adaptadores son intercambiables por configuración.

```
com.inventory
├── domain/                          # Núcleo — cero dependencias de frameworks
│   ├── model/                       # Product, AgentSession, ExtractionResult, ProductOperation
│   └── port/                        # Puertos in (casos de uso) y out (repositorios)
│
├── service/                         # Orquesta sesión → RAG → agente → persistencia
│
├── controller/                      # Controladores REST y DTOs
│
└── infrastructure/                  # Adaptadores (detalles técnicos)
    ├── ai/                          # Agente IA, HttpAiClient, escalado L1→L2→L3
    ├── persistence/                 # JPA, PostgreSQL, MapStruct
    ├── storage/                     # RAG, VectorStore, PDFBox
    └── config/                      # Spring Security, JWT, Caché, Retry
```

---

## Módulos Principales

### 1. Agente de Productos (`/api/products/agent`)
- Crea y actualiza productos mediante instrucciones en lenguaje natural y/o archivos adjuntos.
- Soporta conversaciones multi-turno: si faltan campos, solicita la información al cliente.

### 2. Catálogo (`/api/catalog`)
- Vista pública/privada del catálogo paginado.
- Edición inline de precio, descripción y stock.

### 3. Métricas de Ventas (`/api/metrics`)
- Totales de ventas por período.
- Productos más vendidos / con menor rotación.
- Resumen e insights generados por IA (proveedor configurable).

### 4. Autenticación (`/api/auth`)
- Login con usuario y contraseña → devuelve JWT.
- Refresh de token.
- Roles: `ADMIN` (gestión completa), `VIEWER` (solo lectura).

---

## Modelo de Datos

### Entidad `Product`

| Campo | Tipo | Descripción |
| :--- | :--- | :--- |
| `id` | `UUID` | Identificador único (generado) |
| `sku` | `VARCHAR(100)` | Código de producto único |
| `name` | `VARCHAR(255)` | Nombre del producto |
| `description` | `TEXT` | Descripción detallada |
| `imageUrl` | `VARCHAR(500)` | URL o ruta de la imagen |
| `price` | `DECIMAL(12,2)` | Precio de venta |
| `stock` | `INTEGER` | Unidades disponibles |
| `lastUpdatedAt` | `TIMESTAMP WITH TIME ZONE` | Gestionado por `@UpdateTimestamp` |
| `createdAt` | `TIMESTAMP WITH TIME ZONE` | Gestionado por `@CreationTimestamp` |

### Entidad `User`

| Campo | Tipo | Descripción |
| :--- | :--- | :--- |
| `id` | `UUID` | Identificador único |
| `username` | `VARCHAR(100)` | Nombre de usuario único |
| `passwordHash` | `VARCHAR(255)` | Contraseña hasheada (BCrypt) |
| `role` | `ENUM` | `ADMIN` / `VIEWER` |
| `createdAt` | `TIMESTAMP WITH TIME ZONE` | Fecha de creación |

---

## Autenticación y Seguridad

- **Mecanismo**: JWT Bearer Token en el header `Authorization`.
- **Generación**: `POST /api/auth/login` → devuelve `accessToken` (1 hora) y `refreshToken` (7 días).
- **Protección de rutas**: escrituras (`POST`, `PUT`, `DELETE`) → `ADMIN`. Catálogo GET → público. Métricas → `ADMIN`.
- **Contraseñas**: `BCryptPasswordEncoder`.
- **Flexibilidad**: desacoplado del dominio, migrable a OAuth2 sin cambios en la lógica de negocio.

---

## Integración con IA

Todos los componentes de IA están detrás de puertos genéricos. Ningún proveedor se nombra en el código.

```java
// Puerto de agente (domain/port/out)
public interface ProductAgentPort {
    ExtractionResult extractProduct(String instruction, String ragContext, String sessionContext);
}

// Puerto de análisis (domain/port/out)
public interface AiAnalyticsPort {
    String generateSalesSummary(String salesDataJson);
    List<String> generateProductInsights(String productsJson);
}
```

El adaptador concreto se selecciona por configuración (`application.yml`) sin cambiar una sola línea del dominio.

---

## Caché de Sesiones (Redis)

- **Actual (desarrollo)**: Caffeine in-memory — TTL 30 min, máx. 1 000 sesiones. No apto para múltiples réplicas ya que el estado no se comparte entre instancias.
- **Producción (multi-instancia)**: reemplazar el bean `CacheManager` en [`CacheConfig.java`](src/main/java/com/inventory/infrastructure/config/CacheConfig.java) con `RedisCacheManager` + dependencia `spring-boot-starter-data-redis`. Configurar `REDIS_HOST`, `REDIS_PORT` y `REDIS_PASSWORD` como variables de entorno. El dominio y el servicio de aplicación no requieren ningún cambio.

---

## Endpoints Principales

| Método | Ruta | Descripción | Rol |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/auth/login` | Login — devuelve JWT | Público |
| `POST` | `/api/auth/refresh` | Renovar access token | Autenticado |
| `POST` | `/api/products/agent` | Crear/actualizar producto vía agente IA | `ADMIN` |
| `GET` | `/api/products` | Listar productos (paginado) | `VIEWER` / `ADMIN` |
| `GET` | `/api/products/{id}` | Detalle de producto | `VIEWER` / `ADMIN` |
| `PUT` | `/api/products/{id}` | Actualizar producto | `ADMIN` |
| `DELETE` | `/api/products/{id}` | Eliminar producto | `ADMIN` |
| `POST` | `/api/products/{id}/image` | Subir imagen | `ADMIN` |
| `GET` | `/api/catalog` | Catálogo público paginado | Público |
| `GET` | `/api/metrics/sales` | Métricas de ventas | `ADMIN` |
| `GET` | `/api/metrics/insights` | Insights generados por IA | `ADMIN` |

---

## Configuración y Entorno

### Requisitos Previos

- JDK 21
- Docker (para PostgreSQL) o PostgreSQL 16 local
- Maven 3.9+ o usar el Maven Wrapper incluido (`./mvnw`)

### Variables de Entorno

Copiar `.env.example` a `.env` y completar:

| Variable | Descripción | Ejemplo |
| :--- | :--- | :--- |
| `SERVER_PORT` | Puerto de la API | `8080` |
| `DB_URL` | URL JDBC PostgreSQL | `jdbc:postgresql://localhost:5432/inventory_db` |
| `DB_USERNAME` | Usuario de BD | `postgres` |
| `DB_PASSWORD` | Contraseña de BD | `secret` |
| `JWT_SECRET` | Clave JWT (mín. 256 bits) | `super-secret-key...` |
| `JWT_EXPIRATION_MS` | Duración access token | `3600000` |
| `JWT_REFRESH_EXPIRATION_MS` | Duración refresh token | `604800000` |
| `AI_PROVIDER_ENDPOINT` | URL base del proveedor de IA | `https://api.example.com/v1` |
| `AI_PROVIDER_API_KEY` | API Key del proveedor base | — |
| `AI_PROVIDER_MODEL` | Modelo base (embedding) | `text-embedding-model` |
| `AI_AGENT_L1_MODEL` | Modelo agente nivel 1 (rápido) | `fast-model` |
| `AI_AGENT_L2_MODEL` | Modelo agente nivel 2 (estándar) | `standard-model` |
| `AI_AGENT_L3_MODEL` | Modelo agente nivel 3 (poderoso) | `powerful-model` |
| `IMAGE_STORAGE_PATH` | Ruta local para imágenes | `./uploads` |
| `REDIS_HOST` | Host Redis (solo producción) | `redis` |
| `REDIS_PORT` | Puerto Redis | `6379` |
| `REDIS_PASSWORD` | Contraseña Redis | — |

---

## Ejecución Local

```bash
# 1. Clonar el repositorio
git clone https://github.com/leitoov/product-inventory-ai-service.git
cd product-inventory-ai-service

# 2. Levantar PostgreSQL con Docker Compose
docker compose up -d

# 3. Configurar variables de entorno
cp .env.example .env
# Editar .env con los valores correspondientes

# 4. Compilar y ejecutar
./mvnw clean spring-boot:run

# 5. Acceder a la documentación interactiva
open http://localhost:8080/swagger-ui.html
```