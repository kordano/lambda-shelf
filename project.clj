(defproject lambda-shelf "0.1.0-SNAPSHOT"

  :description "bookmark app"

  :url "http://github.com/kordano/lambda-shelf"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :min-lein-version "2.0.0"

  :source-paths ["src/cljs" "src/clj"]

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-2156"]
                 [org.clojure/core.async "0.1.278.0-76b25b-alpha"]
                 [ring "1.2.2"]
                 [com.ashafa/clutch "0.4.0-RC1"]
                 [enlive "1.1.5"]
                 [compojure "1.1.6"]
                 [om "0.5.0"]
                 [http-kit "2.1.18"]
                 [com.facebook/react "0.9.0.1"]
                 [geschichte "0.1.0-SNAPSHOT"]
                 [hiccups "0.3.0"]
                 [sablono "0.2.14"]]

  :plugins [[lein-cljsbuild "1.0.2"]
            [com.cemerick/austin "0.1.4"]
            [lein-ancient "0.5.4"]]

  :repl-options {:init-ns lambda-shelf.core}


  :main ^:skip-aot lambda-shelf.core

  :uberjar-name "lambda-shelf-standalone.jar"

  ;;:hooks [leiningen.cljsbuild]

  :profiles {:uberjar {:aot :all}}

  :cljsbuild
  {:builds
   [{:source-paths ["src/cljs"]
     :compiler
     {:output-to "resources/public/js/main.js"
      :optimizations :simple}}]})
