# Términos de uso de ADMIN — Piloto académico de Ruta Fija

**Estado:** BORRADOR PARA REVISIÓN — NO APROBADO — NO VIGENTE  
**Versión de trabajo:** 0.1.0  
**Fecha de preparación:** 2026-10-14  
**Aplicación:** Ruta Fija V3.1, piloto académico  

> Este archivo no es una versión publicada ni debe usarse para registrar aceptación. Los campos marcados **[POR DEFINIR]** requieren decisión o información del responsable antes de aprobar el documento. No se presuponen responsables, obligaciones legales ni condiciones comerciales.

## 1. Identificación y alcance del piloto

Ruta Fija es el proyecto académico que se evalúa en este piloto. La identidad de la organización responsable, su representante y el canal responsable de consultas están **[POR DEFINIR]**.

El piloto comprende el acceso a Ruta Fija con las cuentas y funciones que se habiliten expresamente para la prueba académica. Los perfiles contemplados son `SUPER_ADMIN`, `ADMIN` y `CONDUCTOR`. Un `ADMIN` opera dentro de la organización que tiene asociada su cuenta; no obtiene atribuciones de `SUPER_ADMIN`.

El alcance de Ruta Fija V3.1 excluye funcionamiento offline y colas de sincronización. La lista exacta de funciones que se habilitarán para cada organización, el entorno de uso y las fechas del piloto están **[POR DEFINIR]**. La inclusión de una función en el proyecto no confirma por sí sola que esté habilitada o validada para el piloto.

## 2. Uso de la cuenta ADMIN

La cuenta `ADMIN` está destinada a gestionar los usuarios y la operación de su propia organización, dentro de las funciones habilitadas en el piloto. La cuenta no debe utilizarse para consultar o gestionar información de otra organización. La separación de organizaciones es una regla del producto que debe comprobarse antes de habilitar el piloto.

La administración podrá comprender, según la configuración autorizada, la gestión de cuentas de usuario, su activación o desactivación y la información operativa de la organización. La matriz final de acciones permitidas y la persona que autoriza cada alta inicial están **[POR DEFINIR]**.

Las reglas para crear, recuperar, suspender y retirar cuentas; custodiar credenciales; comunicar cambios de responsables; y atender cuentas comprometidas están **[POR DEFINIR]**. **Propuesta para revisión, no aprobada:** asignar cada cuenta a una sola persona. La recuperación de una cuenta `SUPER_ADMIN` no puede ser autorizada por un `ADMIN` subordinado.

## 3. Uso autorizado

El uso previsto se limita a la evaluación académica de Ruta Fija dentro del piloto y a las organizaciones, cuentas y funciones que se autoricen para esa prueba. No se ha definido en este borrador un uso comercial ni una condición de servicio de pago. Las condiciones comerciales no están incluidas y permanecen **[POR DEFINIR]**.

La operación offline y las colas de envío diferido quedan fuera del alcance del piloto. Los usos adicionales, las fuentes de datos permitidas y las restricciones de carga o disponibilidad están **[POR DEFINIR]**.

## 4. Información tratada y ubicación

El sistema contempla datos de cuenta (por ejemplo, correo, nombre, teléfono, rol y organización) y datos operativos asociados a las funciones habilitadas. La aplicación móvil incluye capacidades relacionadas con ubicación del conductor. El propósito preciso de cada dato, quién puede consultarlo, cuándo se obtiene la ubicación, la configuración efectiva del dispositivo y el procedimiento para detenerla están **[POR DEFINIR]**. **Control propuesto para revisión, no aprobado:** documentar estos puntos antes de usar datos reales del piloto.

La ubicación de las bases de datos, respaldos, registros técnicos y demás copias; los proveedores que puedan almacenarlos; y cualquier transferencia entre servicios o ubicaciones están **[POR DEFINIR]**. También están **[POR DEFINIR]** los periodos de conservación, la eliminación o anonimización y la gestión de solicitudes relacionadas con los datos.

Para el envío de códigos de verificación durante el piloto se aprobó provisionalmente Gmail SMTP, con la cuenta emisora controlada por la organización y sin dominio propio. Google opera el dominio `gmail.com`. El uso está limitado a la prueba académica autorizada y a la configuración de correo aprobada; no define por sí solo dónde se almacenan los demás datos del sistema ni las condiciones de conservación del proveedor.

## 5. Contacto y atención

- Responsable de la organización y del piloto: **[POR DEFINIR]**
- Canal para consultas sobre cuentas y operación: **[POR DEFINIR]**
- Canal para comunicar incidentes o problemas de acceso: **[POR DEFINIR]**
- Canal para consultas sobre datos y ubicación: **[POR DEFINIR]**

Estos canales requieren decisión antes de aprobar la versión final, o una decisión expresa de excluir alguno de ellos.

## 6. Aceptación de la versión aprobada

La aceptación de estos términos solo podrá habilitarse cuando exista una versión aprobada e identificada. La acción deberá ser expresa, iniciada por el `ADMIN` y no marcada de forma predeterminada. Iniciar sesión, usar otra función o mantener abierta una sesión no constituye aceptación automática.

El registro de aceptación deberá vincular, como mínimo, el identificador de la cuenta `ADMIN`, la organización asociada, el identificador y versión exactos del documento, la huella criptográfica del contenido aceptado y la fecha y hora del servidor. Mientras este borrador no sea aprobado y publicado, no se deberá registrar una aceptación de términos.

La versión aprobada `1.0.0` se identificará y conservará sin sobrescritura. Cualquier cambio posterior requerirá una versión nueva, aprobada e identificable; la política para asignar versiones posteriores está **[POR DEFINIR]**.

## 7. Decisiones e información pendientes para revisión

1. Nombre legal o institucional de la organización responsable, identificación, domicilio si corresponde y representante autorizado.
2. Responsable operativo del piloto y canales de contacto para soporte, incidentes y consultas sobre datos.
3. Fechas, organizaciones participantes, entorno y funciones concretas habilitadas.
4. Reglas finales de administración y ciclo de vida de cuentas `ADMIN` y de usuarios de la organización.
5. Datos tratados, propósitos, personas con acceso y configuración efectiva de la ubicación del conductor.
6. Ubicación de almacenamiento, respaldos y registros; proveedores involucrados; periodos de conservación y procedimiento de eliminación.
7. Tratamiento operativo de una cuenta comprometida y pasos de suspensión o recuperación.
8. Cualquier condición comercial, de disponibilidad o soporte que se pretenda incorporar. Ninguna se presume en este borrador.
9. Revisión y aprobación expresa del texto antes de generar la versión inmutable `1.0.0`.

