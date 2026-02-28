const http = require("http");
const fs = require("fs");
const path = require("path");
const os = require("os");

const host = "0.0.0.0";
const port = Number(process.env.PORT) || 8080;
const root = __dirname;

const mimeTypes = {
  ".html": "text/html; charset=utf-8",
  ".js": "application/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".gif": "image/gif",
  ".svg": "image/svg+xml",
  ".ico": "image/x-icon"
};

function getLanAddresses() {
  const interfaces = os.networkInterfaces();
  const addresses = [];

  for (const netIf of Object.values(interfaces)) {
    for (const iface of netIf || []) {
      if (iface.family === "IPv4" && !iface.internal) {
        addresses.push(iface.address);
      }
    }
  }

  return addresses;
}

function resolvePath(urlPath) {
  const decodedPath = decodeURIComponent(urlPath.split("?")[0]);
  const requested = decodedPath === "/" ? "/index.html" : decodedPath;
  const safePath = path.normalize(requested).replace(/^(\.\.[/\\])+/, "");
  return path.join(root, safePath);
}

const server = http.createServer((req, res) => {
  const filePath = resolvePath(req.url || "/");

  if (!filePath.startsWith(root)) {
    res.writeHead(403, { "Content-Type": "text/plain; charset=utf-8" });
    res.end("403 Forbidden");
    return;
  }

  fs.stat(filePath, (statErr, stats) => {
    if (statErr || !stats.isFile()) {
      res.writeHead(404, { "Content-Type": "text/plain; charset=utf-8" });
      res.end("404 Not Found");
      return;
    }

    const ext = path.extname(filePath).toLowerCase();
    const contentType = mimeTypes[ext] || "application/octet-stream";

    res.writeHead(200, { "Content-Type": contentType });
    fs.createReadStream(filePath).pipe(res);
  });
});

server.listen(port, host, () => {
  const lanAddresses = getLanAddresses();
  console.log(`Server running at http://localhost:${port}`);
  if (lanAddresses.length) {
    for (const address of lanAddresses) {
      console.log(`LAN: http://${address}:${port}`);
    }
  } else {
    console.log("No LAN IPv4 address detected.");
  }
});
