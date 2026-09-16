import { createRequire } from "module";

const require = createRequire(import.meta.url);

require("./src/core/crash-guard.js");

require("./src/core/launch-upgrades.js");
require("./src/core/broadcast.js");
require("./src/core/batch-downloads.js");
require("./src/core/moviex-integration.js");
require("./src/bot.js");
