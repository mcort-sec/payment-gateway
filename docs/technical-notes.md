# Notas Técnicas — Payment Gateway API

Este documento detalla los aspectos técnicos, decisiones de diseño, invariantes y garantías del sistema.

---

## 1. Arquitectura y Principios de Diseño

El proyecto implementa **Clean Architecture / Ports & Adapters**:

- **Dominio (`domain`)**: Entidades (`Payment`, `Refund`, `Merchant`, `Processor`), Value Objects (`Currency`, `PaymentStatus`, `RefundStatus`) y excepciones de dominio. Código Java puro sin dependencias de frameworks, anotaciones de Spring ni JPA.
- **Aplicación (`application`)**: Casos de uso (`CreatePaymentUseCase`, `ApprovePaymentUseCase`, `CreateRefundUseCase`, `ListPaymentsUseCase`, etc.), comandos de entrada, DTOs y puertos (`PaymentRepositoryPort`, `RefundRepositoryPort`, `ApiKeyHasherPort`, etc.). Desacoplado de la infraestructura.
- **Infraestructura (`infrastructure`)**: Adaptadores de persistencia JPA/Hibernate, integración de seguridad con Spring Security, implementación del hashing criptográfico SHA-256, migraciones Flyway y configuración de beans en `ApplicationConfig`.
- **Punto de Entrada (`entrypoint`)**: Controladores REST Spring MVC (`PaymentController`, `RefundController`, `ProcessorCallbackController`), DTOs de petición/respuesta y `GlobalExceptionHandler`.

---

## 2. Autenticación y Autorización

La API implementa autenticación basada en API Keys suministradas mediante la cabecera `Authorization: Bearer <API_KEY>`.

### Formato de API Keys
- **Merchant**: `pg_test_<12_caracteres_base62>_<43_caracteres_base64url>` (Longitud total: 64 caracteres).
- **Processor**: `pg_proc_test_<12_caracteres_base62>_<43_caracteres_base64url>` (Longitud total: 69 caracteres).

### Mecanismo de Verificación
1. **Búsqueda Indexada y Verificación de Hash**: El prefijo de la API key se utiliza para realizar una búsqueda indexada de la credencial en base de datos (`uq_api_credentials_key_prefix` o `uq_processor_credentials_key_prefix`) y posteriormente se verifica la clave suministrada contra el hash SHA-256 almacenado.
2. **Protección de Secretos**: La API key en plaintext nunca se persiste en base de datos ni se emite en logs del sistema.
3. **Manejo de Roles**:
   - Peticiones sin credenciales o con credenciales inválidas retornan `401 Unauthorized`.
   - Claves de Merchant intentando acceder a endpoints de Processor retornan `403 Forbidden`.
   - Claves de Processor intentando acceder a endpoints de Merchant retornan `403 Forbidden`.

### Desacoplamiento de Autenticación y Estado de Negocio
- La autenticación (validez de la credencial) y las reglas de estado de negocio del actor son responsabilidades separadas.
- Un actor suspendido (`SUSPENDED`) puede conservar una credencial activa válida a nivel de autenticación:
  - **Merchant SUSPENDED**:
    - No puede crear nuevos pagos (`POST /payments` $\to$ `403 Forbidden`).
    - Puede consultar y listar sus pagos y reembolsos propios (`GET /payments`, `GET /payments/{id}`, `GET /refunds`, `GET /refunds/{id}`).
    - Puede cancelar sus propios pagos que permanezcan en estado `PENDING` (`POST /payments/{id}/cancel`).
    - Puede solicitar reembolsos sobre sus propios pagos en estado `APPROVED` (`POST /payments/{paymentId}/refunds`).
  - **Processor SUSPENDED**:
    - Se autentica correctamente, pero cualquier operación de procesamiento (`approve`, `decline`) retorna `403 Forbidden`.
  - **Processor ACTIVE**: Requerido para procesar operaciones de aprobación o rechazo de pagos y reembolsos.

---

## 3. Aislamiento Multi-Tenant

- Cada comercio opera en un espacio aislado identificado por su `merchantId`.
- **Consultas Tenant-Safe**: Todas las consultas (`GET /payments/{id}`, `GET /refunds/{id}`, `GET /payments`, `GET /refunds`) filtran estrictamente por el identificador del recurso y el `merchantId` autenticado.
- **Mitigación de Enumeración**: Si un comercio intenta acceder a un recurso inexistente o perteneciente a otro comercio, la API responde uniformemente con `404 Not Found`.

---

## 4. Idempotencia y Manejo de Replays

Los endpoints de creación (`POST /payments`, `POST /payments/{paymentId}/refunds`) requieren la cabecera `Idempotency-Key`.

1. **Scope de Idempotencia**:
   - Pagos: `(merchantId, idempotencyKey)`
   - Reembolsos: `(merchantId, idempotencyKey)`
2. **Replay Idempotente**:
   - Si se recibe una petición con una clave ya procesada y exactamente el mismo payload (mismo `amount` y `currency` para pagos; mismo `paymentId` y `amount` para reembolsos), la API responde con `201 Created` retornando el recurso existente.
   - La repetición idempotente permite al cliente reintentar peticiones de forma controlada. El backend no implementa mecanismos automáticos de reintento.
3. **Detección de Conflicto de Payload**: Si se reutiliza una clave existente con un payload diferente, la API rechaza la petición retornando `409 Conflict` (`IdempotencyConflictException`).

---

## 5. Control de Concurrencia y Bloqueos

El gateway aplica estrategias diferenciadas de concurrencia según la naturaleza de la operación:

### Bloqueo Optimista (`@Version`)
- Implementado en las tablas `payments` y `refunds`.
- Protege las transiciones de estado (`approve`, `decline`, `cancel`) ante operaciones concurrentes sobre el mismo registro.
- Si dos procesos intentan modificar el estado simultáneamente, la colisión es interceptada y mapeada a `409 Conflict`.

### Bloqueo Pesimista de Escritura (`PESSIMISTIC_WRITE`)
- Aplicado sobre la fila del `Payment` durante la creación de un `Refund` (`CreateRefundUseCase`).
- Ejecuta `SELECT ... FOR UPDATE` sobre el pago padre antes de consultar los reembolsos existentes y calcular la capacidad disponible.
- Protege la capacidad reembolsable acumulada serializando solicitudes de reembolso concurrentes para el mismo pago, previniendo condiciones de carrera TOCTOU (Time-Of-Check to Time-Of-Use) sin requerir mutaciones artificiales ni incrementos forzados de versión en el `Payment`.

---

## 6. Invariante y Capacidad de Reembolsos

Un `Refund` es un agregado independiente de `Payment` y solo puede solicitarse sobre un pago en estado `APPROVED`.

### Reglas de Capacidad
- La capacidad reservada se define como:
  $$\text{reservedRefundAmount} = \sum \text{Refunds}_{(\text{PENDING} \lor \text{APPROVED})}$$
- La capacidad reembolsable disponible se calcula como:
  $$\text{availableRefundableAmount} = \text{payment.amount} - \text{reservedRefundAmount}$$
- Se mantiene estrictamente la invariante:
  $$\sum \text{Refunds}_{(\text{PENDING} \lor \text{APPROVED})} \le \text{payment.amount}$$
- Si $\text{requestedAmount} > \text{availableRefundableAmount}$, la solicitud se rechaza con `409 Conflict` (`RefundAmountExceedsAvailableException`).
- Los reembolsos en estado `DECLINED` liberan la capacidad reservada automáticamente.
- Un reembolso aprobado (`APPROVED`) no altera el estado `APPROVED` del `Payment` padre.
- La API maneja los montos (`amount`) como valores numéricos enteros (`long`) sin asumir escalas fijas de unidades monetarias menores.

---

## 7. Paginación de Historial

Los endpoints de listado (`GET /payments`, `GET /refunds`) implementan paginación offset con orden determinista por `createdAt DESC, id DESC`:

- **Capa de Aplicación**: Modela la paginación mediante `PageQuery(page, size)` y `PageResult<T>`.
- **Capa de Infraestructura**: Traduce la consulta a `PageRequest` de Spring Data JPA.
- **Criterio de Orden**: Ordena por `createdAt DESC`, empleando el UUID `id DESC` únicamente como desempate determinista sin semántica temporal. No garantiza snapshot stability ante inserciones concurrentes.
- **Parámetros y Validaciones**:
  - `page` (0-indexed, por defecto `0`): valores negativos retornan `400 Bad Request`.
  - `size` (por defecto `20`, máximo `100`): valores $\le 0$ o $> 100$ retornan `400 Bad Request`.

---

## 8. Contrato de Errores REST

Todas las respuestas de error siguen una estructura JSON uniforme:

```json
{
  "timestamp": "2026-09-30T10:00:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "Refund amount 2000 exceeds available refundable amount 1000",
  "path": "/payments/3fa85f64-5717-4562-b3fc-2c963f66afa6/refunds"
}
```

### Mapeo de Códigos de Estado HTTP

| Código HTTP | Categoría | Excepciones Mapeadas |
|---|---|---|
| **`400 Bad Request`** | Parámetros inválidos / Validación | `MethodArgumentNotValidException`, `HandlerMethodValidationException`, `ConstraintViolationException`, `MethodArgumentTypeMismatchException`, `HttpMessageNotReadableException`, `MissingRequestHeaderException`, `InvalidPaginationException`, `InvalidPaymentException`, `InvalidRefundException`, `InvalidProcessorException`, `InvalidProcessorCredentialException` |
| **`401 Unauthorized`** | Autenticación ausente o inválida | `ApiKeyAuthenticationEntryPoint`, `AuthenticationException` |
| **`403 Forbidden`** | Permisos insuficientes / Actor suspendido | `ApiAccessDeniedHandler`, `MerchantSuspendedException`, `ProcessorSuspendedException` |
| **`404 Not Found`** | Recurso no encontrado / Cross-tenant | `PaymentNotFoundException`, `RefundNotFoundException`, `MerchantNotFoundException`, `ProcessorNotFoundException` |
| **`409 Conflict`** | Conflicto de estado o idempotencia | `IdempotencyConflictException`, `RefundAmountExceedsAvailableException`, `InvalidPaymentStateException`, `InvalidRefundStateException`, `PaymentConcurrentModificationException`, `RefundConcurrentModificationException` |

---

## 9. Historial de Migraciones Flyway

El esquema relacional evoluciona secuencialmente en `src/main/resources/db/migration`:

1. `V1__create_payments_table.sql`: Tabla inicial de pagos con constraints de monto positivo e idempotencia.
2. `V2__add_version_to_payments.sql`: Columna `version` para bloqueo optimista.
3. `V3__rename_customer_to_merchant.sql`: Renombramiento de columna `customer_id` a `merchant_id`.
4. `V4__create_merchants_and_add_payment_fk.sql`: Tabla `merchants` y clave foránea en `payments`.
5. `V5__create_api_credentials.sql`: Tabla `api_credentials` con índice único por `key_prefix`.
6. `V6__create_processors_and_credentials.sql`: Tablas `processors` y `processor_credentials`.
7. `V7__create_refunds.sql`: Tabla `refunds` con versión, claves foráneas e índice por `payment_id`.

---

## 10. Decisiones de Diseño Clave

1. **Casos de Uso POJO**: Las clases en `application.usecase` son clases Java puras sin anotaciones `@Service` ni `@Component`. Se instancian explícitamente en `ApplicationConfig`, garantizando desacoplamiento del framework.
2. **Montos Enteros (`long`)**: Los montos monetarios se almacenan y procesan como enteros para evitar imprecisiones de coma flotante.
3. **Hashing Determinista**: Las credenciales en texto plano nunca se almacenan. La búsqueda indexada por prefijo optimiza el lookup y permite validar la clave contra el hash SHA-256 almacenado.
4. **Modelado de Procesamiento**: El procesamiento de un pago se modela como una operación separada ejecutada por un Processor mediante peticiones HTTP independientes.
