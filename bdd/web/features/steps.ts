import { After, AfterAll, Before, BeforeAll, Then, When, setDefaultTimeout } from "@cucumber/cucumber";
import { spawn, type ChildProcess } from "node:child_process";
import { fileURLToPath } from "node:url";
import { chromium, type Browser, type Page } from "playwright-core";

setDefaultTimeout(60_000);

const PREVIEW_PORT = 4173;
const webDir = fileURLToPath(new URL("../../../web", import.meta.url));
// WEB_URL points the suite at an already-running app; otherwise the built app is served with `vite preview`.
const externalUrl = process.env.WEB_URL;
const baseUrl = externalUrl ?? `http://localhost:${PREVIEW_PORT}`;

let preview: ChildProcess | undefined;
let previewExit: string | undefined;
let browser: Browser;
let page: Page;

async function isServing(url: string): Promise<boolean> {
  try {
    return (await fetch(url)).ok;
  } catch {
    return false; // not up (yet)
  }
}

async function waitUntilServing(url: string, timeoutMs: number): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (previewExit) throw new Error(`vite preview ${previewExit}`);
    if (await isServing(url)) return;
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error(`${url} did not start serving within ${timeoutMs} ms`);
}

function stopPreview(): void {
  if (preview?.pid) {
    try {
      process.kill(-preview.pid);
    } catch {
      // the process group is already gone
    }
  }
  preview = undefined;
}

// A killed or interrupted run must not leave a preview server behind to be mistaken for the next run's app.
process.once("exit", stopPreview);
for (const signal of ["SIGINT", "SIGTERM"] as const) {
  process.once(signal, () => {
    stopPreview();
    process.exit(1);
  });
}

function startPreview(): ChildProcess {
  // This suite runs with NODE_OPTIONS='--import tsx'; tsx is not resolvable from web/, so drop it for the child.
  const { NODE_OPTIONS: _tsxLoader, ...env } = process.env;
  const child = spawn("pnpm", ["vite", "preview", "--port", String(PREVIEW_PORT), "--strictPort"], {
    cwd: webDir,
    env,
    stdio: "ignore",
    detached: true, // own process group, so pnpm's child (vite itself) can be stopped with it
  });
  child.on("error", (error) => (previewExit = `failed to start: ${error.message}`));
  child.on("exit", (code) => (previewExit = `exited early with code ${code}`));
  return child;
}

BeforeAll(async () => {
  if (!externalUrl) {
    if (await isServing(baseUrl)) {
      throw new Error(
        `Something already serves ${baseUrl}; stop it, or set WEB_URL to test that instance deliberately.`,
      );
    }
    preview = startPreview();
  }
  try {
    await waitUntilServing(baseUrl, 30_000);
    // Uses the installed Google Chrome, so no Playwright browser download is needed.
    browser = await chromium.launch({ channel: "chrome" });
  } catch (error) {
    stopPreview();
    throw error;
  }
});

AfterAll(async () => {
  await browser?.close();
  stopPreview();
});

Before(async () => {
  page = await browser.newPage();
});

After(async () => {
  await page.close();
});

When("I open the home page", async () => {
  await page.goto(baseUrl);
});

Then("I see the heading {string}", async (text: string) => {
  await page.getByRole("heading", { name: text }).waitFor({ state: "visible", timeout: 5_000 });
});
