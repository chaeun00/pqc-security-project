#!/bin/bash
export PATH=/home/ssafy/.nvm/versions/node/v24.14.1/bin:$PATH
cd /home/ssafy/project/dashboard
npx vitest run src/test/MonitorPage.test.tsx
