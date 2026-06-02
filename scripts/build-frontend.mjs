import { mkdir, readdir, readFile, rm, stat, writeFile } from "node:fs/promises";
import { createHash } from "node:crypto";
import path from "node:path";

const projectRoot = path.resolve(import.meta.dirname, "..");
const staticRoot = path.join(projectRoot, "src", "main", "resources", "static");
const assetsRoot = path.join(staticRoot, "assets");
const outputRoot = path.join(projectRoot, "target", "frontend-static");

const requiredFiles = [
  "index.html",
  "sw.js",
  "manifest.webmanifest",
  "assets/app.css",
  "assets/app.js",
  "assets/modules/app-state.js",
  "assets/modules/data-loader.js",
  "assets/modules/display-utils.js",
  "assets/modules/field-config.js",
  "assets/modules/list-config.js",
  "assets/modules/paging.js",
  "assets/modules/routes.js",
  "assets/modules/session.js",
  "assets/modules/status-ui.js",
  "assets/modules/ui-config.js"
];

await assertRequiredFiles();
await assertModuleImports(path.join(assetsRoot, "app.js"));
await assertTextEncoding();
await copyDirectory(staticRoot, outputRoot);
await writeServiceWorker();
await writeBuildManifest();

console.log(`Frontend build prepared at ${path.relative(projectRoot, outputRoot)}`);

async function assertRequiredFiles() {
  for (const file of requiredFiles) {
    await stat(path.join(staticRoot, file));
  }
}

async function assertModuleImports(entryFile) {
  const seen = new Set();
  const stack = [entryFile];
  while (stack.length) {
    const file = stack.pop();
    if (seen.has(file)) continue;
    seen.add(file);
    const source = await readFile(file, "utf8");
    const imports = [...source.matchAll(/from\s+["'](\.\/[^"']+)["']/g)]
      .map(match => match[1])
      .filter(specifier => specifier.endsWith(".js"));
    for (const specifier of imports) {
      const resolved = path.resolve(path.dirname(file), specifier);
      await stat(resolved);
      stack.push(resolved);
    }
  }
}

async function assertTextEncoding() {
  const files = [];
  await collectTextFiles(staticRoot, files);
  const mojibakePattern = /(?:鐧|璺|鎬|閰|搴|鏆傛|Ã.|Â.|�)/;
  const failures = [];
  for (const file of files) {
    const source = await readFile(file, "utf8");
    if (mojibakePattern.test(source)) {
      failures.push(path.relative(projectRoot, file));
    }
  }
  if (failures.length) {
    throw new Error(`Possible mojibake text found:\n${failures.map(file => `- ${file}`).join("\n")}`);
  }
}

async function collectTextFiles(root, files) {
  for (const entry of await readdir(root, { withFileTypes: true })) {
    const fullPath = path.join(root, entry.name);
    if (entry.isDirectory()) {
      await collectTextFiles(fullPath, files);
    } else if (entry.isFile() && /\.(?:html|css|js|json|webmanifest)$/i.test(entry.name)) {
      files.push(fullPath);
    }
  }
}

async function copyDirectory(source, target) {
  await rm(target, { force: true, recursive: true });
  await mkdir(target, { recursive: true });
  for (const entry of await readdir(source, { withFileTypes: true })) {
    const sourcePath = path.join(source, entry.name);
    const targetPath = path.join(target, entry.name);
    if (entry.isDirectory()) {
      await copyDirectory(sourcePath, targetPath);
    } else if (entry.isFile()) {
      await mkdir(path.dirname(targetPath), { recursive: true });
      await writeFile(targetPath, await readFile(sourcePath));
    }
  }
}

async function writeBuildManifest() {
  const files = [];
  await collectFiles(outputRoot, files);
  const manifest = {
    builtAt: new Date().toISOString(),
    files: files.sort((left, right) => left.path.localeCompare(right.path))
  };
  await writeFile(
    path.join(outputRoot, "frontend-build-manifest.json"),
    JSON.stringify(manifest, null, 2)
  );
}

async function writeServiceWorker() {
  const swPath = path.join(outputRoot, "sw.js");
  const shellAssets = await collectShellAssets();
  const hash = createHash("sha256");
  for (const asset of shellAssets) {
    if (asset === "/") continue;
    hash.update(asset);
    hash.update(await readFile(path.join(outputRoot, asset.slice(1))));
  }
  const cacheName = `forklift-erp-client-${hash.digest("hex").slice(0, 12)}`;
  const source = await readFile(swPath, "utf8");
  const generated = source
    .replace(/const CACHE_NAME = ".*?";/, `const CACHE_NAME = "${cacheName}";`)
    .replace(/const SHELL_ASSETS = \[[\s\S]*?\];/, `const SHELL_ASSETS = ${JSON.stringify(shellAssets, null, 2)};`);
  await writeFile(swPath, generated);
}

async function collectShellAssets() {
  const files = [];
  await collectFiles(outputRoot, files);
  const assets = files
    .map(file => `/${file.path}`)
    .filter(file => file !== "/sw.js" && file !== "/frontend-build-manifest.json")
    .sort((left, right) => left.localeCompare(right));
  if (assets.includes("/index.html")) {
    assets.unshift("/");
  }
  return [...new Set(assets)];
}

async function collectFiles(root, files) {
  for (const entry of await readdir(root, { withFileTypes: true })) {
    const fullPath = path.join(root, entry.name);
    if (entry.isDirectory()) {
      await collectFiles(fullPath, files);
    } else if (entry.isFile()) {
      const info = await stat(fullPath);
      files.push({
        path: path.relative(outputRoot, fullPath).replaceAll(path.sep, "/"),
        bytes: info.size
      });
    }
  }
}
