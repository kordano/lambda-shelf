(ns lambda-shelf.login
  (:require [cljs.core.async :refer [put! chan <! >! alts! timeout close!]]
            [om.core :as om :include-macros true]
            [clojure.string :refer [blank?]]
            [sablono.core :as html :refer-macros [html]]
            [lambda-shelf.bookmark :refer [bookmarks-view]])
  (:require-macros [cljs.core.async.macros :refer [go]]))


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
