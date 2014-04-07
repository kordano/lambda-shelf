(ns lambda-shelf.core
  (:gen-class :main true)
  (:require [cemerick.austin.repls :refer (browser-connected-repl-js)]
            [net.cgrand.enlive-html :as enlive]
            [compojure.route :refer [resources]]
            [compojure.core :refer [GET POST defroutes]]
            [geschichte.repo :as repo]
            [geschichte.sync :refer [server-peer]]
            [geschichte.stage :refer [sync! wire-stage]]
            [geschichte.platform :refer [create-http-kit-handler!]]
            [konserve.store :refer [new-mem-store]]
            [konserve.platform :refer [new-couch-store]]
            [compojure.handler :refer [site api]]
            [org.httpkit.server :refer [with-channel on-close on-receive run-server send!]]
            [ring.util.response :as resp]
            [cemerick.friend :as friend]
            [cemerick.friend.workflows :as workflows]
            [cemerick.friend.credentials :as creds]
            [clojure.java.io :as io]
            [lambda-shelf.views :as views]
            [lambda-shelf.quotes :as quotes]
            [lambda-shelf.warehouse :as warehouse]
            [clojure.core.async :refer [<!! >!!]]))

#_(repo/new-repository "shelf@polyc0l0r.net"
                     {:version 1
                      :type "lambda-shelf"}
                     "A bookmarking application."
                     false
                     ;; describe schema here
                     {:links #{{:url "http://slashdot.org"
                                :title "Newz"}}
                      :comments #{}})


(def store (<!! (new-couch-store "bookmarks")))


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

(defn now [] (java.util.Date.))

(def users {"root" {:username "root"
                    :password (creds/hash-bcrypt "lisp")
                    :roles #{::admin}}
            "eve" {:username "eve"
                    :password (creds/hash-bcrypt "lisp")
                    :roles #{::user}}})

(derive ::admin ::user)

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
             {:data (warehouse/get-all-bookmarks)})
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
                  (println (str "Incoming package: " (now)))
                  (send! channel (str (dispatch-bookmark (read-string data))))))))


(defroutes handler
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

  (GET "/login" req (views/login))

  (GET "/logout" req
       (friend/logout* (resp/redirect (str (:context req) "/login"))))

  (GET "/*" req (friend/authorize #{::user} (views/page req))))


(def secured-app
  (-> handler
      (friend/authenticate
        {:allow-anon? true
         :login-uri "/login"
         :default-landing-uri "/"
         :unauthorized-handler #(-> (enlive/html [:h2 "You do not have sufficient privileges to access " (:uri %)])
                                    resp/response
                                    (resp/status 401))
         :credential-fn #(creds/bcrypt-credential-fn users %)
         :workflows [(workflows/interactive-form)]})
      site))


(defn start-server [port]
  (do
    (println (str "Starting server @ port " port))
    (run-server secured-app {:port port :join? false})))


(defn -main [& args]
  (println (first args))
  (warehouse/init-db)
  (let [port (Integer. (or (System/getenv "PORT") (first args)))]
    (start-server port)))

;; --- TESTING ---

#_(def server (start-server 8080))

#_(server)
