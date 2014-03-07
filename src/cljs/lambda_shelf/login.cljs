(ns lambda-shelf.login
  (:require [hiccups.runtime :as hiccupsrt]
            [clojure.browser.repl]
            [cljs.core.async :refer [put! chan <! >! alts! timeout close!]]
            [om.core :as om :include-macros true]
            [clojure.string :refer [blank?]]
            [sablono.core :as html :refer-macros [html]]
            [lambda-shelf.client :refer [bookmarks-view]]
            [lambda-shelf.communicator :refer [post-edn get-edn]])
  (:require-macros [hiccups.core :as hiccups]
                   [cljs.core.async.macros :refer [go]]))


(.log js/console "HAIL TO THE LAMBDA!")


(def app-state (atom {:bookmarks nil}))

;; fire up repl
#_(do
    (def repl-env (reset! cemerick.austin.repls/browser-repl-env
                         (cemerick.austin/repl-env)))
    (cemerick.austin.repls/cljs-repl repl-env))


(defn login-view [app owner]
  (reify
    om/IRender
    (render [this]
      (html
       [:div.col-md-4.col-md-offset-4.col-sm-6.col-sm-offset-3
        [:div#login-panel.panel.panel-primary
         [:div.panel-heading
          [:h3.panel-title "Welcome to the Shelf"]]
         [:div.panel-body
          [:div#input-form {:role "form"}

           [:div.form-group
            [:input.form-control
             {:type "email"
              :placeholder "Email"}]]

           [:div.form-group
            [:input.form-control
             {:type "password"
              :placeholder "Password"}]]

           [:br]

           [:button.btn.btn-primary.btn-block
            {:on-click (fn [_] (do
                                (.log js/console "access granted")
                                (om/root
                                 bookmarks-view
                                 @app
                                 {:target (. js/document (getElementById "main"))})))
             :type "button"}
            "Sign in"]]]]]))))



(defn navbar-view [app owner]
  (reify
    om/IRenderState
    (render-state [this state]
      (html
       [:div.container
        [:div.navbar-header
         [:a.navbar-brand {:href "#"}
        "The Shelf"]]
        [:div.collapse.navbar-collapse.navbar-right
         [:button.btn.btn-default.navbar-btn
          {:type "button"
           :on-click (fn [_] ((om/root
                              login-view
                              @app
                              {:target (. js/document (getElementById "main"))})))}
          "Sign in"]]]))))


(om/root
 bookmarks-view
 app-state
 {:target (. js/document (getElementById "main"))})

(om/root
 navbar-view
 app-state
 {:target (. js/document (getElementById "overall-nav"))})
