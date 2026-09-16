import { createRequire } from "module";

const require = createRequire(import.meta.url);

require("./src/core/launch-upgrades.js");
require("./src/core/broadcast.js");
require("./src/core/batch-downloads.js");
require("./src/bot.js");
