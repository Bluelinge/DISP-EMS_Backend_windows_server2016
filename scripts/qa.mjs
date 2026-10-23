import { createRequire } from 'node:module'
import { mkdir } from 'node:fs/promises'
import { resolve } from 'node:path'
const require = createRequire(import.meta.url)
const { chromium } = require('C:/Users/STD-07/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright')
const base = 'http://127.0.0.1:5173/'
const output = resolve('screenshots'); await mkdir(output,{recursive:true})
const browser=await chromium.launch({headless:true,executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe'})
const context=await browser.newContext({viewport:{width:1920,height:1080},locale:'zh-CN'})
const page=await context.newPage()
const go=async path=>{await page.goto(base+'#'+path);await page.waitForTimeout(400)}
const shot=name=>page.screenshot({path:resolve(output,name),fullPage:true})
try{
 await go('/login'); await page.evaluate(()=>localStorage.removeItem('disp-mes-ui-prototype-v1')); await page.reload(); await page.waitForTimeout(250)
 const intro=await page.locator('.login-intro').boundingBox(), card=await page.locator('.login-card').boundingBox()
 if(Math.abs((intro.x+intro.width/2)-(card.x+card.width/2))>3||intro.y>=card.y)throw Error('login is not vertically centered')
 await shot('round2-login-1920.png')
 await page.getByRole('button',{name:/进入系统/}).click();await page.locator('.select-page').waitFor()
 const product=page.getByLabel('产品编码'); if(!await product.evaluate(e=>document.activeElement===e))throw Error('product code is not focused')
 await product.fill('DISPEN9243-40-000126');await product.press('Enter');await page.locator('.scan-code').waitFor()
 if(await page.locator('.scan-code').textContent()!=='DISPEN9243-40-000126')throw Error('scanner result missing')
 await page.getByRole('button',{name:/开始作业/}).click();await page.locator('.work-page').waitFor()
 if(await page.getByText('投影引导预览').count()||await page.getByText('任务信息').count()||await page.getByRole('button',{name:'跳过本步'}).count())throw Error('legacy work cards remain')
 const video=await page.locator('.work-video').boundingBox(), params=await page.locator('.work-parameters').boundingBox();if(video.width<=params.width)throw Error('video is not dominant')
 const popupPromise=page.waitForEvent('popup');await page.getByRole('button',{name:/打开投影窗口/}).click();const popup=await popupPromise;await popup.close()
 await shot('round2-work-1920.png')
 await go('/login');await page.getByRole('button',{name:'管理员'}).click();await page.getByRole('button',{name:/进入系统/}).click();await go('/admin/processes/1')
 await page.locator('.step-edit-form').waitFor();await page.getByRole('button',{name:'添加参数'}).click();await page.getByRole('button',{name:'添加参数'}).click();await page.getByRole('button',{name:'添加参数'}).click()
 if((await page.locator('.parameter-edit-list article').count())>3)throw Error('parameter limit failed')
 await page.getByRole('button',{name:'保存步骤编辑'}).click()
 await go('/admin/videos');await page.getByRole('button',{name:'查看'}).first().click();await page.getByText('暂无可播放媒体').waitFor();await page.keyboard.press('Escape')
 await go('/admin/users');if(!await page.getByText('手机号').count())throw Error('phone column missing');await page.getByRole('button',{name:'编辑'}).first().click();await page.getByText('手机号').last().waitFor();await page.keyboard.press('Escape')
 await go('/admin/dicts');if(!page.url().endsWith('#/admin/processes'))throw Error('dict route did not redirect')
 await go('/admin/processes');await page.getByRole('button',{name:'删除'}).click();await page.getByText('当前配置引用').waitFor();await page.keyboard.press('Escape')
 await page.setViewportSize({width:1366,height:768});await go('/login');await shot('round2-login-1366.png');const overflow=await page.evaluate(()=>document.documentElement.scrollWidth>window.innerWidth);if(overflow)throw Error('login overflow at 1366')
 await go('/op/work/test');await shot('round2-work-1366.png');if(await page.evaluate(()=>document.documentElement.scrollWidth>window.innerWidth))throw Error('work overflow at 1366')
 console.log(JSON.stringify({loginCentered:true,scannerFocused:true,workAB:true,stepEditing:true,videoPreview:true,userPhone:true,dictRedirect:true,screenshots:4}))
}finally{await browser.close()}
