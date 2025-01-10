import * as esbuild from "esbuild"
import fs from "fs"

const smallerSourceMap = {
  name: "excludeVendorFromSourceMap",
  setup(build) {
    build.onLoad({ filter: /node_modules/ }, (args) => {
      if (args.path.endsWith(".js")) {
        return {
          contents:
            fs.readFileSync(args.path, "utf8") +
            "\n//# sourceMappingURL=data:application/json;base64,eyJ2ZXJzaW9uIjozLCJzb3VyY2VzIjpbIiJdLCJtYXBwaW5ncyI6IkEifQ==",
          loader: "default",
        }
      }
    })
  },
}

await esbuild.build({
  entryPoints: ["server/index.ts"],
  logLevel: "error",
  sourcemap: true,
  bundle: true,
  packages: "bundle",
  platform: "node",
  outfile: "bundled/index.js",
  plugins: [smallerSourceMap],
  loader: {
    ".json": "copy",
  },
})
