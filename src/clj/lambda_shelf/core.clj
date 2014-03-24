(ns lambda-shelf.core
  (:gen-class :main true)
  (:require [cemerick.austin.repls :refer (browser-connected-repl-js)]
            [net.cgrand.enlive-html :as enlive]
            [compojure.route :refer (resources)]
            [compojure.core :refer (GET POST defroutes)]
            [org.httpkit.server :as httpkit]
            [clojure.java.io :as io]
            [lambda-shelf.quotes :as quotes]
            [lambda-shelf.warehouse :as warehouse]))


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

  (POST "/bookmark/add" request
        (let [data (-> request :body slurp read-string)
              resp (warehouse/insert-bookmark (update-in data [:title] #(if (= "" %) (fetch-url-title (:url data)) %)))]
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


(defn start [port]
  (httpkit/run-server site {:port port :join? false}))

(defn -main []
  (warehouse/init-db)
  (let [port (Integer. (or (System/getenv "PORT") "8080"))]
    (start port)))


;; --- TESTING ---
#_(defonce server (start 8080))
#_(.stop server)
#_(.start server)
