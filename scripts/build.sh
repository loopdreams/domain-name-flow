#!/usr/bin/env sh

npm install && \
npx tailwindcss -i ./resources/public/css/styles.css -o ./resources/public/css/tw.css && \
npx webpack --config webpack.config.js && \
clj -T:build uber
