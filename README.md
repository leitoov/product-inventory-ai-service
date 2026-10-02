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
- [Endpoints Principales](#endpoints-principales)
- [Configuración y Entorno](#configuración-y-entorno)
- [Ejecución Local](#ejecución-local)
- [Roadmap](#roadmap)

---

## Descripción

Este servicio backend expone una API REST que permite:

- **Gestión de productos**: alta, edición y visualización del catálogo con campos como SKU, imagen, descripción, precio, stock y marca de tiempo de última actualización.
- **Dashboard de métricas**: análisis de ventas con resúmenes, tendencias e insights generados por un modelo de IA intercambiable.
- **Autenticación**: login seguro con tokens para proteger el acceso al dashboard y a las operaciones de escritura.

---

## Stack Tecnológico

| Capa | Tecnología |
| :--- | :--- |
| Lenguaje | Java 21 (LTS) |
| Framework | Spring Boot 3.x |
| Persistencia | Spring Data JPA / Hibernate |
| Base de Datos | PostgreSQL |
| Seguridad | Spring Security |
| Autenticación | JWT (JSON Web Tokens) — reemplazable por OAuth2/Session |
| IA (abstracción) | Interfaz propia — el proveedor se configura por entorno |
| Documentación API | Springdoc OpenAPI / Swagger UI |
| Build | Maven 3.9+ |
| Runtime | JDK 21 |

---

## Arquitectura

### Hexagonal
Arquitectura Hexagonal (Ports & Adapters)

**Justificación**: dado que el proveedor de IA debe poder cambiarse sin tocar el dominio y que habrá múltiples adaptadores (REST, base de datos, IA, autenticación).

### Estructura de Paquetes (Hexagonal)

```
com.company.inventory
├── domain/                        # Núcleo — sin dependencias de frameworks
│   ├── model/                     # Entidades y Value Objects del dominio
│   └── port/
│       ├── in/                    # Casos de uso (interfaces que llaman los controladores)
│       └── out/                   # Puertos de salida (repo, IA, storage de imágenes)
│
├── application/                   # Orquestación de casos de uso
│   └── service/                   # Implementaciones de los puertos de entrada
│
└── infrastructure/                # Adaptadores (detalles técnicos)
    ├── adapter/
    │   ├── in/
    │   │   └── rest/              # Controladores REST + DTOs de request/response
    │   └── out/
    │       ├── persistence/       # Entidades JPA, repositorios Spring Data
    │       ├── ai/                # Adaptador de IA (implementa puerto genérico)
    │       └── storage/           # Almacenamiento de imágenes (local, S3, etc.)
    └── config/                    # Beans de Spring, seguridad, Swagger
```

---

## Módulos Principales

### 1. Gestión de Productos (`/api/products`)
- Crear, actualizar y consultar productos del catálogo.
- Búsqueda y filtrado (por SKU, nombre, categoría, stock bajo).
- Subida de imagen asociada al producto.

### 2. Catálogo (`/api/catalog`)
- Vista pública/privada del catálogo paginado.
- Edición inline de precio, descripción y stock.

### 3. Métricas de Ventas (`/api/metrics`)
- Totales de ventas por período.
- Productos más vendidos / con menor rotación.
- Resumen e insights generados por el servicio de IA (proveedor configurable).

### 4. Autenticación (`/api/auth`)
- Login con usuario y contraseña → devuelve JWT.
- Refresh de token.
- Roles: `ADMIN` (gestión completa), `VIEWER` (solo lectura de catálogo y métricas).

---

## Modelo de Datos

### Entidad `Product`

| Campo | Tipo | Descripción |
| :--- | :--- | :--- |
| `id` | `UUID` | Identificador único |
| `sku` | `VARCHAR(100)` | Código de producto único |
| `name` | `VARCHAR(255)` | Nombre del producto |
| `description` | `TEXT` | Descripción detallada |
| `imageUrl` | `VARCHAR(500)` | URL o ruta de la imagen |
| `price` | `DECIMAL(12,2)` | Precio de venta |
| `stock` | `INTEGER` | Unidades disponibles |
| `lastUpdatedAt` | `TIMESTAMP WITH TIME ZONE` | Fecha y hora de la última modificación |
| `createdAt` | `TIMESTAMP WITH TIME ZONE` | Fecha de creación |

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

- **Mecanismo**: JWT Bearer Token enviado en el header `Authorization`.
- **Generación**: al hacer `POST /api/auth/login` con credenciales válidas, se retorna un `accessToken` (corta duración) y un `refreshToken`.
- **Protección de rutas**: endpoints de escritura (`POST`, `PUT`, `DELETE`) requieren rol `ADMIN`. Los endpoints de lectura del catálogo son públicos o requieren `VIEWER`.
- **Contraseñas**: almacenadas con `BCryptPasswordEncoder`.
- **Flexibilidad**: el módulo de seguridad está desacoplado. Puede migrarse a OAuth2 / Spring Authorization Server sin afectar el dominio.

---

## Integración con IA

El servicio de IA está abstraído detrás de un **puerto de salida** (`AiAnalyticsPort`). Ningún adaptador de IA menciona un proveedor concreto en la lógica de negocio.

```java
// Puerto genérico en el dominio
public interface AiAnalyticsPort {
    String generateSalesSummary(SalesData data);
    List<String> generateProductInsights(List<Product> products);
}
```

El adaptador concreto se selecciona por configuración (`application.yml`) sin cambiar una sola línea del dominio. Se pueden registrar múltiples adaptadores y activar el deseado con un profile de Spring (`ai-provider-a`, `ai-provider-b`, etc.).

---

## Endpoints Principales

| Método | Ruta | Descripción | Rol requerido |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/auth/login` | Login — devuelve JWT | Público |
| `POST` | `/api/auth/refresh` | Renovar access token | Autenticado |
| `GET` | `/api/products` | Listar productos (paginado) | `VIEWER` / `ADMIN` |
| `POST` | `/api/products` | Crear producto | `ADMIN` |
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
- Docker o PostgreSQL instalado localmente
- Maven 3.9+

### Variables de Entorno

Crear un archivo `.env` en la raíz o configurar en `application.yml`:

| Variable | Descripción | Ejemplo |
| :--- | :--- | :--- |
| `SERVER_PORT` | Puerto de la API | `8080` |
| `DB_URL` | URL JDBC de PostgreSQL | `jdbc:postgresql://localhost:5432/inventory_db` |
| `DB_USERNAME` | Usuario de la base de datos | `postgres` |
| `DB_PASSWORD` | Contraseña de la base de datos | `secret` |
| `JWT_SECRET` | Clave secreta para firmar JWTs (mín. 256 bits) | `changeme-in-production` |
| `JWT_EXPIRATION_MS` | Duración del access token en ms | `3600000` |
| `AI_PROVIDER_ENDPOINT` | URL base del proveedor de IA | `https://api.example.com/v1` |
| `AI_PROVIDER_API_KEY` | API Key del proveedor de IA | — |
| `AI_PROVIDER_MODEL` | Modelo a utilizar | `model-name` |
| `IMAGE_STORAGE_PATH` | Ruta local para imágenes | `./uploads` |

---

## Ejecución Local

```bash
# 1. Clonar el repositorio
git clone https://github.com/leitoov/product-inventory-ai-service.git
cd product-inventory-ai-service

# 2. Levantar PostgreSQL con Docker
docker run --name inventory-db \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=secret \
  -e POSTGRES_DB=inventory_db \
  -p 5432:5432 -d postgres:16

# 3. Configurar variables de entorno
cp .env.example .env
# Editar .env con los valores correspondientes

# 4. Compilar y ejecutar
./mvnw clean spring-boot:run

# 5. Acceder a la documentación interactiva
open http://localhost:8080/swagger-ui.html
```

---

