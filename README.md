# retchat

retchat is a homemade chat system i made because i wanted something simple, that didn't need a
database, that worked on a cheap VPS and that allowed me to chat with my phone without handing my 
messages to a third party.

it doesn't aspire to be Matrix or Discord. it's a small ecosystem with clients for Android and Linux,
and a server. you can run the server on any Linux machine and have ciphered rooms with nicks.


## what works

- **rooms** - `/join <room>` creates or joins an existing room. each room has its own user list.
- **nicks** - `/nick <nickame>` changes how you're seen. nicknames must be unique in rooms.
- **dms** - `/dm <nick> <message>` direct messages a user by nickname. you can switch conversations
between your current room and dms in the conversations sidebar.
- **cipher** - Diffie‑Hellman 2048 bits (grupo 14, RFC 3526) to agree on a key, then 
  SHA‑256 to derive a 32-byte key. each message is encrypted with XOR using a flow of generated keys
  with HMAC‑SHA256(counter), and each messages carries its own HMAC. counters avoid repetition. 
  it's not "quantum-resistant", but works for chatting with people *securely*.
- **persistance** - the app remembers your last nick and room and applies them automatically on launch.


## what doesn't work (yet)

- sending files, including images
- message history (if you disconnect, messages are gone)


## protocol spec 

it can be found [here](https://github.com/retinc/retchat-docs)


## why such a weird cipher?

i didn't want to involve TLS for a simple chat - certificates are an annoyance for self hosted services.
the in-band DH provides with the perfect forward secret (new keys per session). HMAC avoids tampering.
XOR works just fine. it's not standard, but it's fun and works.


## contributions / notes

this is a personal project. the source code is here in case it is found useful by someone or they 
want to learn from it.

license: MIT (or whatever, i really don't care). just don't claim it as yours.