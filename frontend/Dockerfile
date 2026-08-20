FROM node:20-alpine AS build
WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.29-alpine AS runtime
USER root
RUN apk add --no-cache wget

# La configuración se resuelve al arrancar, no al construir: el entrypoint de la
# imagen pasa por envsubst todo lo que haya en /etc/nginx/templates y escribe el
# resultado en /etc/nginx/conf.d. Así la misma imagen vale para docker-compose y
# para un despliegue gestionado, que dan al backend direcciones distintas entre
# sí.
#
# El filtro es imprescindible: sin él envsubst sustituiría en la plantilla
# cualquier $variable que coincidiese con una del entorno del contenedor,
# incluidas las propias de nginx ($host, $uri, $scheme…).
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
ENV NGINX_ENVSUBST_FILTER=^BACKEND_

# Compone BACKEND_ORIGIN a partir de BACKEND_ORIGIN o BACKEND_HOSTPORT y lo
# exporta antes de que corra envsubst. El bit de ejecución es obligatorio: el
# entrypoint ignora en silencio los ficheros de /docker-entrypoint.d/ que no lo
# tengan, y el fallo se vería solo como un proxy_pass vacío.
COPY --chmod=755 docker-entrypoint.d/19-backend-origin.envsh /docker-entrypoint.d/
COPY --from=build --chown=101:101 /app/dist/frontend/browser /usr/share/nginx/html

USER 101
EXPOSE 8080
