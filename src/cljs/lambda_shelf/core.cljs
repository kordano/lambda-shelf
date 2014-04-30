(ns lambda-shelf.core
  (:require [hiccups.runtime :as hiccupsrt]
            [clojure.browser.repl]
            [cljs.core.async :refer [put! chan <! >! alts! timeout close!]]
            [om.core :as om :include-macros true]
            [clojure.string :refer [blank?]]
            [sablono.core :as html :refer-macros [html]]
            [lambda-shelf.bookmark :refer [bookmarks-view]]
            [lambda-shelf.login :refer [login-view]]
            [lambda-shelf.communicator :refer [connect!]])
  (:require-macros [hiccups.core :as hiccups]
                   [cljs.core.async.macros :refer [go]]))

;; fire up repl
#_(do
    (ns austin-dev)
    (def repl-env (reset! cemerick.austin.repls/browser-repl-env
                         (cemerick.austin/repl-env)))
    (cemerick.austin.repls/cljs-repl repl-env))

(.log js/console "HAIL TO THE LAMBDA!")

(def app-state (atom {:bookmarks nil}))


(def uri (goog.Uri. js/document.URL))

(def ssl? (= (.getScheme uri) "https"))


#_(defn user-list-view
  "Side view for current registered users"
  [app owner]
  (reify
    om/IInitState
    {:ws-in (chan)
     :ws-out (chan)}
    om/IWillMount
    (will-mount [_]
      (go
        (let [{:keys [in out websocket]}
               (<! (connect!
                    (str (if ssl? "wss://" "ws://")
                         (.getDomain uri)
                         ":"
                         (.getPort uri)
                         "/bookmark/ws")))]
          (om/set-state! owner :ws-in in)
          (om/set-state! owner :ws-out out)
          (>! in {:topic :get-all-users :data []})
          (let [nval (<! out)]
            (om/transact!
             app
             :users
             (fn [_] nval))))))
    om/IRender
    (render [_]
      (html
       [:div
        [:h4 "The Collective"]
        [:ul.list-group
         (map
          #(vec [:li.list-group-item.user-list-item
                 [:p [:a {:href "#"
                          :title "Add friend"}
                      [:span.glyphicon.glyphicon-user]]
                  %]])
          (:users app))]]))))


#_(om/root
 user-list-view
 app-state
 {:target (. js/document (getElementById "user-list-container"))})

(om/root
 bookmarks-view
 app-state
 {:target (. js/document (getElementById "main"))})
