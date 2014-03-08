(ns lambda-shelf.core
  (:gen-class :main true)
  (:require [cemerick.austin.repls :refer (browser-connected-repl-js)]
            [net.cgrand.enlive-html :as enlive]
            [compojure.route :refer (resources)]
            [compojure.handler :refer [site]]
            [compojure.core :refer (GET POST defroutes)]
            [clojure.java.io :as io]
            [org.httpkit.server :refer [with-channel on-close on-receive run-server send!]]
            [lambda-shelf.quotes :as quotes]
            [lambda-shelf.database :as database]))


(defn fetch-url [url]
  (enlive/html-resource (java.net.URL. url)))


(defn fetch-url-title [url]
  "fetch url and extract title"
  (-> (fetch-url url)
      (enlive/select [:head :title])
      first
      :content
      first))


(enlive/deftemplate page
  (io/resource "public/index.html")
  []
  [:body] (enlive/append
           (enlive/html [:script (browser-connected-repl-js)])))


(defroutes all-routes
  (resources "/")

  (GET "/bookmark/init" []
       {:status 200
        :headers {"Content-Type" "application/edn"}
        :body (str (database/get-all-bookmarks))})

  (POST "/bookmark/add" request
        (let [data (-> request :body slurp read-string)
              resp (database/insert-bookmark (update-in data [:title] #(if (= "" %) (fetch-url-title (:url data)) %)))]
          {:status 200
           :headers {"Content-Type" "application/edn"}
           :body (str (database/get-all-bookmarks))}))

  (POST "/bookmark/fetch-title" request
        (let [data (-> request :body slurp read-string)]
          {:status 200
           :headers {"Content-Type" "application/edn"}
           :body (str {:title (fetch-url-title (:url data))})}))

  (POST "/bookmark/vote" request
        (let [data (-> request :body slurp read-string)
              resp (database/vote-bookmark data)]
          {:status 200
           :headers {"Content-Type" "application/edn"}
           :body (str (database/get-all-bookmarks))}))

  (POST "/bookmark/comment" request
        (let [data (-> request :body slurp read-string)
              resp (database/comment-bookmark data)]
          {:status 200
           :headers {"Content-Type" "application/edn"}
           :body (str (database/get-all-bookmarks))}))

  (GET "/*" req (page)))

;; http-kit server
(defn start [port]
  (run-server (site #'all-routes) {:port port}))

(defn -main []
  (database/initialize-databases)
  (let [port (Integer. (or (System/getenv "PORT") "8080"))]
    (start port)))


;; --- TESTING ---
#_(defonce server (start 8080))
#_(.stop server)
#_(.start server)
