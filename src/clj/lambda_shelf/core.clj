(ns lambda-shelf.core
  (:require [cemerick.austin.repls :refer (browser-connected-repl-js)]
            [net.cgrand.enlive-html :as enlive]
            [compojure.route :refer (resources)]
            [compojure.core :refer (GET defroutes)]
            [ring.adapter.jetty :refer [run-jetty]]
            [clojure.java.io :as io]
            [org.httpkit.server :refer [with-channel on-close on-receive run-server send!]]))


(defn destructure-request [{type :type data :data }]
  (case type
    "greeting" {:type "greeting" :data "Hail to the LAMBDA!"}
    "DEFAULT"))


                                        ; websocket server
(defn handler [request]
  (with-channel request channel
    (on-close channel (fn [status] (println "channel closed: " status)))
    (on-receive channel (fn [data]
                          (do
                            (println (str "data received: " (str (read-string data))))
                            (send! channel (str (destructure-request (read-string data)))))))))


(defn start-ws-server [port]
  (run-server handler {:port port}))


                                        ; ring server, only for production
(enlive/deftemplate page
  (io/resource "public/index.html")
  []
  [:body] (enlive/append
            (enlive/html [:script (browser-connected-repl-js)])))

(defroutes site
  (resources "/")
  (GET "/*" req (page)))

(defn run
  []
  (defonce ^:private server
    (ring.adapter.jetty/run-jetty #'site {:port 8080 :join? false}))
  server)

(defn -main
  [& args]
  (println "Starting ring server")
  (run)
  (println "Starting websocket server")
  (start-ws-server 9090))

#_(run)
#_(start-ws-server 9090)
