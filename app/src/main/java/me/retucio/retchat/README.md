# retchat

retchat is a hobby chat system i built because i wanted something simple that doesn't need a database, runs on a cheap vps, and lets me talk from my phone without handing my messages to a third party.

it's not trying to be matrix or discord. it's a small protocol + a server + an android client. you can run the server on any linux machine, connect with the android app, and have encrypted rooms with nicknames.


## what works

- **rooms** - `/join room` creates or switches rooms. each room has its own user list.
- **nicknames** - `/nick name` changes what people see. names are unique per room (no two "bob" in the same room).
- **commands** - `/rooms` lists all active rooms, `/who` lists users in your current room.
- **encryption** - 2048-bit diffie‑hellman (rfc 3526 group 14) to agree on a key, then sha‑256 to derive a 32‑byte key. every message is xor'd with a keystream from hmac-sha256(counter), and each message has its own hmac. counters prevent replay. it's not quantum‑safe, but it's fine for chatting with friends.
- **persistence** - the app remembers your last nickname and room. reconnect and you're back where you left.


## what doesn't (yet)

- file sharing
- private messages (only room broadcast)
- message history (if you disconnect and reconnect, you don't get old messages)


## building the server

you need openssl and a c compiler (gcc works).

```bash
git clone https://github.com/retucio/retchat
cd retchat
make
```

the binary ends up in `bin/server`. run it:
`./bin/server (port)`  
 > port defaults to 6677


## protocol overview (for the curious)
1. tcp handshake - client connects.
2. DH key exchange - server sends its public key (length + bytes), client replies with its public key. both sides compute the shared secret, then sha‑256(shared_secret) -> 32‑byte enc_key.
3. message framing - for each message (including the null terminator):
   - 32 bytes hmac(enc_key, ciphertext)
   - uint16 (big‑endian) length of ciphertext
   - ciphertext = plaintext xor keystream(enc_key, counter)
   - keystream is hmac(enc_key, little‑endian counter) repeated (hash chaining if more than 32 bytes needed)
4. commands - plaintext starts with '/' (e.g. /nick foo). server parses and responds with [SERVER] ... messages.
5. broadcast - normal messages (not starting with '/') are sent to everyone in the same room as [nickname] message.
counters start at 0 for each direction and increment after each message.


## why the weird crypto?
i didn't want to drag in tls for a simple chat - certificates are a pain for self‑hosted stuff. the in‑band dh provides forward secrecy (new keys per session). the hmac prevents tampering. xor with a keyed hash is basically a stream cipher. it's not standard, but it's fun and works.


## contributing / notes
this is a personal project. the code is here in case someone finds it useful or wants to learn from it. if you break something, keep both pieces.
license: MIT (or whatever, i don't really care). just don't pretend you wrote the whole thing.