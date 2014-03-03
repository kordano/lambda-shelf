(ns lambda-shelf.core
  (:gen-class :main true)
  (:require [cemerick.austin.repls :refer (browser-connected-repl-js)]
            [net.cgrand.enlive-html :as enlive]
            [compojure.route :refer (resources)]
            [compojure.core :refer (GET POST defroutes)]
            [ring.adapter.jetty :refer [run-jetty]]
            [clojure.java.io :as io]
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


(defroutes site
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

  (POST "/bookmark/tag" request
        (let [data (-> request :body slurp read-string)
              resp (database/tag-bookmark data)]
          {:status 200
           :headers {"Content-Type" "application/edn"}
           :body (str (database/get-all-bookmarks))}))

  (GET "/*" req (page)))


(defn start [port]
  (run-jetty #'site {:port port :join? false}))


(defn -main []
  (database/initialize-databases)
  (let [port (Integer. (or (System/getenv "PORT") "8080"))]
    (start port)))

;; --- TESTING ---
#_(defonce server (start 8080))
#_(.stop server)
#_(.start server)
