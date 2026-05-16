const fs = require("fs");
const path = require("path");

const root = process.cwd();
const javaRoot = path.join(root, "src", "main", "java");
const langRoot = path.join(root, "src", "main", "resources", "assets", "wavedefense", "lang");

function walk(dir, predicate, out = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      walk(fullPath, predicate, out);
    } else if (predicate(fullPath)) {
      out.push(fullPath);
    }
  }
  return out;
}

function rel(file) {
  return path.relative(root, file).replaceAll(path.sep, "/");
}

const javaFiles = walk(javaRoot, (file) => file.endsWith(".java"));
const langFiles = walk(langRoot, (file) => file.endsWith(".json"));

const usedKeys = new Set();
const literalFindings = [];
const allowedBlankKeys = new Set(["wavedefense.auto.text_da39a3ee"]);

for (const file of javaFiles) {
  const source = fs.readFileSync(file, "utf8");

  for (const match of source.matchAll(/(?:Component\.)?translatable\(\s*"([^"]+)"/g)) {
    usedKeys.add(match[1]);
  }

  const lines = source.split(/\r?\n/);
  lines.forEach((line, index) => {
    const isLiteral = line.includes("Component.literal(");
    const isRawDraw = /draw(?:Centered)?String\([^,\n]+,\s*"/.test(line);
    if (isLiteral || isRawDraw) {
      literalFindings.push({
        file: rel(file),
        line: index + 1,
        text: line.trim(),
      });
    }
  });
}

let failed = false;

for (const file of langFiles) {
  const lang = JSON.parse(fs.readFileSync(file, "utf8"));
  const missing = [...usedKeys].filter((key) => !(key in lang));
  const empty = Object.entries(lang)
    .filter(([key, value]) => !allowedBlankKeys.has(key) && typeof value === "string" && value.trim() === "")
    .map(([key]) => key);

  console.log(`${path.basename(file)}: keys=${Object.keys(lang).length}, missing=${missing.length}, empty=${empty.length}`);

  if (missing.length || empty.length) {
    failed = true;
    for (const key of missing.slice(0, 25)) console.log(`  missing: ${key}`);
    for (const key of empty.slice(0, 25)) console.log(`  empty: ${key}`);
  }
}

console.log(`used translatable keys: ${usedKeys.size}`);
console.log(`literal/raw text findings: ${literalFindings.length}`);

for (const finding of literalFindings.slice(0, 80)) {
  console.log(`${finding.file}:${finding.line}: ${finding.text}`);
}

process.exit(failed ? 1 : 0);
