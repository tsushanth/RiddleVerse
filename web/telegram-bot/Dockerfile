FROM node:20-slim
WORKDIR /app
COPY package.json ./
RUN npm install --production
COPY server.js ./
ENV PORT=3463
EXPOSE 3463
CMD ["node", "server.js"]
