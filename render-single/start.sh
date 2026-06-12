#!/bin/bash
set -e
echo "[1/4] Naming Service..."
java -Dorg.omg.CORBA.ORBClass=org.jacorb.orb.ORB \
  -Dorg.omg.CORBA.ORBSingletonClass=org.jacorb.orb.ORBSingleton \
  -Djacorb.log.default.verbosity=1 \
  -DOAAddress=iiop://127.0.0.1:2809 \
  -Djacorb.naming.ior_filename=/app/ns.ior \
  -cp /app/corba-server.jar org.jacorb.naming.NameServer &
sleep 15

echo "[2/4] Serveur CORBA..."
java -Dorg.omg.CORBA.ORBClass=org.jacorb.orb.ORB \
  -Dorg.omg.CORBA.ORBSingletonClass=org.jacorb.orb.ORBSingleton \
  -Djava.awt.headless=true -Xmx512m -Xms128m \
  -jar /app/corba-server.jar \
  -ORBInitRef NameService=corbaloc:iiop:127.0.0.1:2809/NameService &
sleep 20

echo "[3/4] Spring Boot..."
java -Djava.awt.headless=true -Xmx384m -Xms128m \
  -Dserver.port=8080 \
  -Dcorba.naming.host=127.0.0.1 \
  -Dcorba.naming.port=2809 \
  -jar /app/web-middleware.jar &
sleep 30

echo "[4/4] Nginx..."
nginx -g "daemon off;" &

echo "=== Tous les services démarrés ==="
wait
