(defproject lambda-shelf "0.1.0-SNAPSHOT"

  :description "bookmark app"

  :url "http://example.com/FIXME"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :source-paths ["src/cljs" "src/clj"]

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-2156"]
                 [org.clojure/core.async "0.1.267.0-0d7780-alpha"]
                 [org.clojure/java.jdbc "0.3.3"]
                 [postgresql "9.1-901-1.jdbc4"]
                 [ring "1.2.1"]
                 [enlive "1.1.5"]
                 [compojure "1.1.6"]
                 [clj-time "0.6.0"]
                 [om "0.3.6"]
                 [com.facebook/react "0.8.0.1"]
                 [hiccups "0.3.0"]]

  :plugins [[lein-cljsbuild "1.0.1"]
            [com.cemerick/austin "0.1.3"]
            [lein-ancient "0.5.4"]]

  :repl-options {:init-ns lambda-shelf.core}

  :main lambda-shelf.core

  :cljsbuild
  {:builds
   [{:source-paths ["src/cljs"]
     :compiler
     {:output-to "resources/public/js/main.js"
      :optimizations :simple}}]})
