# ADR-001: PostgreSQL 16 local administrado con Docker Compose

- Estado: aceptada
- Fecha: 2026-08-28
- Alcance: Fase 1

## Contexto

El proyecto necesita desarrollar y demostrar persistencia real sin depender por
ahora de infraestructura administrada ni de una instalación externa. La base
debe poder recrearse, migrarse y probarse de forma consistente desde Visual
Studio Code en Windows.

## Decisión

Se usará la imagen oficial `postgres:16-alpine` mediante `compose.yaml`.
PostgreSQL será el único servicio que arranca por defecto. Backend y frontend se
habilitan con el perfil `app`. El volumen de Docker conserva los datos y Flyway
es el único mecanismo autorizado para evolucionar el esquema.

Las pruebas de integración usarán PostgreSQL efímero con Testcontainers. H2 no
se empleará como base principal ni para afirmar compatibilidad SQL.

Los datos de demostración de dos organizaciones se cargarán únicamente con el
perfil `dev`; nunca con `test` o `prod`.

## Consecuencias

Positivas:

- No se requiere instalar PostgreSQL ni un administrador de base en Windows.
- Desarrollo y CI prueban el mismo motor y versión mayor.
- El esquema puede reconstruirse desde cero y luego trasladarse, mediante las
  mismas migraciones, a PostgreSQL externo cuando se autorice.
- El volumen puede conservarse o eliminarse explícitamente según el ensayo.

Costos y límites:

- Docker Desktop debe estar iniciado para desarrollo y Testcontainers.
- El volumen local no es un respaldo ni ofrece alta disponibilidad.
- La etiqueta `16-alpine` recibe actualizaciones de parche; antes de una entrega
  productiva deberá registrarse y probarse una referencia inmutable.
- Credenciales en `.env` son aceptables solo para el equipo local y no sustituyen
  un gestor de secretos futuro.

## Alternativas descartadas

- H2: diferencias de dialecto, constraints, índices y comportamiento
  transaccional pueden ocultar defectos.
- PostgreSQL instalado en el host: aumenta divergencias de versión y pasos
  manuales.
- PostgreSQL administrado: aplazado por decisión del propietario del producto.

