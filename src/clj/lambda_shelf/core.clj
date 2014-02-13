(ns lambda-shelf.core
  (:gen-class :main true)
  (:require [cemerick.austin.repls :refer (browser-connected-repl-js)]
            [net.cgrand.enlive-html :as enlive]
            [compojure.route :refer (resources)]
            [compojure.core :refer (GET POST defroutes)]
            [ring.adapter.jetty :refer [run-jetty]]
            [clojure.java.io :as io]
            [lambda-shelf.database :as database]))


                                        ; ring server, only for production
(enlive/deftemplate page
  (io/resource "public/index.html")
  []
  [:body] (enlive/append
            (enlive/html [:script (browser-connected-repl-js)])))


(defroutes site
  (resources "/")
  (GET "/bookmark/init" [] {:status 200
                   :headers {"Content-Type" "application/edn"}
                   :body (str (database/get-all-bookmarks))})
  (POST "/bookmark/add" request (let [data (-> request :body slurp read-string)
                               resp (database/insert-bookmark data)]
                           {:status 200
                            :headers {"Content-Type" "application/edn"}
                            :body (str (database/get-all-bookmarks))}))
  (GET "/*" req (page)))


(defn start [port]
  (run-jetty #'site {:port port :join? false}))


(defn -main []
  (database/migrate)
  (let [port (Integer. (or (System/getenv "PORT") "8080"))]
    (start port)))
