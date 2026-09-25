FROM node:24-bookworm

WORKDIR /app
RUN apt-get update \
  && apt-get install -y --no-install-recommends ffmpeg ca-certificates \
  && rm -rf /var/lib/apt/lists/*

COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run setup:media && npm run build

ENV NODE_ENV=production
EXPOSE 10000
CMD ["npm", "start"]
