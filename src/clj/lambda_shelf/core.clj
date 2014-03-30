(ns lambda-shelf.core
  (:gen-class :main true)
  (:require [cemerick.austin.repls :refer (browser-connected-repl-js)]
            [net.cgrand.enlive-html :as enlive]
            [compojure.route :refer [resources]]
            [compojure.core :refer [GET POST defroutes]]
            [geschichte.repo :as repo]
            [geschichte.sync :refer [server-peer sync! wire-stage]]
            [geschichte.platform :refer [create-http-kit-handler!]]
            [konserve.store :refer [new-mem-store]]
            [org.httpkit.server :refer [with-channel on-close on-receive run-server send!]]
            [clojure.java.io :as io]
            [lambda-shelf.quotes :as quotes]
            [lambda-shelf.warehouse :as warehouse]
            [clojure.core.async :refer [<!! >!!]]))


(def store (<!! (new-mem-store)))


(def peer (server-peer (create-http-kit-handler! "ws://localhost:8080/geschichte/ws")
                       store))


(def stage (->> (repo/new-repository "shelf@polyc0l0r.net"
                                     {:version 1
                                      :type "lambda-shelf"}
                                     "A bookmarking application."
                                     false
                                     {:links #{}
                                      :comments #{}})
                (wire-stage peer)
                <!!
                sync!
                <!!
                atom))


(defn fetch-url [url]
  (enlive/html-resource (java.net.URL. url)))


(defn fetch-url-title [url]
  "fetch url and extract title"
  (-> (fetch-url url)
      (enlive/select [:head :title])
      first
      :content
      first))



(defn dispatch-bookmark [{:keys [topic data] :as incoming}]
  (case topic
    :greeting {:data "Greetings Master!" :topic :greeting}
    :get-all (warehouse/get-all-bookmarks)
    :fetch-title {:title (fetch-url-title (:url data))}
    :add (do (warehouse/insert-bookmark
              (update-in
               data
               [:title]
               #(if (= "" %) (fetch-url-title (:url data)) %)))
             (warehouse/get-all-bookmarks))
    :vote (do (warehouse/vote-bookmark data)
              (warehouse/get-all-bookmarks))
    :comment (do (warehouse/comment-bookmark data)
                 (warehouse/get-all-bookmarks))
    "DEFAULT"))


(defn bookmark-handler [request]
  (with-channel request channel
    (on-close channel
              (fn [status]
                (println "channel closed: " status)))
    (on-receive channel
                (fn [data]
                  (send! channel (str (dispatch-bookmark (read-string data))))))))


(enlive/deftemplate page
  (io/resource "public/index.html")
  []
  [:body] (enlive/append
           (enlive/html [:script (browser-connected-repl-js)])))


(defroutes site
  (resources "/")

  (GET "/bookmark/init" []
       {:status 200
        :headers {"Content-Type" "application/edn"}
        :body (str (warehouse/get-all-bookmarks))})

  (GET "/bookmark/export.edn" []
       {:status 200
        :headers {"Content-Type" "application/edn"}
        :body (str (warehouse/get-all-bookmarks))})

  (GET "/bookmark/ws" [] bookmark-handler) ;; websocket handling

  (GET "/geschichte/ws" [] (-> @peer :volatile :handler))

  (POST "/bookmark/add" request
        (let [data (-> request :body slurp read-string)
              resp (warehouse/insert-bookmark
                    (update-in
                     data
                     [:title]
                     #(if (= "" %) (fetch-url-title (:url data)) %)))]
          {:status 200
           :headers {"Content-Type" "application/edn"}
           :body (str (warehouse/get-all-bookmarks))}))

  (POST "/bookmark/fetch-title" request
        (let [data (-> request :body slurp read-string)]
          {:status 200
           :headers {"Content-Type" "application/edn"}
           :body (str {:title (fetch-url-title (:url data))})}))

  (POST "/bookmark/vote" request
        (let [data (-> request :body slurp read-string)
              resp (warehouse/vote-bookmark data)]
          {:status 200
           :headers {"Content-Type" "application/edn"}
           :body (str (warehouse/get-all-bookmarks))}))

  (POST "/bookmark/comment" request
        (let [data (-> request :body slurp read-string)
              resp (warehouse/comment-bookmark data)]
          {:status 200
           :headers {"Content-Type" "application/edn"}
           :body (str (warehouse/get-all-bookmarks))}))

  (GET "/*" req (page)))


(defn start-server [port]
  (do
    (println (str "Starting server @ port " port))
    (run-server #'site {:port port :join? false})))


(defn -main []
  (warehouse/init-db)
  (let [port (Integer. (or (System/getenv "PORT") "8080"))]
    (start-server port)))


;; --- TESTING ---
#_(def server (start-server 8080))
#_(server)
#_(.start server)
