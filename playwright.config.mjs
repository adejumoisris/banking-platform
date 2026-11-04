import {defineConfig} from '@playwright/test';
export default defineConfig({
 testDir:'./frontend-tests',testMatch:'*.spec.mjs',workers:1,
 use:{baseURL:'http://127.0.0.1:4173',viewport:{width:1440,height:1000},timezoneId:'Africa/Lagos',screenshot:'only-on-failure',headless:true},
 webServer:{command:'node scripts/preview-staff.mjs',url:'http://127.0.0.1:4173',reuseExistingServer:false},
 reporter:'list'
});
