(ns lambda-shelf.core
  (:gen-class :main true)
  (:require [cemerick.austin.repls :refer (browser-connected-repl-js)]
            [net.cgrand.enlive-html :as enlive]
            [compojure.route :refer [resources]]
            [compojure.core :refer [GET POST defroutes]]
            [geschichte.repo :as repo]
            [geschichte.stage :as s]
            [geschichte.sync :refer [server-peer client-peer]]
            [geschichte.platform :refer [create-http-kit-handler!]]
            [konserve.store :refer [new-mem-store]]
            [konserve.platform :refer [new-couch-store]]
            [compojure.handler :refer [site api]]
            [org.httpkit.server :refer [with-channel on-close on-receive run-server send!]]
            [ring.util.response :as resp]
            [cemerick.friend :as friend]
            [cemerick.friend.workflows :as workflows]
            [cemerick.friend.credentials :as creds]
            [lambda-shelf.views :as views]
            [lambda-shelf.quotes :as quotes]
            [clojure.core.async :refer [<!! >!!] :as async]
            [com.ashafa.clutch.utils :as utils]
            [com.ashafa.clutch :refer [couch]]
            [clojure.tools.logging :refer [info warn error]]))

(def behind-proxy? (or (System/getenv "SHELF_IS_BEHIND_PROXY")
                       false))


(def proto (or (System/getenv "SHELF_PROTO")
               "http"))

(def host (or (System/getenv "SHELF_HOST")
              "localhost"))

(def port (Integer.
           (or (System/getenv "SHELF_PORT")
               "8088")))



;; supply some store
(def store (<!! #_(new-mem-store)
            (new-couch-store
                 (couch (utils/url (utils/url (str "http://" (or (System/getenv "DB_PORT_5984_TCP_ADDR")
                                                                 "localhost") ":5984"))
                                   "bookmarks")))))

(def user-store (<!! #_(new-mem-store)
                     (new-couch-store
                      (couch (utils/url (utils/url (str "http://" (or (System/getenv "DB_PORT_5984_TCP_ADDR")
                                                                 "localhost") ":5984"))
                                   "users")))))

;; start synching
(def user-peer
  (server-peer
   (create-http-kit-handler! (str (if (= proto "https")
                                    "wss" "ws") "://" host ":" port "/users/ws"))
   user-store))

(def peer
  (server-peer (create-http-kit-handler! (str (if (= proto "https")
                                                "wss" "ws") "://" host ":" port "/geschichte/ws"))
               store))



#_(clojure.pprint/pprint (repo/new-repository
      "users@polyc0l0r.net"
      {:version 1
       :type "user"}
      "user management"
      false
      {"eve@polyc0l0r.net"
       {:username "eve@polyc0l0r.net"
        :password "$2a$10$FHlpFYfbz5hj8/4mC5mMQOge5Nu3oAOZ3mhfUn/PTlLfj2inwlKwa"

        :roles #{::user}}}))

(def user-stage
  (-> {:meta
       {:causal-order {#uuid "05f827a8-c061-5b5c-9ff9-806cd05cad44" []},
        :last-update #inst "2014-04-21T19:09:00.319-00:00",
        :head "master",
        :public false,
        :branches
        {"master" {:heads #{#uuid "05f827a8-c061-5b5c-9ff9-806cd05cad44"}}},
        :schema {:version 1, :type "http://github.com/ghubber/geschichte"},
        :pull-requests {},
        :id #uuid "2c58ac7d-f231-4601-b527-8eabd9fc336d",
        :description "user management"},
       :author "users@polyc0l0r.net",
       :schema {:version 1, :type "user"},
       :transactions [],
       :type :meta-sub,
       :new-values
       {#uuid "05f827a8-c061-5b5c-9ff9-806cd05cad44"
        {:transactions
         [[#uuid "14fee57a-ef69-5b26-b1a3-ddce1c3861bc"
           #uuid "123ed64b-1e25-59fc-8c5b-038636ae6c3d"]],
         :parents [],
         :ts #inst "2014-04-21T19:09:00.319-00:00",
         :author "users@polyc0l0r.net",
         :schema {:version 1, :type "user"}},
        #uuid "14fee57a-ef69-5b26-b1a3-ddce1c3861bc"
        {"eve@polyc0l0r.net"
         {:username "eve@polyc0l0r.net",
          :password
          "$2a$10$XfI6c004FVfthOCSbahQRuC0L3665C7Ry27rhjB8oVIehdqBMnAr2",
          :roles #{:lambda-shelf.core/user}}},
        #uuid "123ed64b-1e25-59fc-8c5b-038636ae6c3d"
        '(fn replace [old params] params)}}
      (s/wire-stage user-peer)
      <!!
      s/sync!
      <!!
      atom))


;; TODO find better way...
;; geschichte is now setup

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
    :fetch-title {:title (fetch-url-title (:url data))}
    "DEFAULT"))


(defn bookmark-handler [request]
  (with-channel request channel
    (on-close channel
              (fn [status]
                (info "bookmark channel closed: " status)))
    (on-receive channel
                (fn [data]
                  (info (str "Incoming package: " (java.util.Date.)))
                  (send! channel (str (dispatch-bookmark (read-string data))))))))


(defroutes handler
  (resources "/")

  (GET "/bookmark/ws" [] bookmark-handler) ;; websocket handling

  (GET "/geschichte/ws" [] (-> @peer :volatile :handler))

  (GET "/users/ws" [] (-> @user-peer :volatile :handler))

  (GET "/login" req (views/login))

  (GET "/registration" req (views/registration))

  (POST "/register" req (let [params (:params req)
                              new-user {(:email params)
                                        (-> params
                                            (dissoc :email-check)
                                            (assoc :username (:email params))
                                            (dissoc :email)
                                            (update-in [:password] creds/hash-bcrypt)
                                            (assoc :roles #{::user}))}]
                          (swap! user-stage #(-> %
                                                 (s/transact
                                                  new-user
                                                  'merge)
                                                 repo/commit
                                                 s/sync!
                                                 <!!))
                          (resp/redirect (str (:context req) "/login"))))

  (GET "/logout" req
       (friend/logout* (resp/redirect (str (:context req) "/login"))))

  (GET "/*" req (friend/authorize #{::user} (views/page req))))


;; TODO secure geschichte on user-repo basis
(def secured-app
  (-> (if behind-proxy?
        (friend/requires-scheme-with-proxy handler
                                           (keyword proto)
                                           {(keyword proto) port})
        handler)
      (friend/authenticate
       {:allow-anon? true
        :login-uri "/login"
        :default-landing-uri "/"
        :unauthorized-handler #(-> (enlive/html [:h2 "You do not have sufficient privileges to access " (:uri %)])
                                   resp/response
                                   (resp/status 401))
        :credential-fn #(creds/bcrypt-credential-fn (<!! (s/realize-value @user-stage user-store eval)) %)
        :workflows [(workflows/interactive-form)]})
      site))


(defn start-server [port]
  (do
    (info (str "Starting server @ port " port))
    (run-server secured-app {:port port :join? false})))


(defn -main [& args]
  (info (first args))
  (start-server port))

;; --- TESTING ---

#_(def server (start-server 8088))

#_(server)

(comment
  (<!! (s/realize-value @user-stage user-store eval))

  (pprint (-> @peer :volatile :log deref))

  (swap! peer (fn [old]
                @(server-peer (create-http-kit-handler! (str "ws://" host "/geschichte/ws"))
                              store)))

  (pprint (-> store :state deref (get "repo1@shelf.polyc0l0r.net")))

  (keys (-> store :state deref))

  (async/go
    (println "BUS-IN msg" (alts! [(-> @peer :volatile :chans first)
                                  (async/timeout 1000)])))

  )
