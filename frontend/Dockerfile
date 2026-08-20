# 1. Aşama: Bağımlılıkları kur ve production build al
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

# 2. Aşama: Statik dosyaları nginx ile sun, /api ve /ws'i backend'e yönlendir
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
