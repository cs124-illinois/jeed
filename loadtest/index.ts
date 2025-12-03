/**
 * Jeed Load Test Script
 *
 * Sends compile and execute requests to stress test the Jeed server
 * and help identify memory leaks.
 */

const SERVER_URL = process.env.JEED_SERVER || "http://localhost:8888";
const CONCURRENCY = parseInt(process.env.CONCURRENCY || "2");
const DELAY_MS = parseInt(process.env.DELAY_MS || "100");

// Sample Java snippets to compile and run
const javaSnippets = [
  `System.out.println("Hello, World!");`,
  `int x = 5; int y = 10; System.out.println(x + y);`,
  `for (int i = 0; i < 5; i++) { System.out.println(i); }`,
  `String s = "test"; System.out.println(s.toUpperCase());`,
  `int[] arr = {1, 2, 3, 4, 5}; for (int n : arr) System.out.println(n);`,
  `double d = Math.sqrt(16); System.out.println(d);`,
  `java.util.List<String> list = new java.util.ArrayList<>(); list.add("a"); System.out.println(list);`,
  `int factorial = 1; for (int i = 1; i <= 5; i++) factorial *= i; System.out.println(factorial);`,
  `String[] words = {"hello", "world"}; System.out.println(String.join(" ", words));`,
  `java.util.Random r = new java.util.Random(42); System.out.println(r.nextInt(100));`,
];

// Sample Kotlin snippets to compile and run
const kotlinSnippets = [
  `println("Hello, World!")`,
  `val x = 5; val y = 10; println(x + y)`,
  `for (i in 0..4) { println(i) }`,
  `val s = "test"; println(s.uppercase())`,
  `val arr = arrayOf(1, 2, 3, 4, 5); arr.forEach { println(it) }`,
  `val d = kotlin.math.sqrt(16.0); println(d)`,
  `val list = mutableListOf("a"); println(list)`,
  `var factorial = 1; for (i in 1..5) factorial *= i; println(factorial)`,
  `val words = arrayOf("hello", "world"); println(words.joinToString(" "))`,
  `val r = kotlin.random.Random(42); println(r.nextInt(100))`,
];

// Sample Java source files (not snippets) - using FlatSource format
const javaSources = [
  [
    { path: "Main.java", contents: `public class Main {
  public static void main(String[] args) {
    System.out.println("Hello from Main!");
  }
}` }
  ],
  [
    { path: "Calculator.java", contents: `public class Calculator {
  public static int add(int a, int b) { return a + b; }
  public static void main(String[] args) {
    System.out.println(add(5, 3));
  }
}` }
  ],
];

// Sample Kotlin source files - using FlatSource format
const kotlinSources = [
  [
    { path: "Main.kt", contents: `fun main() {
  println("Hello from Kotlin Main!")
}` }
  ],
  [
    { path: "Calculator.kt", contents: `fun add(a: Int, b: Int) = a + b
fun main() {
  println(add(5, 3))
}` }
  ],
];

interface FlatSource {
  path: string;
  contents: string;
}

interface JeedRequest {
  label: string;
  snippet?: string;
  sources?: FlatSource[];
  tasks: string[];
  arguments?: {
    snippet?: {
      indent?: number;
      fileType?: string;
    };
    kompilation?: {
      useK2?: boolean;
    };
  };
}

interface Stats {
  total: number;
  success: number;
  failed: number;
  javaSnippet: number;
  kotlinSnippet: number;
  javaSource: number;
  kotlinSource: number;
  errors: Map<string, number>;
}

const stats: Stats = {
  total: 0,
  success: 0,
  failed: 0,
  javaSnippet: 0,
  kotlinSnippet: 0,
  javaSource: 0,
  kotlinSource: 0,
  errors: new Map(),
};

function randomChoice<T>(arr: T[]): T {
  return arr[Math.floor(Math.random() * arr.length)];
}

async function sendRequest(request: JeedRequest): Promise<boolean> {
  try {
    const response = await fetch(SERVER_URL, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(request),
    });

    if (!response.ok) {
      const text = await response.text();
      const errorKey = `HTTP ${response.status}: ${text.slice(0, 100)}`;
      stats.errors.set(errorKey, (stats.errors.get(errorKey) || 0) + 1);
      return false;
    }

    const result = await response.json();

    // Check if any tasks failed
    if (result.failedTasks && result.failedTasks.length > 0) {
      const errorKey = `Task failed: ${result.failedTasks.join(", ")}`;
      stats.errors.set(errorKey, (stats.errors.get(errorKey) || 0) + 1);
      return false;
    }

    return true;
  } catch (error) {
    const errorKey = `Network error: ${(error as Error).message}`;
    stats.errors.set(errorKey, (stats.errors.get(errorKey) || 0) + 1);
    return false;
  }
}

async function runJavaSnippet(): Promise<boolean> {
  const snippet = randomChoice(javaSnippets);
  const request: JeedRequest = {
    label: "loadtest-java-snippet",
    snippet,
    tasks: ["compile", "execute"],
    arguments: {
      snippet: { indent: 2 },
    },
  };
  stats.javaSnippet++;
  return sendRequest(request);
}

async function runKotlinSnippet(): Promise<boolean> {
  const snippet = randomChoice(kotlinSnippets);
  const request: JeedRequest = {
    label: "loadtest-kotlin-snippet",
    snippet,
    tasks: ["kompile", "execute"],
    arguments: {
      snippet: { indent: 2, fileType: "KOTLIN" },
      kompilation: { useK2 },
    },
  };
  stats.kotlinSnippet++;
  return sendRequest(request);
}

async function runJavaSource(): Promise<boolean> {
  const sources = randomChoice(javaSources);
  const request: JeedRequest = {
    label: "loadtest-java-source",
    sources,
    tasks: ["compile", "execute"],
  };
  stats.javaSource++;
  return sendRequest(request);
}

async function runKotlinSource(): Promise<boolean> {
  const sources = randomChoice(kotlinSources);
  const request: JeedRequest = {
    label: "loadtest-kotlin-source",
    sources,
    tasks: ["kompile", "execute"],
    arguments: {
      kompilation: { useK2 },
    },
  };
  stats.kotlinSource++;
  return sendRequest(request);
}

type TestRunner = () => Promise<boolean>;

async function runRandomTest(runners: TestRunner[]): Promise<void> {
  stats.total++;
  const runner = randomChoice(runners);
  const success = await runner();
  if (success) {
    stats.success++;
  } else {
    stats.failed++;
  }
}

function printStats(): void {
  console.log("\n--- Load Test Statistics ---");
  console.log(`Total requests: ${stats.total}`);
  console.log(`Successful: ${stats.success} (${((stats.success / stats.total) * 100).toFixed(1)}%)`);
  console.log(`Failed: ${stats.failed} (${((stats.failed / stats.total) * 100).toFixed(1)}%)`);
  console.log(`\nBy type:`);
  console.log(`  Java snippets: ${stats.javaSnippet}`);
  console.log(`  Kotlin snippets: ${stats.kotlinSnippet}`);
  console.log(`  Java sources: ${stats.javaSource}`);
  console.log(`  Kotlin sources: ${stats.kotlinSource}`);

  if (stats.errors.size > 0) {
    console.log(`\nErrors:`);
    for (const [error, count] of stats.errors) {
      console.log(`  ${error}: ${count}`);
    }
  }
}

async function checkServerStatus(): Promise<boolean> {
  try {
    const response = await fetch(`${SERVER_URL}/version`);
    if (response.ok) {
      const version = await response.text();
      console.log(`Server version: ${version}`);
      return true;
    }
  } catch {
    // Server not ready
  }
  return false;
}

async function waitForServer(): Promise<void> {
  console.log(`Waiting for server at ${SERVER_URL}...`);
  while (!(await checkServerStatus())) {
    await new Promise(resolve => setTimeout(resolve, 1000));
  }
  console.log("Server is ready!");
}

// Global flag for K2 compiler
let useK2 = false;

// Idle test - just poll /version to test Ktor/Netty memory behavior without compilations
async function runIdleTest(): Promise<void> {
  const intervalMs = parseInt(process.env.IDLE_INTERVAL_MS || "125"); // 8 requests/sec like k8s liveness probe
  let requestCount = 0;
  let errorCount = 0;

  console.log("\nIdle Test Mode - Only polling /version endpoint");
  console.log(`Interval: ${intervalMs}ms (${(1000 / intervalMs).toFixed(1)} requests/sec)`);
  console.log("This simulates Kubernetes liveness probe traffic with NO compilations\n");

  let running = true;
  process.on("SIGINT", () => {
    console.log("\n\nShutting down idle test...");
    running = false;
  });

  const statusInterval = setInterval(() => {
    process.stdout.write(`\rRequests: ${requestCount} | Errors: ${errorCount}`);
  }, 1000);

  while (running) {
    try {
      const response = await fetch(`${SERVER_URL}/version`);
      if (response.ok) {
        requestCount++;
      } else {
        errorCount++;
      }
    } catch {
      errorCount++;
    }
    await new Promise(resolve => setTimeout(resolve, intervalMs));
  }

  clearInterval(statusInterval);
  console.log(`\nFinal: ${requestCount} requests, ${errorCount} errors`);
}

async function main(): Promise<void> {
  const args = process.argv.slice(2);
  const javaOnly = args.includes("--java-only");
  const kotlinOnly = args.includes("--kotlin-only");
  const idleMode = args.includes("--idle");
  useK2 = args.includes("--k2");

  console.log("Jeed Load Test");
  console.log(`Server: ${SERVER_URL}`);
  console.log(`Concurrency: ${CONCURRENCY}`);
  console.log(`Delay between batches: ${DELAY_MS}ms`);

  if (idleMode) {
    console.log("Mode: Idle (version polling only)");
  } else if (javaOnly) {
    console.log("Mode: Java only");
  } else if (kotlinOnly) {
    console.log("Mode: Kotlin only");
  } else {
    console.log("Mode: Mixed (Java + Kotlin)");
  }
  if (!idleMode && useK2) {
    console.log("Kotlin compiler: K2 (FIR)");
  } else if (!idleMode) {
    console.log("Kotlin compiler: K1 (legacy)");
  }

  await waitForServer();

  // Idle mode - just poll /version
  if (idleMode) {
    await runIdleTest();
    return;
  }

  // Build list of test runners based on flags
  const runners: TestRunner[] = [];
  if (!kotlinOnly) {
    runners.push(runJavaSnippet, runJavaSource);
  }
  if (!javaOnly) {
    runners.push(runKotlinSnippet, runKotlinSource);
  }

  console.log("\nStarting load test (Ctrl+C to stop)...\n");

  // Print stats periodically
  const statsInterval = setInterval(() => {
    process.stdout.write(`\rRequests: ${stats.total} | Success: ${stats.success} | Failed: ${stats.failed}`);
  }, 500);

  // Handle graceful shutdown
  let running = true;
  process.on("SIGINT", () => {
    console.log("\n\nShutting down...");
    running = false;
  });

  // Run load test
  while (running) {
    // Run CONCURRENCY requests in parallel
    const promises = [];
    for (let i = 0; i < CONCURRENCY; i++) {
      promises.push(runRandomTest(runners));
    }
    await Promise.all(promises);

    // Small delay between batches
    await new Promise(resolve => setTimeout(resolve, DELAY_MS));
  }

  clearInterval(statsInterval);
  printStats();
}

main().catch(console.error);
