# ADR-009: Calidad, accesibilidad básica y evidencia reproducible de M9

- Estado: auditoría automática aprobada; pendiente de validación física.
- Fecha: 2026-09-26.
- Alcance: M9 de Flutter Android, 9 puntos de la matriz móvil.

## Contexto

M1 a M8 fueron aprobados y aportan 71 de 80 puntos. El bloque restante no
agrega una función operativa nueva: valida que el cliente Android sea
comprensible, comprobable y reproducible antes de declarar el 80 % comprometido.

La arquitectura real de Flutter usa controladores de sesión y sincronización,
repositorios/gateways y widgets de presentación. No existe una capa denominada
`ViewModel`; por tanto, la calidad se prueba en las capas que sí existen, sin
introducir una abstracción artificial solo para cumplir la matriz.

## Decisión

1. Probar controladores, repositorios y widgets críticos. Los widgets cubren
   inicio de sesión, error accesible, escala de texto al 140 % y el estado de
   sincronización pendiente.
2. Mantener accesibilidad básica verificable: encabezado semántico, etiquetas
   explícitas para controles con icono, avisos de estado mediante región viva,
   texto escalable y acciones con texto visible. No se depende solo de color o
   de un icono para comunicar un estado.
3. Generar exclusivamente APK **debug** `0.1.0+2`, verificable con
   `apksigner` en esquema v2 y hash SHA-256. No se presenta como APK de tienda
   ni como artefacto de producción.
4. Registrar el commit Git de referencia y si el árbol de trabajo está limpio
   o tiene cambios. Un hash de APK con árbol `DIRTY` sigue siendo rastreable,
   pero no se declarará falsamente asociado por completo a ese commit. La
   asociación final a un commit requiere que el propietario autorice y publique
   el cambio correspondiente.
5. Encadenar la auditoría M8 para que M9 no compense regresiones de seguridad,
   persistencia, privacidad o integración Android ya aprobadas.

## Consecuencias

El criterio técnico de M9 puede llegar a 80/80 solo si las regresiones,
análisis, pruebas, firma y hash aprueban. La puerta humana permanece separada:
un conductor debe ejecutar la APK en un Android físico, comprobar el flujo
principal y confirmar VERDE.

Permanecen fuera del 80 % comprometido: FCM, ejecución GPS en segundo plano,
fotos, mapas, sincronización offline avanzada, iOS, publicación en tienda,
firma release y observabilidad productiva.
