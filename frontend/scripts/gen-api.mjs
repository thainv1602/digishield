/**
 * Runs orval and fails when it did not generate anything.
 *
 * orval v6 catches its own errors, prints them with a 🛑 and exits 0. A spec it
 * cannot read therefore looks exactly like a successful run: the CI step goes
 * green, and whatever was in src/api/generated (nothing, on a fresh checkout)
 * stays there. A stray duplicate YAML key sat in the spec for days that way.
 *
 * So this wrapper judges the run by its output rather than by the exit code:
 * orval has to finish quietly and leave a generated client behind.
 */
import { spawnSync } from 'node:child_process';
import { existsSync, readdirSync, rmSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const frontend = dirname(dirname(fileURLToPath(import.meta.url)));
const outDir = join(frontend, 'src', 'api', 'generated');

/** Every .ts file under dir, recursively. */
function generatedFiles(dir) {
  if (!existsSync(dir)) return [];
  return readdirSync(dir, { withFileTypes: true }).flatMap((e) => {
    const full = join(dir, e.name);
    if (e.isDirectory()) return generatedFiles(full);
    return e.name.endsWith('.ts') ? [full] : [];
  });
}

/**
 * Files in the output directory that git tracks. Everything else there is
 * generated, and the .gitignore says exactly this.
 */
const KEEP = new Set(['.gitkeep', 'README.md']);

// orval writes over what it generates but never prunes, so an operation the
// spec drops leaves its file behind and the client keeps offering a route the
// API no longer serves. Locally that had already accumulated seven orphans a
// fresh checkout does not have.
if (existsSync(outDir)) {
  for (const entry of readdirSync(outDir)) {
    if (!KEEP.has(entry)) {
      rmSync(join(outDir, entry), { recursive: true });
    }
  }
}

const orval = spawnSync(
  process.platform === 'win32' ? 'npx.cmd' : 'npx',
  ['orval', '--config', './orval.config.ts'],
  { cwd: frontend, encoding: 'utf8' },
);

const output = `${orval.stdout ?? ''}${orval.stderr ?? ''}`;
process.stdout.write(output);

function fail(why) {
  console.error(`\n[gen:api] ${why}`);
  console.error(
    '[gen:api] The OpenAPI spec is the contract the client is generated from.\n' +
      '[gen:api] A spec that cannot produce one is broken, whatever orval exits with.',
  );
  process.exit(1);
}

if (orval.error) fail(`orval could not be started: ${orval.error.message}`);
if (orval.status !== 0) fail(`orval exited with ${orval.status}.`);

// orval prints "🛑 <project> - <error>" and still exits 0.
const reported = output.split('\n').filter((line) => line.includes('🛑'));
if (reported.length > 0) fail(`orval reported an error:\n  ${reported.join('\n  ')}`);

// The line orval prints once it has written everything. Absent means it stopped
// somewhere in the middle, which on a developer's machine leaves the previous
// run's files behind and looks like nothing happened.
if (!output.includes('ready to use orval')) {
  fail('orval did not report a finished run.');
}

const files = generatedFiles(outDir);
if (files.length === 0) fail(`orval wrote no client to ${outDir}.`);

console.log(`[gen:api] ${files.length} file(s) generated from the spec.`);
