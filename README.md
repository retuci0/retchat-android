# retchat

retchat es un sistema de chat casero que hice porque quería algo sencillo, que no necesitara base de 
datos, que funcionara en un VPS barato y que me permitiera hablar desde el móvil sin entregar mis mensajes a un tercero.

no pretende ser Matrix ni Discord. es un pequeño protocolo + un servidor + un cliente Android. puedes 
ejecutar el servidor en cualquier máquina Linux, conectarte con la app de Android y tener salas cifradas con apodos.


## lo que funciona

- **salas** - `/join sala` crea o cambia de sala. cada sala tiene su propia lista de usuarios.
- **apodos** - `/nick nombre` cambia lo que ven los demás. los nombres son únicos por sala (no puede 
  haber dos "Dani" en la misma sala).
- **comandos** - `/rooms` lista todas las salas activas, `/who` lista los usuarios de tu sala actual.
- **cifrado** - Diffie‑Hellman de 2048 bits (grupo 14, RFC 3526) para acordar una clave, luego 
  SHA‑256 para derivar una clave de 32 bytes. cada mensaje se cifra con XOR usando un flujo de 
  claves generado con HMAC‑SHA256(contador), y cada mensaje lleva su propio HMAC. los contadores 
  evitan la repetición. no es "quantum-resistant", pero sirve para charlar con amigos.
- **persistencia** - la app recuerda tu último apodo y tu última sala. al reconectar vuelves a 
  donde estabas.


## lo que (todavía) no funciona

- envío de archivos
- mensajes privados (solo difusión por sala)
- historial de mensajes (si te desconectas y vuelves, no ves los mensajes antiguos)


## compilar el servidor

Necesitas OpenSSL y un compilador de C (gcc vale).

```bash
git clone https://github.com/retucio/retchat
cd retchat
make
```

## resumen del protocolo (para curiosos)

1. **handshake TCP** - el cliente se conecta.
2. **intercambio DH** - el servidor envía su clave pública (longitud + bytes), el cliente responde 
   con su clave pública. ambos calculan el secreto compartido y luego SHA‑256(secreto_compartido) 
   -> clave_cifrado de 32 bytes.
3. **estructura del mensaje** - para cada mensaje (incluyendo el terminador nulo):
   - 32 bytes de HMAC(clave_cifrado, texto_cifrado)
   - uint16 (big‑endian) longitud del texto_cifrado
   - texto_cifrado = texto_plano XOR flujo(clave_cifrado, contador)
   - el flujo se genera como HMAC(clave_cifrado, contador en little‑endian) repetido (encadenando 
     hashes si se necesitan más de 32 bytes)
4. **comandos** - el texto plano empieza con '/' (ej. `/nick dani`). el servidor lo interpreta y 
   responde con mensajes `[SERVER] ...`.
5. **difusión** - los mensajes normales (que no empiezan con '/') se envían a todos los de la misma 
   sala con el formato `[apodo] mensaje`.

los contadores empiezan en 0 para cada sentido y se incrementan tras cada mensaje.


## ¿por qué este cifrado tan raro?

no quería meter TLS para un chat sencillo - los certificados son un fastidio para servicios 
autohosteados. el DH en banda proporciona secreto perfecto hacia adelante (claves nuevas por 
sesión). el HMAC evita manipulaciones. el XOR con una función hash claveada es básicamente un 
cifrador de flujo. no es estándar, pero es divertido y funciona.


## contribuciones / notas

esto es un proyecto personal. el código está aquí por si a alguien le resulta útil o quiere aprender 
de él.

licencia: MIT (o lo que sea, realmente me la pela). tan solo no finjas que lo has hecho tú todo.