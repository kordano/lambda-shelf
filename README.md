# lambda-shelf

A basic stuff collector using David Nolens React interface [om](https://github.com/swannodette/om "om") for the frontend, ring for backend server and postgresql as backend storage.

Enjoy live application on [heroku](https://stark-lake-6076.herokuapp.com/ "Lambda Shelf").

## Usage

A [postgresql](http://www.postgresql.org/download/ "postgresql installation") instance is required.

Adjust database credentials in [database.clj](https://github.com/kordano/lambda-shelf/blob/master/src/clj/lambda_shelf/database.clj "database file"). Refer to [clojure.java.jdbc](https://github.com/clojure/java.jdbc "jdbc") for configuration.

Compile Clojurescript
```
lein cljsbuild once
```
Start server
```
lein run
```
Visit [localhost:8080](http://localhost:8080 "Lambda Shelf").

## License

Copyright © 2014 Konrad Kühne

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
