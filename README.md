# frostflower

Additional local file backend for [duratom](https://github.com/jimpil/duratom).

Frostflower's file backend does not use `agent` like `duratom` does, and won't return 
from `swap!` before the write is completed. 

## Usage

There is an additional backend dispatch for `:sync-local-file` key.

```clj
;; require frostflower to make the additional dispatch available
(require 'frostflower)
(require '[duratom.core :as duratom])

(duratom :sync-local-file
         :file-path "/home/..."
         :init {:x 1 :y 2})
```
 
## License

Copyright © 2017 vermilionsands

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
