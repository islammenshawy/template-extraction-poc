# Build stage
FROM node:18-alpine AS build

# Build argument for API URL
ARG VITE_API_BASE_URL=http://localhost:8080/api
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL

WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

# Production stage
FROM nginx:alpine

# Copy built files from build stage
COPY --from=build /app/dist /usr/share/nginx/html

# Copy nginx configuration template
COPY default.conf.template /etc/nginx/templates/default.conf.template

# Validate Nginx configuration build-time
# We substitute environment variables with valid values to verify syntax
RUN export BACKEND_URL="http://localhost:8080" && \
    mkdir -p /etc/nginx/conf.d && \
    envsubst '${BACKEND_URL}' < /etc/nginx/templates/default.conf.template > /etc/nginx/conf.d/default.conf && \
    nginx -t

# Expose port
EXPOSE 80

# Start nginx (envsubst will run automatically)
CMD ["nginx", "-g", "daemon off;"]
