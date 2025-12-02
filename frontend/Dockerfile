FROM node:18-alpine AS build
ARG VITE_API_BASE_URL=http://localhost:8080/api
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh
EXPOSE 80

# Set environment variable for backend URL
ENV BACKEND_URL=http://backend:8080

ENTRYPOINT ["/entrypoint.sh"]
