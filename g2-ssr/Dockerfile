FROM node:22-bookworm-slim
RUN sed -i 's|deb.debian.org|mirrors.aliyun.com|g; s|security.debian.org|mirrors.aliyun.com/debian-security|g' /etc/apt/sources.list.d/debian.sources \
    && apt-get update \
    && apt-get install -y --no-install-recommends build-essential python3 libcairo2-dev libpango1.0-dev libjpeg62-turbo-dev libgif-dev librsvg2-dev \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY package*.json ./
RUN npm config set registry https://registry.npmmirror.com && npm ci --omit=dev
COPY . .
ENV PORT=3000 OUTPUT_DIR=/app/output FONTCONFIG_FILE=/app/fonts/fonts.conf
EXPOSE 3000
CMD ["node", "app.js"]
