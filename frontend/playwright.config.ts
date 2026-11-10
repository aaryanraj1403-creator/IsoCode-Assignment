import {defineConfig} from '@playwright/test';
import {existsSync} from 'node:fs';
const candidates=[process.env.PLAYWRIGHT_EXECUTABLE_PATH,'C:/Program Files/Google/Chrome/Application/chrome.exe','C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'];
const executablePath=candidates.find((p):p is string=>!!p&&existsSync(p));
export default defineConfig({
 testDir:'./e2e',timeout:60000,expect:{timeout:15000},workers:1,
 reporter:[['list'],['html',{open:'never'}]],
 use:{baseURL:process.env.BASE_URL||'http://127.0.0.1:5173',headless:true,screenshot:'only-on-failure',trace:'retain-on-failure',
 launchOptions:executablePath?{executablePath}:{}},
});
