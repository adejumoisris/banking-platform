import http from 'node:http';
import fs from 'node:fs/promises';
import path from 'node:path';
const root=path.resolve('src/main/resources/static');
const csp="default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'";
http.createServer(async(req,res)=>{
 try {
 const url=new URL(req.url,'http://127.0.0.1');
 const filename=path.resolve(root,'.'+decodeURIComponent(url.pathname==='/'?'/index.html':url.pathname));
 if(!filename.startsWith(root+path.sep))throw Error();
 const data=await fs.readFile(filename);
 res.writeHead(200,{'Content-Type':({'.html':'text/html','.js':'text/javascript','.css':'text/css','.svg':'image/svg+xml'})[path.extname(filename)]||'application/octet-stream','Cache-Control':'no-store','Content-Security-Policy':csp});
 res.end(data);
 }catch{res.writeHead(404);res.end('Not found')}
}).listen(4173,'127.0.0.1',()=>console.log('Staff preview: http://127.0.0.1:4173'));
