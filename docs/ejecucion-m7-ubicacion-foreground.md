# M7 — Ubicación manual en primer plano

## Alcance

M7 permite al conductor enviar voluntariamente un único punto vigente mientras
la aplicación está abierta. No crea seguimiento continuo, historial, mapa ni
actividad en segundo plano.

El flujo respeta este orden:

1. El conductor abre **Perfil → Gestionar ubicación**.
2. Lee y confirma el consentimiento dentro de Ruta Fija.
3. Android solicita el permiso de ubicación únicamente en ese momento.
4. La aplicación obtiene un único punto, con máximo 15 segundos de espera, y
   lo envía a la API móvil autenticada.
5. El servidor reemplaza el punto vigente previo. La aplicación no muestra ni
   guarda las coordenadas.

La actualización solo se habilita para disponibilidad `DISPONIBLE` o
`EN_SERVICIO`. El backend vuelve a validar esa regla antes de conservar el
punto.

## Controles de privacidad

- El consentimiento funcional y el permiso Android son independientes.
- Si Android rechaza el permiso, no se activa el consentimiento del servidor ni
  se transmite un punto.
- **Eliminar ubicación vigente** borra el punto sin desactivar el
  consentimiento.
- **Desactivar ubicación** retira el consentimiento y borra el punto vigente.
- No hay coordenadas en la interfaz, registros de aplicación, auditoría,
  reportes ni eventos de tiempo real.

## Validación técnica

Desde la raíz del repositorio:

~~~powershell
.\scripts\Test-RutaFijaM7ForegroundLocation.ps1
~~~

La salida aprobada comienza con:

~~~text
M7_FOREGROUND_LOCATION_AUDIT=PASS m7=8/80 total=66/80 ...
~~~

La auditoría vuelve a ejecutar la regresión móvil M1–M6, verifica los permisos
del manifiesto, prohíbe capacidades de seguimiento continuo y comprueba las
pruebas de consentimiento, rechazo, eliminación y revocación.

## Prueba manual en Android físico

Con el backend iniciado y el teléfono conectado por USB:

~~~powershell
Set-Location 'D:\UTP\CICLO X UTP\INTEGRADOR I\APF1\solucion_1_ruta_fija\mobile'
$env:Path = 'D:\dev\flutter\bin;D:\Android\Sdk\platform-tools;' + $env:Path
adb reverse tcp:8080 tcp:8080
flutter run --debug --dart-define=RF_API_BASE_URL=http://127.0.0.1:8080/api/v1
~~~

1. Inicie sesión con un conductor activo que esté `DISPONIBLE`.
2. Abra **Perfil → Gestionar ubicación** y pulse **Autorizar y actualizar
   ubicación**.
3. Compruebe que el permiso Android aparece solo después de la confirmación de
   la aplicación.
4. Autorice mientras usa la aplicación y espere la confirmación de actualización.
5. Pulse **Eliminar ubicación vigente** y confirme que la acción no muestra
   coordenadas ni cambia el consentimiento.
6. Pulse **Desactivar ubicación**, confirme y vuelva a entrar: debe mostrarse
   `Consentimiento pendiente`.
7. Pruebe rechazar el permiso Android: la aplicación debe informar que no se
   envió ningún punto y el consentimiento debe continuar pendiente.

La fase solo se declara `VERDE — M7` tras esa prueba manual.
