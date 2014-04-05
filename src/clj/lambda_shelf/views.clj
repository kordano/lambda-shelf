(ns lambda-shelf.views
  (:require [cemerick.austin.repls :refer (browser-connected-repl-js)]
            [net.cgrand.enlive-html :refer [deftemplate after html append]]
            [cemerick.friend :as friend]
            [clojure.java.io :as io]))


(deftemplate page
  (io/resource "public/index.html")
  [req]
  [:head]
  (append
   (html
    [:link {:rel "shortcut icon" :href "/favicon.ico"}]))
  [:body]
  (append
   (html
    [:div.navbar.navbar-default {:role "navigation"}
     [:div.container
      [:div.navbar-header
       [:a.navbar-brand {:href "#"}
        "The Shelf"]]
      [:div.collapse.navbar-collapse
       [:p.navbar-text "Signed in as " (-> req friend/identity :current)]
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
         [:a {:href "logout"
              :data-toggle "tooltip"
              :data-placement "bottom"
              :title "Logout"}
          [:span.glyphicon.glyphicon-off]]]]]]]
    [:div#main.container]
    [:script {:src "https://ajax.googleapis.com/ajax/libs/jquery/1.11.0/jquery.min.js"}]
    [:script {:src "http://fb.me/react-0.9.0.min.js"}]
    [:script {:src "//netdna.bootstrapcdn.com/bootstrap/3.1.1/js/bootstrap.min.js"}]
    [:script {:src "js/main.js"}]
    [:script (browser-connected-repl-js)])))


(deftemplate login
  (io/resource "public/index.html")
  []
  [:body]
  (append
   (html
    [:div.col-md-4.col-md-offset-4.col-sm-6.col-sm-offset-3
     [:div#login-panel.panel.panel-primary
      [:div.panel-heading
       [:h3.panel-title "Welcome to the Shelf"]]
      [:div.panel-body
       [:div#input-form {:role "form"}
        [:form {:method "POST" :action "login"}
         [:div.form-group
          [:input.form-control
           {:type "text"
            :name "username"
            :placeholder "User"}]]

         [:div.form-group
          [:input.form-control
           {:type "password"
            :name "password"
            :placeholder "Password"}]]

         [:br]

         [:input.btn.btn-primary.btn-block
          {:type "submit"
           :value "Login"}]]]]]])))
