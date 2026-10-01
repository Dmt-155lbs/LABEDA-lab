# Laboratorio 1: RabbitMQ con LOGISTPULSE

## Objetivo

Levantar RabbitMQ y dos aplicaciones Spring Boot en contenedores. `inventory-service` recibe una lectura de stock por HTTP. Si el stock disponible es menor o igual al mínimo, publica el evento `StockoutRiskDetected`. `recommendation-service` consume el evento desde una cola y registra una recomendación pendiente. El productor no invoca al consumidor directamente.

## Requisitos

- Docker Desktop instalado y abierto, o Docker Engine con Docker Compose v2.
- Conexión a Internet la primera vez para descargar imágenes y dependencias.
- Puertos locales disponibles: 5672, 8080 y 15672.

No necesitas instalar Java ni Maven en tu máquina: Docker construye las aplicaciones con Java 21.

## 1. Iniciar el laboratorio

Abre una terminal en la carpeta que contiene `docker-compose.yml` y ejecuta:

```bash
docker compose up --build
```

Espera hasta que aparezcan los logs de inicio de `inventory-service` y `recommendation-service`. La primera compilación puede tardar unos minutos.

## 2. Abrir RabbitMQ

En el navegador, visita `http://localhost:15672`.

- Usuario: `lab`
- Clave: `lab123`

En **Exchanges** debe aparecer `logistpulse.events`. En **Queues and Streams** debe aparecer `recommendation.stockout-risk.q`.

## 3. Publicar un evento de riesgo

Envía esta petición desde una segunda terminal:

```bash
curl -i -X POST http://localhost:8080/api/inventory/stock \
  -H 'Content-Type: application/json' \
  -d '{"sku":"CAF-500","location":"QUITO-CENTRO","available":4,"minimum":10}'
```

La respuesta debería indicar `riskDetected: true` y devolver un `eventId`. En la terminal de Compose busca `RECOMENDACION_PENDIENTE` en los logs del consumidor:

```bash
docker compose logs -f recommendation-service
```

En RabbitMQ, la cola puede volver rápidamente a **Ready = 0**, porque el consumidor toma y confirma el mensaje.

## 4. Probar el caso sin riesgo

```bash
curl -i -X POST http://localhost:8080/api/inventory/stock \
  -H 'Content-Type: application/json' \
  -d '{"sku":"CAF-500","location":"QUITO-CENTRO","available":15,"minimum":10}'
```

La respuesta indicará `riskDetected: false`. No se publica ningún evento.

## 5. Ver que la cola conserva mensajes

Detén solo el consumidor con `Ctrl+C` en otra terminal o ejecuta:

```bash
docker compose stop recommendation-service
```

Publica dos o tres lecturas de stock con `available` menor o igual a `minimum`. En RabbitMQ revisa la cola: los mensajes deben quedar en **Ready**. Reinicia el consumidor:

```bash
docker compose start recommendation-service
```

Los mensajes se procesarán y la cantidad **Ready** bajará. Esto muestra el desacoplamiento temporal entre productor y consumidor.

## Flujo implementado

```text
Cliente --HTTP--> inventory-service --publica evento--> Exchange topic
                                                        |
                                                        v
                                  Queue recommendation.stockout-risk.q
                                                        |
                                                        v
                                         recommendation-service
```

- Exchange: `logistpulse.events` (tipo `topic`, durable).
- Routing key: `inventory.stockout-risk.detected`.
- Cola durable: `recommendation.stockout-risk.q`.
- Evento: `StockoutRiskDetected` con identificador, fecha, SKU, local y niveles de stock.

## 6. Detener y limpiar

```bash
docker compose down
```

Para borrar también el volumen de RabbitMQ y reiniciar el estado del laboratorio:

```bash
docker compose down -v
```

## Qué demuestra y qué falta para producción

Este ejercicio demuestra publicación, enrutamiento, consumo y retención temporal de mensajes en una cola durable. Aún no implementa base de datos, Transactional Outbox, reintentos configurados, dead-letter queue, seguridad de credenciales ni idempotencia persistente. El siguiente paso didáctico es provocar un fallo en el consumidor y añadir reintento/DLQ; para integrar cambios de base de datos y eventos, se agregaría Outbox.

Las credenciales incluidas son solo para el laboratorio local. No las reutilices en un entorno compartido o productivo.

## Ejecutarlo en GitHub Codespaces

1. Sube el contenido de esta carpeta a un repositorio de GitHub. La carpeta `.devcontainer` debe quedar en la raíz del repositorio.
2. En GitHub, selecciona **Code → Codespaces → Create codespace on main** para crear un Codespace nuevo después de subir `.devcontainer/devcontainer.json`. Si ya existe uno, abre la paleta con `Shift+Command+P` (Mac) o `Ctrl+Shift+P` (Windows/Linux) y ejecuta **Codespaces: Rebuild Container** para que tome la configuración del repositorio. La configuración instala Docker CLI y conecta el Codespace con el daemon de Docker del host.
3. Abre la terminal integrada en la raíz del proyecto y verifica Docker:

   ```bash
   docker --version
   docker compose version
   ```

4. Levanta RabbitMQ y las dos aplicaciones:

   ```bash
   docker compose up --build -d
   docker compose ps
   ```

5. En **Ports**, Codespaces mostrará los puertos `8080` y `15672`. Déjalos como **Private**. Abre el puerto `15672` para la consola RabbitMQ e inicia sesión con `lab` / `lab123`.
6. Prueba el API desde la terminal del Codespace (no hace falta usar la URL reenviada para esta prueba):

   ```bash
   curl -i -X POST http://localhost:8080/api/inventory/stock \
     -H 'Content-Type: application/json' \
     -d '{"sku":"CAF-500","location":"QUITO-CENTRO","available":4,"minimum":10}'
   ```

7. Para ver que el consumidor recibió el evento:

   ```bash
   docker compose logs -f recommendation-service
   ```

8. Para detener el laboratorio:

   ```bash
   docker compose down
   ```

Si `docker --version` responde `command not found`, el Codespace todavía no aplicó `.devcontainer/devcontainer.json`: crea uno nuevo desde el repositorio o ejecuta **Codespaces: Rebuild Container**. Si el rebuild falla, abre **View Creation Log** y revisa el primer error de configuración. Una vez que `docker compose version` funcione, confirma que el terminal está en la carpeta que contiene `docker-compose.yml`. RabbitMQ y las aplicaciones se comunican usando el nombre `rabbitmq` dentro de la red de Compose; desde el navegador, RabbitMQ se abre por el puerto reenviado `15672`.


## Solución si recommendation-service se cierra por JsonMapper

Si los logs muestran `ClassNotFoundException: com.fasterxml.jackson.databind.json.JsonMapper`, confirma que `recommendation-service/pom.xml` incluye `spring-boot-starter-json`. Luego reconstruye y reinicia ese servicio:

```bash
docker compose up --build -d recommendation-service
docker compose ps
docker compose logs --tail=60 recommendation-service
```

La dependencia aporta Jackson, que el convertidor JSON de RabbitMQ necesita para deserializar el evento.


## Diagnóstico de conexión en Codespaces

Si los tres contenedores están en la misma red, pero desde `inventory-service` la conexión TCP a `rabbitmq:5672` expira, el contenedor RabbitMQ está configurado con listeners IPv4 explícitos mediante `rabbitmq/rabbitmq.conf`. Después de aplicar cambios de configuración, recrea los servicios y prueba el puerto AMQP:

```bash
docker compose down
docker compose up --build -d
docker compose exec rabbitmq rabbitmq-diagnostics listeners
docker compose exec inventory-service sh -c 'busybox nc -z -v -w 3 rabbitmq 5672'
```

La prueba de `nc` debe indicar que el puerto está abierto.

## Preparar el repositorio nuevo

Este ZIP coloca `.devcontainer`, `docker-compose.yml` y los servicios directamente en su raíz. Extrae su contenido y sube esos archivos y carpetas a la raíz del repositorio nuevo. No dejes todo dentro de una carpeta intermedia llamada `rabbitmq-logistpulse-lab`, porque Codespaces busca `.devcontainer/devcontainer.json` en la raíz del repositorio.
