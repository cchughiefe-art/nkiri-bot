import { createRequire } from "module";

const require = createRequire(import.meta.url);

require("./src/core/access-gate.js");
require("./src/bot.js");
