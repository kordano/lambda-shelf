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


(defn nav-view
  "Nav view with navigation buttons and search form"
  [app owner]
  (reify
    om/IRender
    (render [this]
      (html
       [:ul.nav.navbar-nav.navbar-right
        [:li
         [:form.navbar-form {:role "form"}
          [:input.form-control
           {:type "search"
            :placeholder "Search it ..."}]]]
        [:li
         [:a {:href "#"
              :data-toggle "tooltip"
              :data-placement "bottom"
              :title "Browse bookmarks"}
          [:span.glyphicon.glyphicon-bookmark]]]
        [:li
         [:a {:href "logout"
              :data-toggle "tooltip"
              :data-placement "bottom"
              :title "Logout"}
          [:span.glyphicon.glyphicon-off]]]]))))


(om/root
 nav-view
 app-state
 {:target (. js/document (getElementById "nav-container"))})

(om/root
 bookmarks-view
 app-state
 {:target (. js/document (getElementById "main"))})
