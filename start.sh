#!/bin/bash
# Start Spring Boot in the background on port 8081
java -Xmx300m -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC -Djava.security.egd=file:/dev/./urandom -jar app.jar --server.port=8081 &

# Start Nitro Node.js Server in the foreground on port 8080
export PORT=8080
export NITRO_PORT=8080
node frontend/.output/server/index.mjs
