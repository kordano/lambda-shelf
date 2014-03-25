(ns lambda-shelf.core
  (:require [hiccups.runtime :as hiccupsrt]
            [clojure.browser.repl]
            [cljs.core.async :refer [put! chan <! >! alts! timeout close!]]
            [om.core :as om :include-macros true]
            [clojure.string :refer [blank?]]
            [sablono.core :as html :refer-macros [html]]
            [lambda-shelf.bookmark :refer [bookmarks-view]]
            [lambda-shelf.login :refer [login-view]]
            [lambda-shelf.communicator :refer [post-edn get-edn]])
  (:require-macros [hiccups.core :as hiccups]
                   [cljs.core.async.macros :refer [go]]))


;; fire up repl
#_(do
    (def repl-env (reset! cemerick.austin.repls/browser-repl-env
                         (cemerick.austin/repl-env)))
    (cemerick.austin.repls/cljs-repl repl-env))

(.log js/console "HAIL TO THE LAMBDA!")

(def app-state (atom {:bookmarks nil}))

(defn navbar-view [app owner]
  (reify
    om/IRenderState
    (render-state [this state]
      (html
       [:div.container
        [:div.navbar-header
         [:a.navbar-brand {:href "#"}
        "The Shelf"]]
        [:div.collapse.navbar-collapse
         [:ul.nav.navbar-nav.navbar-right
          [:li
           [:a {:href "#"
                :data-toggle "tooltip"
                :data-placement "bottom"
                :title "Import bookmarks"}
            [:span.glyphicon.glyphicon-import]]]
          [:li
           [:a {:href "/bookmark/export.edn"
                :data-toggle "tooltip"
                :data-placement "bottom"
                :title "Export bookmarks"}
            [:span.glyphicon.glyphicon-export]]]
          [:li
           [:a {:href "#"
                :data-toggle "tooltip"
                :data-placement "bottom"
                :title "Sign in"
                 :on-click (fn [_] ((om/root
                                    login-view
                                    @app
                                    {:target (. js/document (getElementById "main"))})))}
                [:span.glyphicon.glyphicon-user]]]]]]))))


(om/root
 bookmarks-view
 app-state
 {:target (. js/document (getElementById "main"))})

(om/root
 navbar-view
 app-state
 {:target (. js/document (getElementById "overall-nav"))})