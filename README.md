# lambda-shelf

A basic reddit-like link collector using David Nolen's React interface [om](https://github.com/swannodette/om "om") for the frontend and [geschichte](https://github.com/ghubber/geschichte) for P2P synching with repository semantics (replacing a traditional backend).

Enjoy live application [here](https://shelf.polyc0l0r.net "Lambda Shelf").


## Usage

A [couchdb](http://couchdb.apache.org "couchdb site") instance for [konserve](https://github.com/ghubber/konserve) is required.

Adjust database credentials in [core.clj](https://github.com/kordano/lambda-shelf/blob/master/src/clj/lambda_shelf/core.clj "warehouse file"). Refer to [clutch](https://github.com/clojure-clutch/clutch "clutch") for configuration.

Compile Clojurescript
```
lein cljsbuild once
```

Start server
```
lein run 8080
```

Visit [localhost:8080](http://localhost:8080 "Lambda Shelf").


## TODO

- Follow different p2p users, pull data selectively.
- Embed (external) pictures/videos/...
- Markdown format in comments.
- Investigate `Diaspora` peer protocol (maybe it can be plugged in).


## License

Copyright © 2014 Konrad Kühne, Christian Weilbach

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
